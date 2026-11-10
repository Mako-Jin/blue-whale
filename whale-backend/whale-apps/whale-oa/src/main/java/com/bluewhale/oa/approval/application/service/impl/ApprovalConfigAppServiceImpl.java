package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalConfigSaveCommand;
import com.bluewhale.oa.approval.application.service.ApprovalConfigAppService;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.domain.model.ApprovalConfig;
import com.bluewhale.oa.approval.domain.model.ApprovalConfigQuery;
import com.bluewhale.oa.approval.domain.repository.ApprovalConfigRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批业务配置应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApprovalConfigAppServiceImpl implements ApprovalConfigAppService {

    private final ApprovalConfigRepository approvalConfigRepository;

    @Override
    public PageResult<ApprovalConfigView> page(ApprovalConfigQuery query) {
        List<ApprovalConfigView> records = approvalConfigRepository.page(query)
                .stream().map(this::toView).toList();
        return PageResult.of(records, approvalConfigRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public ApprovalConfigView detail(Long id) {
        return toView(requireConfig(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalConfigView create(ApprovalConfigSaveCommand command) {
        if (approvalConfigRepository.findByBusinessType(command.getBusinessType()).isPresent()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_EXISTS);
        }
        ApprovalConfig config = new ApprovalConfig();
        config.setBusinessType(command.getBusinessType());
        apply(config, command);
        config.enable();
        approvalConfigRepository.insert(config);
        return toView(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalConfigView update(Long id, ApprovalConfigSaveCommand command) {
        ApprovalConfig config = requireConfig(id);
        approvalConfigRepository.findByBusinessType(command.getBusinessType())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_EXISTS);
                });
        config.setBusinessType(command.getBusinessType());
        apply(config, command);
        approvalConfigRepository.update(config);
        return toView(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalConfigView changeEnabled(Long id, boolean enabled) {
        ApprovalConfig config = requireConfig(id);
        if (enabled) {
            config.enable();
        } else {
            config.disable();
        }
        approvalConfigRepository.update(config);
        return toView(config);
    }

    @Override
    public ApprovalConfigView requireEnabled(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_INVALID);
        }
        ApprovalConfig config = approvalConfigRepository.findByBusinessType(businessType)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_NOT_FOUND));
        if (!config.isEnabled()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_NOT_FOUND, "该业务审批配置已停用");
        }
        return toView(config);
    }

    /**
     * 查询配置，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 配置
     */
    private ApprovalConfig requireConfig(Long id) {
        return approvalConfigRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_CONFIG_NOT_FOUND));
    }

    /**
     * 将命令字段写入配置模型（不改动启用状态）。
     *
     * @param config 配置
     * @param command 保存命令
     */
    private void apply(ApprovalConfig config, ApprovalConfigSaveCommand command) {
        config.setBusinessName(command.getBusinessName());
        config.setDefinitionCode(command.getDefinitionCode());
        config.setTitleTemplate(command.getTitleTemplate());
        config.setBusinessUrl(command.getBusinessUrl());
        config.setCcUserIds(command.getCcUserIds());
        config.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param config 配置
     * @return 配置视图
     */
    private ApprovalConfigView toView(ApprovalConfig config) {
        ApprovalConfigView view = new ApprovalConfigView();
        view.setId(config.getId());
        view.setBusinessType(config.getBusinessType());
        view.setBusinessName(config.getBusinessName());
        view.setDefinitionCode(config.getDefinitionCode());
        view.setTitleTemplate(config.getTitleTemplate());
        view.setBusinessUrl(config.getBusinessUrl());
        view.setCcUserIds(config.getCcUserIds());
        view.setEnabled(config.isEnabled());
        view.setRemark(config.getRemark());
        view.setCreateTime(config.getCreateTime());
        view.setUpdateTime(config.getUpdateTime());
        return view;
    }
}
