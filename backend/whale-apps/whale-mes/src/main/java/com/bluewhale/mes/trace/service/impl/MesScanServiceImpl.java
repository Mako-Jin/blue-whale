package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesScanRuleSaveRequest;
import com.bluewhale.mes.trace.dto.MesScanValidateRequest;
import com.bluewhale.mes.trace.entity.MesBarcode;
import com.bluewhale.mes.trace.entity.MesScanRule;
import com.bluewhale.mes.trace.mapper.MesBarcodeMapper;
import com.bluewhale.mes.trace.mapper.MesScanRuleMapper;
import com.bluewhale.mes.trace.service.MesScanService;
import com.bluewhale.mes.trace.vo.MesScanRuleVO;
import com.bluewhale.mes.trace.vo.MesScanValidateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MES 扫码防错应用服务实现
 *
 * <p>校验顺序：条码存在 → 条码状态 → 幂等键 → 规则匹配（工位/工序越具体优先级越高）。
 * 规则命中物料差异时，{@code BLOCK} 直接阻断（passed=false），{@code WARN} 放行并告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesScanServiceImpl implements MesScanService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 规则状态：启用 */
    private static final String RULE_ENABLED = "ENABLED";

    /** 条码状态：正常 */
    private static final String BARCODE_ACTIVE = "ACTIVE";

    /** 条码状态：作废 */
    private static final String BARCODE_VOID = "VOID";

    /** 处理方式：阻断 */
    private static final String ACTION_BLOCK = "BLOCK";

    private final MesBarcodeMapper barcodeMapper;

    private final MesScanRuleMapper ruleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesScanValidateVO validate(MesScanValidateRequest request) {
        MesBarcode barcode = barcodeMapper.selectOne(new LambdaQueryWrapper<MesBarcode>()
                .eq(MesBarcode::getBarcodeValue, request.getBarcodeValue())
                .last("LIMIT 1"));
        if (barcode == null) {
            return blocked(request.getBarcodeValue(), null, "条码不存在");
        }
        if (BARCODE_VOID.equals(barcode.getStatus())) {
            return blocked(barcode.getBarcodeValue(), barcode.getBatchNo(), "条码已作废");
        }
        if (StringUtils.hasText(request.getScanNo())
                && request.getScanNo().equals(barcode.getLastScanNo())) {
            MesScanValidateVO duplicate = passed(barcode, null, "重复扫码，已幂等忽略");
            duplicate.setDuplicate(true);
            return duplicate;
        }
        MesScanRule rule = matchRule(request.getStationCode(), request.getOperationCode());
        MesScanValidateVO result = passed(barcode, rule == null ? null : rule.getRuleCode(), "校验通过");
        if (rule != null && StringUtils.hasText(rule.getExpectedItemCode())
                && !Objects.equals(rule.getExpectedItemCode(), barcode.getItemCode())) {
            String message = "物料防错：期望 " + rule.getExpectedItemCode() + "，实际 " + barcode.getItemCode();
            if (ACTION_BLOCK.equals(rule.getAction())) {
                result = blocked(barcode.getBarcodeValue(), barcode.getBatchNo(), message);
                result.setRuleCode(rule.getRuleCode());
                return result;
            }
            result.setMessage(message);
            result.setRuleCode(rule.getRuleCode());
        }
        barcode.setScanCount((barcode.getScanCount() == null ? 0 : barcode.getScanCount()) + 1);
        barcode.setLastScanNo(request.getScanNo());
        barcode.setLastScanTime(LocalDateTime.now());
        barcodeMapper.updateById(barcode);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesScanRuleVO saveRule(MesScanRuleSaveRequest request) {
        LambdaQueryWrapper<MesScanRule> duplicate = new LambdaQueryWrapper<>();
        duplicate.eq(MesScanRule::getRuleCode, request.getRuleCode());
        duplicate.ne(request.getId() != null, MesScanRule::getId, request.getId());
        if (ruleMapper.selectCount(duplicate) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "扫码规则编码已存在：" + request.getRuleCode());
        }
        MesScanRule rule = request.getId() == null ? new MesScanRule() : ruleMapper.selectById(request.getId());
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "扫码规则不存在：" + request.getId());
        }
        rule.setRuleCode(request.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setStationCode(request.getStationCode());
        rule.setOperationCode(request.getOperationCode());
        rule.setExpectedItemCode(request.getExpectedItemCode());
        rule.setAction(StringUtils.hasText(request.getAction()) ? request.getAction() : ACTION_BLOCK);
        rule.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : RULE_ENABLED);
        rule.setRemark(request.getRemark());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else if (ruleMapper.updateById(rule) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "扫码规则已被其他用户修改，请刷新后重试");
        }
        return toRuleVo(rule);
    }

    @Override
    public PageResult<MesScanRuleVO> pageRules(String ruleCode, String stationCode, String status,
                                               Long current, Long size) {
        LambdaQueryWrapper<MesScanRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(ruleCode), MesScanRule::getRuleCode, ruleCode);
        wrapper.eq(StringUtils.hasText(stationCode), MesScanRule::getStationCode, stationCode);
        wrapper.eq(StringUtils.hasText(status), MesScanRule::getStatus, status);
        wrapper.orderByDesc(MesScanRule::getCreateTime);
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageNo = Math.max(current == null ? 1L : current, 1L);
        Page<MesScanRule> page = ruleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MesScanRuleVO> records = page.getRecords().stream().map(this::toRuleVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 匹配最具体的启用规则（工位/工序限定越多优先级越高）
     *
     * @param stationCode   工位编码
     * @param operationCode 工序编码
     * @return 命中的规则，无命中返回 {@code null}
     */
    private MesScanRule matchRule(String stationCode, String operationCode) {
        LambdaQueryWrapper<MesScanRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesScanRule::getStatus, RULE_ENABLED);
        List<MesScanRule> rules = ruleMapper.selectList(wrapper);
        MesScanRule best = null;
        int bestScore = -1;
        for (MesScanRule rule : rules) {
            if (!scopeMatches(rule.getStationCode(), stationCode)
                    || !scopeMatches(rule.getOperationCode(), operationCode)) {
                continue;
            }
            int score = (StringUtils.hasText(rule.getStationCode()) ? 1 : 0)
                    + (StringUtils.hasText(rule.getOperationCode()) ? 1 : 0)
                    + (StringUtils.hasText(rule.getExpectedItemCode()) ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = rule;
            }
        }
        return best;
    }

    /**
     * 判断规则范围是否适用于当前扫码上下文（规则为空表示不限）
     *
     * @param ruleValue  规则限定值
     * @param actualValue 实际值
     * @return 匹配返回 {@code true}
     */
    private boolean scopeMatches(String ruleValue, String actualValue) {
        return !StringUtils.hasText(ruleValue) || Objects.equals(ruleValue, actualValue);
    }

    /**
     * 构建通过结果
     *
     * @param barcode 条码
     * @param ruleCode 命中规则编码
     * @param message 提示信息
     * @return 校验结果
     */
    private MesScanValidateVO passed(MesBarcode barcode, String ruleCode, String message) {
        MesScanValidateVO vo = baseResult(barcode);
        vo.setPassed(true);
        vo.setBlocked(false);
        vo.setDuplicate(false);
        vo.setMessage(message);
        vo.setRuleCode(ruleCode);
        return vo;
    }

    /**
     * 构建阻断结果
     *
     * @param barcodeValue 条码值
     * @param batchNo      批次号
     * @param message      阻断原因
     * @return 校验结果
     */
    private MesScanValidateVO blocked(String barcodeValue, String batchNo, String message) {
        MesScanValidateVO vo = new MesScanValidateVO();
        vo.setPassed(false);
        vo.setBlocked(true);
        vo.setDuplicate(false);
        vo.setMessage(message);
        vo.setBarcodeValue(barcodeValue);
        vo.setBatchNo(batchNo);
        return vo;
    }

    /**
     * 构建结果基础字段
     *
     * @param barcode 条码
     * @return 校验结果
     */
    private MesScanValidateVO baseResult(MesBarcode barcode) {
        MesScanValidateVO vo = new MesScanValidateVO();
        vo.setBarcodeValue(barcode.getBarcodeValue());
        vo.setBatchNo(barcode.getBatchNo());
        vo.setItemCode(barcode.getItemCode());
        return vo;
    }

    /**
     * 扫码规则实体转视图
     *
     * @param rule 规则实体
     * @return 规则视图
     */
    private MesScanRuleVO toRuleVo(MesScanRule rule) {
        MesScanRuleVO vo = new MesScanRuleVO();
        vo.setId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setRuleName(rule.getRuleName());
        vo.setStationCode(rule.getStationCode());
        vo.setOperationCode(rule.getOperationCode());
        vo.setExpectedItemCode(rule.getExpectedItemCode());
        vo.setAction(rule.getAction());
        vo.setStatus(rule.getStatus());
        vo.setRemark(rule.getRemark());
        return vo;
    }
}
