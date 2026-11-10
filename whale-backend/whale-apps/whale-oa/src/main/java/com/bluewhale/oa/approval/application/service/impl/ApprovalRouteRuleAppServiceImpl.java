package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.oa.approval.application.command.ApprovalRouteRuleSaveCommand;
import com.bluewhale.oa.approval.application.service.ApprovalRouteRuleAppService;
import com.bluewhale.oa.approval.application.view.ApprovalRouteRuleView;
import com.bluewhale.oa.approval.domain.model.ApprovalRouteRule;
import com.bluewhale.oa.approval.domain.repository.ApprovalRouteRuleRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批人解析规则应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApprovalRouteRuleAppServiceImpl implements ApprovalRouteRuleAppService {

    private final ApprovalRouteRuleRepository approvalRouteRuleRepository;

    @Override
    public List<ApprovalRouteRuleView> list(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RULE_INVALID, "业务类型不能为空");
        }
        return approvalRouteRuleRepository.listByBusinessType(businessType)
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRouteRuleView save(ApprovalRouteRuleSaveCommand command) {
        ApprovalRouteRule rule = approvalRouteRuleRepository
                .findByBusinessAndNode(command.getBusinessType(), command.getNodeKey())
                .orElseGet(() -> {
                    ApprovalRouteRule created = new ApprovalRouteRule();
                    created.setBusinessType(command.getBusinessType());
                    created.setNodeKey(command.getNodeKey());
                    return created;
                });
        rule.setNodeName(command.getNodeName());
        rule.setResolverType(command.getResolverType());
        rule.setResolverValue(command.getResolverValue());
        rule.setFallbackResolverType(command.getFallbackResolverType());
        rule.setFallbackResolverValue(command.getFallbackResolverValue());
        rule.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        rule.setEnabled(command.getEnabled() == null || command.getEnabled());
        rule.setRemark(command.getRemark());
        if (rule.getId() == null) {
            approvalRouteRuleRepository.insert(rule);
        } else {
            approvalRouteRuleRepository.update(rule);
        }
        return toView(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        approvalRouteRuleRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_RULE_NOT_FOUND));
        approvalRouteRuleRepository.deleteById(id);
    }

    /**
     * 领域模型转视图。
     *
     * @param rule 规则
     * @return 规则视图
     */
    private ApprovalRouteRuleView toView(ApprovalRouteRule rule) {
        ApprovalRouteRuleView view = new ApprovalRouteRuleView();
        view.setId(rule.getId());
        view.setBusinessType(rule.getBusinessType());
        view.setNodeKey(rule.getNodeKey());
        view.setNodeName(rule.getNodeName());
        view.setResolverType(rule.getResolverType());
        view.setResolverValue(rule.getResolverValue());
        view.setFallbackResolverType(rule.getFallbackResolverType());
        view.setFallbackResolverValue(rule.getFallbackResolverValue());
        view.setSortNo(rule.getSortNo());
        view.setEnabled(rule.isEnabled());
        view.setRemark(rule.getRemark());
        view.setCreateTime(rule.getCreateTime());
        view.setUpdateTime(rule.getUpdateTime());
        return view;
    }
}
