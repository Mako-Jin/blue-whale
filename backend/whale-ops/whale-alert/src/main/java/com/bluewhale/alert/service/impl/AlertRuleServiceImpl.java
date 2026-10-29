package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertRuleQueryRequest;
import com.bluewhale.alert.dto.AlertRuleSaveRequest;
import com.bluewhale.alert.converter.AlertConverter;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertRuleService;
import com.bluewhale.alert.vo.AlertRuleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 告警规则服务实现
 *
 * <p>新增 / 更新前按 {@code rule_code} 查重（仅未删除记录），冲突抛 {@code PARAM_ERROR}；
 * 详情 / 更新 / 删除 / 启停前均校验存在性，缺失抛 {@code NOT_FOUND}。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 停用状态 */
    private static final int STATUS_DISABLED = 0;

    private final AlertRuleMapper alertRuleMapper;

    @Override
    public PageResult<AlertRuleVO> page(AlertRuleQueryRequest request) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(inner -> inner.like(AlertRule::getRuleName, request.getKeyword())
                    .or().like(AlertRule::getRuleCode, request.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(request.getMetric()), AlertRule::getMetric, request.getMetric());
        wrapper.eq(StringUtils.hasText(request.getLevel()), AlertRule::getLevel, request.getLevel());
        wrapper.eq(request.getStatus() != null, AlertRule::getStatus, request.getStatus());
        wrapper.orderByDesc(AlertRule::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<AlertRule> page = alertRuleMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<AlertRuleVO> records = page.getRecords().stream().map(AlertRuleServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AlertRuleVO getDetail(Long id) {
        return toVo(requireRule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO create(AlertRuleSaveRequest request) {
        ensureUnique(request.getRuleCode(), null);
        AlertRule entity = AlertConverter.INSTANCE.toEntity(request);
        alertRuleMapper.insert(entity);
        return toVo(alertRuleMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO update(Long id, AlertRuleSaveRequest request) {
        AlertRule existing = requireRule(id);
        ensureUnique(request.getRuleCode(), id);
        AlertConverter.INSTANCE.updateEntity(request, existing);
        existing.setId(id);
        alertRuleMapper.updateById(existing);
        return toVo(alertRuleMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        alertRuleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        AlertRule existing = requireRule(id);
        existing.setStatus(status);
        alertRuleMapper.updateById(existing);
    }

    /**
     * 查询规则，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 规则ID
     * @return 规则实体
     */
    private AlertRule requireRule(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return rule;
    }

    /**
     * 唯一性校验：{@code rule_code} 冲突时抛出 {@code PARAM_ERROR}
     *
     * @param ruleCode  规则编码
     * @param excludeId 更新场景下排除自身ID，新增场景传 {@code null}
     */
    private void ensureUnique(String ruleCode, Long excludeId) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getRuleCode, ruleCode);
        if (excludeId != null) {
            wrapper.ne(AlertRule::getId, excludeId);
        }
        if (alertRuleMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 规则实体转视图对象
     *
     * @param rule 规则实体
     * @return 规则视图对象
     */
    private static AlertRuleVO toVo(AlertRule rule) {
        AlertRuleVO vo = AlertConverter.INSTANCE.toRuleVo(rule);
        return vo;
    }
}
