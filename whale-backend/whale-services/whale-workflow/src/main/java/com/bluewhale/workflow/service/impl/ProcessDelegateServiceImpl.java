package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.request.DelegateRuleSaveRequest;
import com.bluewhale.workflow.po.ProcessDelegatePo;
import com.bluewhale.workflow.mapper.ProcessDelegateMapper;
import com.bluewhale.workflow.service.ProcessDelegateService;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.DelegateRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 流程代理规则服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessDelegateServiceImpl implements ProcessDelegateService {

    /** 代理规则 Mapper */
    private final ProcessDelegateMapper processDelegateMapper;

    @Override
    public PageResult<DelegateRuleVO> page(String fromAccount, long current, long size) {
        long safeCurrent = Math.min(Math.max(current, 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long safeSize = size <= 0
                ? WorkflowConstants.DEFAULT_PAGE_SIZE
                : Math.min(size, WorkflowConstants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<ProcessDelegatePo> wrapper = new LambdaQueryWrapper<ProcessDelegatePo>()
                .eq(hasText(fromAccount), ProcessDelegatePo::getFromAccount, fromAccount)
                .orderByDesc(ProcessDelegatePo::getCreateTime)
                .orderByDesc(ProcessDelegatePo::getId);
        Page<ProcessDelegatePo> page = processDelegateMapper.selectPage(new Page<>(safeCurrent, safeSize, false), wrapper);
        List<DelegateRuleVO> records = page.getRecords().stream()
                .map(WorkflowConverter.INSTANCE::toDelegateVo)
                .toList();
        return PageResult.of(records, page.getTotal(), safeCurrent, safeSize);
    }

    @Override
    public DelegateRuleVO create(DelegateRuleSaveRequest request) {
        ProcessDelegatePo entity = new ProcessDelegatePo();
        apply(entity, request);
        ensureNoOverlap(entity);
        processDelegateMapper.insert(entity);
        return WorkflowConverter.INSTANCE.toDelegateVo(entity);
    }

    @Override
    public DelegateRuleVO update(Long id, DelegateRuleSaveRequest request) {
        ProcessDelegatePo entity = processDelegateMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "代理规则不存在");
        }
        apply(entity, request);
        ensureNoOverlap(entity);
        processDelegateMapper.updateById(entity);
        return WorkflowConverter.INSTANCE.toDelegateVo(entity);
    }

    @Override
    public void delete(Long id) {
        ProcessDelegatePo entity = processDelegateMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "代理规则不存在");
        }
        processDelegateMapper.deleteById(id);
    }

    @Override
    public Optional<String> resolveActive(String definitionCode, String account, LocalDateTime now) {
        if (!hasText(definitionCode) || !hasText(account)) {
            return Optional.empty();
        }
        List<ProcessDelegatePo> rules = processDelegateMapper.selectList(new LambdaQueryWrapper<ProcessDelegatePo>()
                .eq(ProcessDelegatePo::getEnabled, WorkflowConstants.DELEGATE_ENABLED)
                .eq(ProcessDelegatePo::getDefinitionCode, definitionCode.trim())
                .eq(ProcessDelegatePo::getFromAccount, account.trim())
                .le(ProcessDelegatePo::getStartTime, now)
                .ge(ProcessDelegatePo::getEndTime, now)
                .orderByDesc(ProcessDelegatePo::getStartTime)
                .orderByDesc(ProcessDelegatePo::getId)
                .last("LIMIT 1"));
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0).getToAccount());
    }

    /**
     * 校验并写入规则字段。
     *
     * @param entity 规则实体
     * @param request 保存请求
     */
    private static void apply(ProcessDelegatePo entity, DelegateRuleSaveRequest request) {
        String definitionCode = request.getDefinitionCode().trim();
        String fromAccount = request.getFromAccount().trim();
        String toAccount = request.getToAccount().trim();
        if (fromAccount.equals(toAccount)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "委托人与代理人不能相同");
        }
        if (request.getStartTime().isAfter(request.getEndTime())
                || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "生效开始时间必须早于结束时间");
        }
        entity.setDefinitionCode(definitionCode);
        entity.setFromAccount(fromAccount);
        entity.setToAccount(toAccount);
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled()
                ? WorkflowConstants.DELEGATE_ENABLED : WorkflowConstants.DELEGATE_DISABLED);
        entity.setRemark(request.getRemark());
    }

    /**
     * 校验同流程定义、同委托人的启用规则时间段不重叠。
     *
     * <p>重叠判定：{@code existing.start < new.end && existing.end > new.start}；
     * 边界相接（start == existing.end）不算重叠。</p>
     *
     * @param entity 待保存规则
     */
    private void ensureNoOverlap(ProcessDelegatePo entity) {
        if (entity.getEnabled() == null || entity.getEnabled() != WorkflowConstants.DELEGATE_ENABLED) {
            return;
        }
        LambdaQueryWrapper<ProcessDelegatePo> wrapper = new LambdaQueryWrapper<ProcessDelegatePo>()
                .eq(ProcessDelegatePo::getEnabled, WorkflowConstants.DELEGATE_ENABLED)
                .eq(ProcessDelegatePo::getDefinitionCode, entity.getDefinitionCode())
                .eq(ProcessDelegatePo::getFromAccount, entity.getFromAccount())
                .ne(entity.getId() != null, ProcessDelegatePo::getId, entity.getId())
                .lt(ProcessDelegatePo::getStartTime, entity.getEndTime())
                .gt(ProcessDelegatePo::getEndTime, entity.getStartTime());
        if (processDelegateMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "代理生效时间段与其他规则重叠");
        }
    }

    /**
     * 判断文本非空。
     *
     * @param value 文本
     * @return 非空返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
