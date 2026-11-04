package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.DelegateRuleSaveRequest;
import com.bluewhale.workflow.entity.ProcessDelegate;
import com.bluewhale.workflow.mapper.ProcessDelegateMapper;
import com.bluewhale.workflow.service.ProcessDelegateService;
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

    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    /** 启用状态 */
    private static final int ENABLED = 1;

    /** 默认启用 */
    private static final int DEFAULT_ENABLED = 1;

    /** 代理规则 Mapper */
    private final ProcessDelegateMapper processDelegateMapper;

    @Override
    public PageResult<DelegateRuleVO> page(String fromAccount, long current, long size) {
        long safeCurrent = Math.max(1L, current);
        long safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        LambdaQueryWrapper<ProcessDelegate> wrapper = new LambdaQueryWrapper<ProcessDelegate>()
                .eq(hasText(fromAccount), ProcessDelegate::getFromAccount, fromAccount)
                .orderByDesc(ProcessDelegate::getCreateTime)
                .orderByDesc(ProcessDelegate::getId);
        Page<ProcessDelegate> page = processDelegateMapper.selectPage(new Page<>(safeCurrent, safeSize, false), wrapper);
        List<DelegateRuleVO> records = page.getRecords().stream()
                .map(WorkflowConverter.INSTANCE::toDelegateVo)
                .toList();
        return PageResult.of(records, page.getTotal(), safeCurrent, safeSize);
    }

    @Override
    public DelegateRuleVO create(DelegateRuleSaveRequest request) {
        ProcessDelegate entity = new ProcessDelegate();
        apply(entity, request);
        processDelegateMapper.insert(entity);
        return WorkflowConverter.INSTANCE.toDelegateVo(entity);
    }

    @Override
    public DelegateRuleVO update(Long id, DelegateRuleSaveRequest request) {
        ProcessDelegate entity = processDelegateMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "代理规则不存在");
        }
        apply(entity, request);
        processDelegateMapper.updateById(entity);
        return WorkflowConverter.INSTANCE.toDelegateVo(entity);
    }

    @Override
    public void delete(Long id) {
        ProcessDelegate entity = processDelegateMapper.selectById(id);
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
        List<ProcessDelegate> rules = processDelegateMapper.selectList(new LambdaQueryWrapper<ProcessDelegate>()
                .eq(ProcessDelegate::getEnabled, ENABLED)
                .eq(ProcessDelegate::getDefinitionCode, definitionCode.trim())
                .eq(ProcessDelegate::getFromAccount, account.trim())
                .le(ProcessDelegate::getStartTime, now)
                .ge(ProcessDelegate::getEndTime, now)
                .orderByDesc(ProcessDelegate::getStartTime)
                .orderByDesc(ProcessDelegate::getId)
                .last("LIMIT 1"));
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0).getToAccount());
    }

    /**
     * 校验并写入规则字段。
     *
     * @param entity 规则实体
     * @param request 保存请求
     */
    private static void apply(ProcessDelegate entity, DelegateRuleSaveRequest request) {
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
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() ? DEFAULT_ENABLED : 0);
        entity.setRemark(request.getRemark());
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
