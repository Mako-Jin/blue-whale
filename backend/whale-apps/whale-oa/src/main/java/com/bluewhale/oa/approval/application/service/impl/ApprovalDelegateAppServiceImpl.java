package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalDelegateRuleCommand;
import com.bluewhale.oa.approval.application.service.ApprovalDelegateAppService;
import com.bluewhale.oa.approval.application.view.ApprovalDelegateRuleView;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateCommand;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateRuleSnapshot;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 审批代理设置应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApprovalDelegateAppServiceImpl implements ApprovalDelegateAppService {

    /** 归属校验单页上限 */
    private static final long OWNER_SCAN_SIZE = 200L;

    private final WorkflowApprovalGateway workflowApprovalGateway;

    @Override
    public PageResult<ApprovalDelegateRuleView> page(ApprovalActor actor, long current, long size) {
        requireActor(actor);
        PageResult<WorkflowDelegateRuleSnapshot> page = workflowApprovalGateway
                .pageDelegateRules(actor.getUsername(), current, size);
        List<ApprovalDelegateRuleView> rows = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ApprovalDelegateRuleView create(ApprovalDelegateRuleCommand command, ApprovalActor actor) {
        requireActor(actor);
        validate(command, actor);
        WorkflowDelegateCommand gatewayCommand = toCommand(command, actor);
        return toView(workflowApprovalGateway.createDelegateRule(gatewayCommand));
    }

    @Override
    public ApprovalDelegateRuleView update(Long id, ApprovalDelegateRuleCommand command, ApprovalActor actor) {
        requireActor(actor);
        requireOwned(id, actor);
        validate(command, actor);
        return toView(workflowApprovalGateway.updateDelegateRule(id, toCommand(command, actor)));
    }

    @Override
    public void delete(Long id, ApprovalActor actor) {
        requireActor(actor);
        requireOwned(id, actor);
        workflowApprovalGateway.deleteDelegateRule(id);
    }

    /**
     * 校验代理规则归属当前用户。
     *
     * @param id 规则ID
     * @param actor 当前用户
     */
    private void requireOwned(Long id, ApprovalActor actor) {
        boolean owned = workflowApprovalGateway.pageDelegateRules(actor.getUsername(), 1L, OWNER_SCAN_SIZE)
                .getRecords().stream().anyMatch(rule -> Objects.equals(rule.getId(), id));
        if (!owned) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_DELEGATE_NOT_FOUND);
        }
    }

    /**
     * 校验命令区间与代理人。
     *
     * @param command 命令
     * @param actor 当前用户
     */
    private void validate(ApprovalDelegateRuleCommand command, ApprovalActor actor) {
        if (command.getStartTime().isAfter(command.getEndTime())
                || command.getStartTime().isEqual(command.getEndTime())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RULE_INVALID, "代理生效结束时间必须晚于开始时间");
        }
        if (Objects.equals(command.getToAccount().trim(), actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RULE_INVALID, "代理人不能是本人");
        }
    }

    /**
     * 转换为 Workflow 命令，委托人强制为当前用户。
     *
     * @param command 命令
     * @param actor 当前用户
     * @return Workflow 命令
     */
    private WorkflowDelegateCommand toCommand(ApprovalDelegateRuleCommand command, ApprovalActor actor) {
        WorkflowDelegateCommand gatewayCommand = new WorkflowDelegateCommand();
        gatewayCommand.setDefinitionCode(command.getDefinitionCode().trim());
        gatewayCommand.setFromAccount(actor.getUsername());
        gatewayCommand.setToAccount(command.getToAccount().trim());
        gatewayCommand.setStartTime(command.getStartTime());
        gatewayCommand.setEndTime(command.getEndTime());
        gatewayCommand.setEnabled(command.getEnabled() == null ? Boolean.TRUE : command.getEnabled());
        gatewayCommand.setRemark(command.getRemark());
        return gatewayCommand;
    }

    /**
     * 校验当前用户上下文。
     *
     * @param actor 当前用户
     */
    private void requireActor(ApprovalActor actor) {
        if (actor == null || !StringUtils.hasText(actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_ACTION_FORBIDDEN);
        }
    }

    /**
     * 映射代理规则视图。
     *
     * @param rule 规则快照
     * @return 视图
     */
    private ApprovalDelegateRuleView toView(WorkflowDelegateRuleSnapshot rule) {
        ApprovalDelegateRuleView view = new ApprovalDelegateRuleView();
        view.setId(rule.getId());
        view.setDefinitionCode(rule.getDefinitionCode());
        view.setFromAccount(rule.getFromAccount());
        view.setToAccount(rule.getToAccount());
        view.setStartTime(rule.getStartTime());
        view.setEndTime(rule.getEndTime());
        view.setEnabled(rule.getEnabled());
        view.setRemark(rule.getRemark());
        view.setCreateTime(rule.getCreateTime());
        view.setUpdateTime(rule.getUpdateTime());
        return view;
    }
}
