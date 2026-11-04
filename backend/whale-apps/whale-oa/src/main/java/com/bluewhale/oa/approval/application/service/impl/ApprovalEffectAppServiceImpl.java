package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.oa.approval.application.service.ApprovalEffectAppService;
import com.bluewhale.oa.approval.application.view.ApprovalEffectView;
import com.bluewhale.oa.approval.domain.model.ApprovalEffect;
import com.bluewhale.oa.approval.domain.model.ApprovalEffectStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalEffectRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批业务副作用应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApprovalEffectAppServiceImpl implements ApprovalEffectAppService {

    private final ApprovalEffectRepository approvalEffectRepository;

    @Override
    public List<ApprovalEffectView> list(Long approvalInstanceId) {
        return approvalEffectRepository.listByInstance(approvalInstanceId)
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean claim(Long approvalInstanceId, String effectType, String payload) {
        validate(approvalInstanceId, effectType);
        ApprovalEffect effect = new ApprovalEffect();
        effect.setApprovalInstanceId(approvalInstanceId);
        effect.setEffectType(effectType);
        effect.setEffectStatus(ApprovalEffectStatus.PENDING);
        effect.setPayload(payload);
        return approvalEffectRepository.insertIfAbsent(effect);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDone(Long approvalInstanceId, String effectType) {
        ApprovalEffect effect = requireEffect(approvalInstanceId, effectType);
        effect.markDone();
        approvalEffectRepository.update(effect);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long approvalInstanceId, String effectType, String error) {
        ApprovalEffect effect = requireEffect(approvalInstanceId, effectType);
        effect.markFailed(error);
        approvalEffectRepository.update(effect);
    }

    /**
     * 校验业务副作用参数。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     */
    private void validate(Long approvalInstanceId, String effectType) {
        if (approvalInstanceId == null || !StringUtils.hasText(effectType)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_EFFECT_NOT_FOUND, "审批实例ID与副作用类型不能为空");
        }
    }

    /**
     * 查询副作用，缺失时抛业务异常。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     * @return 副作用
     */
    private ApprovalEffect requireEffect(Long approvalInstanceId, String effectType) {
        validate(approvalInstanceId, effectType);
        return approvalEffectRepository.findByInstanceAndType(approvalInstanceId, effectType)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_EFFECT_NOT_FOUND));
    }

    /**
     * 领域模型转视图。
     *
     * @param effect 副作用
     * @return 副作用视图
     */
    private ApprovalEffectView toView(ApprovalEffect effect) {
        ApprovalEffectView view = new ApprovalEffectView();
        view.setEffectType(effect.getEffectType());
        view.setEffectStatus(effect.getEffectStatus() == null ? null : effect.getEffectStatus().name());
        view.setExecutedAt(effect.getExecutedAt());
        view.setRetryCount(effect.getRetryCount());
        view.setErrorMsg(effect.getErrorMsg());
        return view;
    }
}
