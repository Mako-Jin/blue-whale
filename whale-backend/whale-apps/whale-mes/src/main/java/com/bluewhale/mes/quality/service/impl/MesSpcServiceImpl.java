package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.quality.dto.MesSpcRuleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcRuleSaveRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleRequest;
import com.bluewhale.mes.quality.entity.MesInspectionPlanItem;
import com.bluewhale.mes.quality.entity.MesSpcRule;
import com.bluewhale.mes.quality.entity.MesSpcSample;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanItemMapper;
import com.bluewhale.mes.quality.mapper.MesSpcRuleMapper;
import com.bluewhale.mes.quality.mapper.MesSpcSampleMapper;
import com.bluewhale.mes.quality.service.MesSpcService;
import com.bluewhale.mes.quality.vo.MesSpcChartPointVO;
import com.bluewhale.mes.quality.vo.MesSpcRuleVO;
import com.bluewhale.mes.quality.vo.MesSpcSampleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SPC 应用服务实现
 *
 * <p>样本必须关联有效检验项且与规则绑定项一致；子组逐点录入，满额后计算均值与极差并按控制限判定，
 * 命中规则写入样本留痕。{@code BLOCK} 动作不阻断样本落库，以保证质量数据可追溯，由调用方按规则处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesSpcServiceImpl implements MesSpcService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 图表最大样本行数 */
    private static final int MAX_CHART_SAMPLES = 500;

    /** 图表默认子组数 */
    private static final int DEFAULT_CHART_POINTS = 50;

    /** 默认子组大小 */
    private static final int DEFAULT_SUBGROUP_SIZE = 5;

    /** 规则状态：启用 */
    private static final String RULE_ENABLED = "ENABLED";

    private final MesSpcRuleMapper ruleMapper;

    private final MesSpcSampleMapper sampleMapper;

    private final MesInspectionPlanItemMapper planItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSpcRuleVO saveRule(MesSpcRuleSaveRequest request) {
        MesInspectionPlanItem planItem = planItemMapper.selectById(request.getPlanItemId());
        if (planItem == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验计划项不存在：" + request.getPlanItemId());
        }
        MesSpcRule rule;
        if (request.getId() == null) {
            rule = new MesSpcRule();
            rule.setRuleCode(StringUtils.hasText(request.getRuleCode())
                    ? request.getRuleCode() : MesCodeGenerator.nextNo("SPR"));
        } else {
            rule = ruleMapper.selectById(request.getId());
            if (rule == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "SPC 规则不存在：" + request.getId());
            }
            if (StringUtils.hasText(request.getRuleCode())) {
                rule.setRuleCode(request.getRuleCode());
            }
        }
        assertRuleCodeUnique(rule.getId(), rule.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setPlanItemId(request.getPlanItemId());
        rule.setOperationCode(request.getOperationCode());
        rule.setChartType(StringUtils.hasText(request.getChartType()) ? request.getChartType() : "XBAR_R");
        rule.setSubgroupSize(request.getSubgroupSize() == null
                ? DEFAULT_SUBGROUP_SIZE : request.getSubgroupSize());
        rule.setCenterLine(request.getCenterLine());
        rule.setUcl(request.getUcl());
        rule.setLcl(request.getLcl());
        rule.setRUcl(request.getRUcl());
        rule.setRLcl(request.getRLcl());
        rule.setAlertAction(StringUtils.hasText(request.getAlertAction())
                ? request.getAlertAction() : "WARN");
        rule.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : RULE_ENABLED);
        rule.setRemark(request.getRemark());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else if (ruleMapper.updateById(rule) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPC 规则已被其他用户修改，请刷新后重试");
        }
        log.info("SPC 规则保存: ruleCode={}, planItemId={}", rule.getRuleCode(), rule.getPlanItemId());
        return toRuleVo(rule);
    }

    @Override
    public PageResult<MesSpcRuleVO> pageRules(MesSpcRuleQueryRequest request) {
        LambdaQueryWrapper<MesSpcRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getRuleCode()), MesSpcRule::getRuleCode, request.getRuleCode());
        wrapper.eq(request.getPlanItemId() != null, MesSpcRule::getPlanItemId, request.getPlanItemId());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesSpcRule::getStatus, request.getStatus());
        wrapper.orderByDesc(MesSpcRule::getCreateTime);
        Page<MesSpcRule> page = ruleMapper.selectPage(pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toRuleVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSpcSampleVO submitSample(MesSpcSampleRequest request) {
        MesSpcRule rule = ruleMapper.selectById(request.getRuleId());
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SPC 规则不存在：" + request.getRuleId());
        }
        if (!RULE_ENABLED.equals(rule.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPC 规则未启用：" + rule.getRuleCode());
        }
        if (!rule.getPlanItemId().equals(request.getPlanItemId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPC 规则与检验计划项不匹配");
        }
        MesInspectionPlanItem planItem = planItemMapper.selectById(request.getPlanItemId());
        if (planItem == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验计划项不存在：" + request.getPlanItemId());
        }
        List<MesSpcSample> subgroup = new ArrayList<>(listSubgroup(rule.getId(), request.getSubgroupNo()));
        int subgroupSize = rule.getSubgroupSize() == null ? DEFAULT_SUBGROUP_SIZE : rule.getSubgroupSize();
        if (subgroup.size() >= subgroupSize) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "子组样本数已满：" + request.getSubgroupNo() + "，应开启新子组");
        }
        int sampleSeq = request.getSampleSeq() == null ? subgroup.size() + 1 : request.getSampleSeq();
        if (subgroup.stream().anyMatch(sample -> Integer.valueOf(sampleSeq).equals(sample.getSampleSeq()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "子组内样本序号重复：" + sampleSeq);
        }
        MesSpcSample sample = new MesSpcSample();
        sample.setSampleNo(MesCodeGenerator.nextNo("QSP"));
        sample.setRuleId(rule.getId());
        sample.setRuleCode(rule.getRuleCode());
        sample.setPlanItemId(rule.getPlanItemId());
        sample.setInspectionTaskId(request.getInspectionTaskId());
        sample.setOperationCode(StringUtils.hasText(request.getOperationCode())
                ? request.getOperationCode() : rule.getOperationCode());
        sample.setSubgroupNo(request.getSubgroupNo());
        sample.setSampleSeq(sampleSeq);
        sample.setSubgroupSize(subgroupSize);
        sample.setMeasuredValue(request.getMeasuredValue());
        sample.setAlertFlag(0);
        sample.setSampledAt(request.getSampledAt() == null ? LocalDateTime.now() : request.getSampledAt());
        sampleMapper.insert(sample);
        subgroup.add(sample);
        if (subgroup.size() == subgroupSize) {
            evaluateSubgroup(rule, subgroup);
        }
        log.info("SPC 样本录入: sampleNo={}, ruleCode={}, subgroup={}, seq={}",
                sample.getSampleNo(), rule.getRuleCode(), request.getSubgroupNo(), sampleSeq);
        return toSampleVo(sample);
    }

    @Override
    public PageResult<MesSpcSampleVO> pageSamples(MesSpcSampleQueryRequest request) {
        LambdaQueryWrapper<MesSpcSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getRuleId() != null, MesSpcSample::getRuleId, request.getRuleId());
        wrapper.eq(request.getPlanItemId() != null, MesSpcSample::getPlanItemId, request.getPlanItemId());
        wrapper.eq(request.getAlertFlag() != null, MesSpcSample::getAlertFlag, request.getAlertFlag());
        wrapper.orderByAsc(MesSpcSample::getSubgroupNo).orderByAsc(MesSpcSample::getSampleSeq);
        Page<MesSpcSample> page = sampleMapper.selectPage(pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toSampleVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<MesSpcChartPointVO> chart(Long planItemId, Long ruleId, Integer limit) {
        LambdaQueryWrapper<MesSpcSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesSpcSample::getPlanItemId, planItemId);
        wrapper.eq(ruleId != null, MesSpcSample::getRuleId, ruleId);
        wrapper.orderByAsc(MesSpcSample::getSubgroupNo).orderByAsc(MesSpcSample::getSampleSeq);
        wrapper.last("LIMIT " + MAX_CHART_SAMPLES);
        List<MesSpcSample> samples = sampleMapper.selectList(wrapper);
        Map<Integer, List<MesSpcSample>> groups = new LinkedHashMap<>();
        samples.forEach(sample -> groups.computeIfAbsent(sample.getSubgroupNo(), key -> new ArrayList<>()).add(sample));
        int maxPoints = limit == null ? DEFAULT_CHART_POINTS
                : Math.min(Math.max(limit, 1), (int) MAX_PAGE_SIZE);
        List<MesSpcChartPointVO> points = new ArrayList<>();
        for (Map.Entry<Integer, List<MesSpcSample>> entry : groups.entrySet()) {
            if (points.size() >= maxPoints) {
                break;
            }
            points.add(toChartPoint(entry.getKey(), entry.getValue()));
        }
        return points;
    }

    /**
     * 子组满额判定：计算均值与极差，命中控制限时回填全部子组样本
     *
     * @param rule      SPC 规则
     * @param subgroup  子组样本
     */
    private void evaluateSubgroup(MesSpcRule rule, List<MesSpcSample> subgroup) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (MesSpcSample sample : subgroup) {
            sum = sum.add(sample.getMeasuredValue());
            min = min == null || sample.getMeasuredValue().compareTo(min) < 0
                    ? sample.getMeasuredValue() : min;
            max = max == null || sample.getMeasuredValue().compareTo(max) > 0
                    ? sample.getMeasuredValue() : max;
        }
        BigDecimal xbar = sum.divide(BigDecimal.valueOf(subgroup.size()), 4, RoundingMode.HALF_UP);
        BigDecimal range = max.subtract(min);
        String alert = evaluateLimits(rule, xbar, range);
        for (MesSpcSample sample : subgroup) {
            sample.setSubgroupXbar(xbar);
            sample.setSubgroupRange(range);
            sample.setAlertFlag(alert == null ? 0 : 1);
            sample.setAlertRule(alert);
            sampleMapper.updateById(sample);
        }
        if (alert != null) {
            log.warn("SPC 规则命中: ruleCode={}, subgroup={}, alert={}, action={}",
                    rule.getRuleCode(), subgroup.get(0).getSubgroupNo(), alert, rule.getAlertAction());
        }
    }

    /**
     * 按控制限判定子组，返回命中说明
     *
     * @param rule  SPC 规则
     * @param xbar  子组均值
     * @param range 子组极差
     * @return 命中说明（未命中返回 {@code null}）
     */
    private String evaluateLimits(MesSpcRule rule, BigDecimal xbar, BigDecimal range) {
        List<String> alerts = new ArrayList<>();
        if (rule.getUcl() != null && xbar.compareTo(rule.getUcl()) > 0) {
            alerts.add("均值超出上控制限 " + rule.getUcl().toPlainString());
        }
        if (rule.getLcl() != null && xbar.compareTo(rule.getLcl()) < 0) {
            alerts.add("均值低于下控制限 " + rule.getLcl().toPlainString());
        }
        if (rule.getRUcl() != null && range.compareTo(rule.getRUcl()) > 0) {
            alerts.add("极差超出上控制限 " + rule.getRUcl().toPlainString());
        }
        if (rule.getRLcl() != null && range.compareTo(rule.getRLcl()) < 0) {
            alerts.add("极差低于下控制限 " + rule.getRLcl().toPlainString());
        }
        return alerts.isEmpty() ? null : String.join("；", alerts);
    }

    /**
     * 查询子组样本
     *
     * @param ruleId      规则 ID
     * @param subgroupNo  子组号
     * @return 子组样本
     */
    private List<MesSpcSample> listSubgroup(Long ruleId, Integer subgroupNo) {
        return sampleMapper.selectList(new LambdaQueryWrapper<MesSpcSample>()
                .eq(MesSpcSample::getRuleId, ruleId)
                .eq(MesSpcSample::getSubgroupNo, subgroupNo)
                .orderByAsc(MesSpcSample::getSampleSeq));
    }

    /**
     * 规则编码租户内唯一校验
     *
     * @param id       规则 ID
     * @param ruleCode 规则编码
     */
    private void assertRuleCodeUnique(Long id, String ruleCode) {
        LambdaQueryWrapper<MesSpcRule> wrapper = new LambdaQueryWrapper<MesSpcRule>()
                .eq(MesSpcRule::getRuleCode, ruleCode)
                .ne(id != null, MesSpcRule::getId, id);
        if (ruleMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPC 规则编码已存在：" + ruleCode);
        }
    }

    /**
     * 子组聚合为图表点
     *
     * @param subgroupNo 子组号
     * @param samples    子组样本
     * @return 图表点
     */
    private MesSpcChartPointVO toChartPoint(Integer subgroupNo, List<MesSpcSample> samples) {
        MesSpcChartPointVO point = new MesSpcChartPointVO();
        point.setSubgroupNo(subgroupNo);
        point.setSampleCount(samples.size());
        MesSpcSample first = samples.get(0);
        point.setSubgroupXbar(first.getSubgroupXbar());
        point.setSubgroupRange(first.getSubgroupRange());
        if (point.getSubgroupXbar() == null) {
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal min = first.getMeasuredValue();
            BigDecimal max = first.getMeasuredValue();
            for (MesSpcSample sample : samples) {
                sum = sum.add(sample.getMeasuredValue());
                min = sample.getMeasuredValue().compareTo(min) < 0 ? sample.getMeasuredValue() : min;
                max = sample.getMeasuredValue().compareTo(max) > 0 ? sample.getMeasuredValue() : max;
            }
            point.setSubgroupXbar(sum.divide(BigDecimal.valueOf(samples.size()), 4, RoundingMode.HALF_UP));
            point.setSubgroupRange(max.subtract(min));
        }
        point.setAlertFlag(samples.stream().anyMatch(sample -> Integer.valueOf(1).equals(sample.getAlertFlag()))
                ? 1 : 0);
        samples.stream().map(MesSpcSample::getAlertRule).filter(StringUtils::hasText).findFirst()
                .ifPresent(point::setAlertRule);
        samples.stream().map(MesSpcSample::getSampledAt)
                .filter(sampledAt -> sampledAt != null)
                .max(LocalDateTime::compareTo).ifPresent(point::setSampledAt);
        return point;
    }

    /**
     * 构建分页对象
     *
     * @param current 当前页
     * @param size    每页条数
     * @return 分页对象
     */
    private <T> Page<T> pageOf(Long current, Long size) {
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageCurrent = Math.max(current == null ? 1L : current, 1L);
        return new Page<>(pageCurrent, pageSize);
    }

    /**
     * 规则实体转视图
     *
     * @param rule 规则
     * @return 视图
     */
    private MesSpcRuleVO toRuleVo(MesSpcRule rule) {
        MesSpcRuleVO vo = new MesSpcRuleVO();
        vo.setId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setRuleName(rule.getRuleName());
        vo.setPlanItemId(rule.getPlanItemId());
        vo.setOperationCode(rule.getOperationCode());
        vo.setChartType(rule.getChartType());
        vo.setSubgroupSize(rule.getSubgroupSize());
        vo.setCenterLine(rule.getCenterLine());
        vo.setUcl(rule.getUcl());
        vo.setLcl(rule.getLcl());
        vo.setRUcl(rule.getRUcl());
        vo.setRLcl(rule.getRLcl());
        vo.setAlertAction(rule.getAlertAction());
        vo.setStatus(rule.getStatus());
        vo.setRemark(rule.getRemark());
        vo.setCreateTime(rule.getCreateTime());
        return vo;
    }

    /**
     * 样本实体转视图
     *
     * @param sample 样本
     * @return 视图
     */
    private MesSpcSampleVO toSampleVo(MesSpcSample sample) {
        MesSpcSampleVO vo = new MesSpcSampleVO();
        vo.setId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setRuleId(sample.getRuleId());
        vo.setRuleCode(sample.getRuleCode());
        vo.setPlanItemId(sample.getPlanItemId());
        vo.setInspectionTaskId(sample.getInspectionTaskId());
        vo.setOperationCode(sample.getOperationCode());
        vo.setSubgroupNo(sample.getSubgroupNo());
        vo.setSampleSeq(sample.getSampleSeq());
        vo.setSubgroupSize(sample.getSubgroupSize());
        vo.setMeasuredValue(sample.getMeasuredValue());
        vo.setSubgroupXbar(sample.getSubgroupXbar());
        vo.setSubgroupRange(sample.getSubgroupRange());
        vo.setAlertFlag(sample.getAlertFlag());
        vo.setAlertRule(sample.getAlertRule());
        vo.setSampledAt(sample.getSampledAt());
        return vo;
    }
}
