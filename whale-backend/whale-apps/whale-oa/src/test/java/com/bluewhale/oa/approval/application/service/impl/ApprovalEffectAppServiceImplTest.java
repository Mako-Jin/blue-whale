package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.domain.model.ApprovalEffect;
import com.bluewhale.oa.approval.domain.model.ApprovalEffectStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalEffectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalEffectAppServiceImpl} 审批副作用幂等单元测试。
 *
 * @author BlueWhale
 */
class ApprovalEffectAppServiceImplTest {

    private final ApprovalEffectRepository approvalEffectRepository = mock(ApprovalEffectRepository.class);

    private final ApprovalEffectAppServiceImpl approvalEffectAppService =
            new ApprovalEffectAppServiceImpl(approvalEffectRepository);

    @Test
    @DisplayName("claim：首次登记返回 true，重复登记由仓储幂等拦截")
    void shouldClaimOnce() {
        when(approvalEffectRepository.insertIfAbsent(any())).thenReturn(true);

        boolean created = approvalEffectAppService.claim(7L, "LEAVE_BALANCE_DEDUCT", "{\"days\":2}");

        assertTrue(created);
        verify(approvalEffectRepository).insertIfAbsent(any());
    }

    @Test
    @DisplayName("markDone：待执行副作用迁移为 DONE 并落库")
    void shouldMarkDone() {
        ApprovalEffect effect = pendingEffect();
        when(approvalEffectRepository.findByInstanceAndType(7L, "LEAVE_BALANCE_DEDUCT"))
                .thenReturn(Optional.of(effect));

        approvalEffectAppService.markDone(7L, "LEAVE_BALANCE_DEDUCT");

        assertEquals(ApprovalEffectStatus.DONE, effect.getEffectStatus());
        verify(approvalEffectRepository).update(effect);
    }

    @Test
    @DisplayName("markFailed：已完成副作用拒绝重复失败标记")
    void shouldRejectFailAfterDone() {
        ApprovalEffect effect = pendingEffect();
        effect.setEffectStatus(ApprovalEffectStatus.DONE);
        when(approvalEffectRepository.findByInstanceAndType(7L, "LEAVE_BALANCE_DEDUCT"))
                .thenReturn(Optional.of(effect));

        BizException exception = assertThrows(BizException.class,
                () -> approvalEffectAppService.markFailed(7L, "LEAVE_BALANCE_DEDUCT", "ERP 超时"));

        assertEquals(40904, exception.getCode());
        verify(approvalEffectRepository, never()).update(any());
    }

    @Test
    @DisplayName("markDone：副作用不存在时返回 40404")
    void shouldRejectMissingEffect() {
        when(approvalEffectRepository.findByInstanceAndType(7L, "LEAVE_BALANCE_DEDUCT"))
                .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> approvalEffectAppService.markDone(7L, "LEAVE_BALANCE_DEDUCT"));

        assertEquals(40404, exception.getCode());
    }

    /**
     * 构造待执行副作用。
     *
     * @return 副作用
     */
    private static ApprovalEffect pendingEffect() {
        ApprovalEffect effect = new ApprovalEffect();
        effect.setId(1L);
        effect.setApprovalInstanceId(7L);
        effect.setEffectType("LEAVE_BALANCE_DEDUCT");
        effect.setEffectStatus(ApprovalEffectStatus.PENDING);
        return effect;
    }
}
