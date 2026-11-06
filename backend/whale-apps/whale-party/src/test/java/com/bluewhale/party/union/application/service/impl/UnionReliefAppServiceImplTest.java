package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.ReliefPayCommand;
import com.bluewhale.party.union.application.view.UnionReliefSubmitView;
import com.bluewhale.party.union.application.view.UnionReliefView;
import com.bluewhale.party.union.domain.gateway.ReliefApprovalGateway;
import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.ReliefType;
import com.bluewhale.party.union.domain.model.UnionRelief;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.domain.repository.UnionReliefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 慰问帮扶应用服务：上限校验、审批成功流转、审批不可用降级、状态同步映射。
 *
 * @author BlueWhale
 */
class UnionReliefAppServiceImplTest {

    private UnionReliefRepository unionReliefRepository;

    private UnionMemberRepository unionMemberRepository;

    private ReliefApprovalGateway approvalGateway;

    private PartySequenceService sequenceService;

    private UnionReliefAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        unionReliefRepository = mock(UnionReliefRepository.class);
        unionMemberRepository = mock(UnionMemberRepository.class);
        approvalGateway = mock(ReliefApprovalGateway.class);
        sequenceService = mock(PartySequenceService.class);
        service = new UnionReliefAppServiceImpl(unionReliefRepository, unionMemberRepository,
                approvalGateway, sequenceService, new PartyProperties());
        actor = new PartyActor(9L, "union_admin", Set.of("union_admin"), Set.of());
    }

    @Test
    @DisplayName("已批次数达上限时提交被拒")
    void submitShouldRejectWhenCountLimitExceeded() {
        stubRelief(relief(ReliefStatus.DRAFT, "100.00"));
        when(unionReliefRepository.countApproved(anyLong(), any(), anyInt())).thenReturn(2L);
        when(unionReliefRepository.sumApprovedAmount(anyLong(), any(), anyInt())).thenReturn(BigDecimal.ZERO);

        BizException exception = assertThrows(BizException.class, () -> service.submit(1L, actor));
        assertEquals(PartyErrorCode.UNION_RELIEF_LIMIT_EXCEEDED, exception.getCode());
        verify(approvalGateway, never()).start(any());
        verify(unionReliefRepository, never()).save(any(UnionRelief.class));
    }

    @Test
    @DisplayName("已批金额加本次超上限时提交被拒")
    void submitShouldRejectWhenAmountLimitExceeded() {
        stubRelief(relief(ReliefStatus.DRAFT, "100.00"));
        when(unionReliefRepository.countApproved(anyLong(), any(), anyInt())).thenReturn(0L);
        when(unionReliefRepository.sumApprovedAmount(anyLong(), any(), anyInt()))
                .thenReturn(new BigDecimal("4950.00"));

        BizException exception = assertThrows(BizException.class, () -> service.submit(1L, actor));
        assertEquals(PartyErrorCode.UNION_RELIEF_LIMIT_EXCEEDED, exception.getCode());
        verify(approvalGateway, never()).start(any());
    }

    @Test
    @DisplayName("审批发起成功转为审批中并记录实例")
    void submitShouldMoveToSubmittedWhenApprovalStarted() {
        stubRelief(relief(ReliefStatus.DRAFT, "100.00"));
        stubLimitUnused();
        when(approvalGateway.start(any())).thenReturn(88L);
        stubSave();

        UnionReliefSubmitView view = service.submit(1L, actor);

        assertEquals(ReliefStatus.SUBMITTED.name(), view.getStatus());
        assertTrue(view.isApprovalStarted());
        assertEquals(88L, view.getApprovalInstanceId());
        assertNull(view.getApprovalHint());
        verify(unionReliefRepository).save(any(UnionRelief.class));
    }

    @Test
    @DisplayName("审批服务异常时降级为草稿且写提示，不回滚申请")
    void submitShouldFallbackToDraftWhenApprovalUnavailable() {
        stubRelief(relief(ReliefStatus.DRAFT, "100.00"));
        stubLimitUnused();
        when(approvalGateway.start(any())).thenThrow(new IllegalStateException("workflow down"));
        stubSave();

        UnionReliefSubmitView view = service.submit(1L, actor);

        assertEquals(ReliefStatus.DRAFT.name(), view.getStatus());
        assertFalse(view.isApprovalStarted());
        assertNotNull(view.getApprovalHint());
        verify(unionReliefRepository).save(any(UnionRelief.class));
    }

    @Test
    @DisplayName("审批通过状态码 1 映射为已通过")
    void syncShouldMapApprovedStatus() {
        stubRelief(relief(ReliefStatus.SUBMITTED, "100.00"));
        when(approvalGateway.queryStatus(anyLong())).thenReturn(1);
        stubSave();

        UnionReliefView view = service.sync(1L, actor);

        assertEquals(ReliefStatus.APPROVED.name(), view.getStatus());
        assertEquals("APPROVED", view.getApprovalStatus());
        assertNotNull(view.getApprovedAt());
    }

    @Test
    @DisplayName("审批退回状态码 2 映射为已退回")
    void syncShouldMapRejectedStatus() {
        stubRelief(relief(ReliefStatus.SUBMITTED, "100.00"));
        when(approvalGateway.queryStatus(anyLong())).thenReturn(2);
        stubSave();

        UnionReliefView view = service.sync(1L, actor);

        assertEquals(ReliefStatus.REJECTED.name(), view.getStatus());
        assertEquals("REJECTED", view.getApprovalStatus());
    }

    @Test
    @DisplayName("未审批通过不能登记发放")
    void payShouldRequireApprovedStatus() {
        stubRelief(relief(ReliefStatus.DRAFT, "100.00"));

        BizException draft = assertThrows(BizException.class, () -> service.pay(1L, null, actor));
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, draft.getCode());

        stubRelief(relief(ReliefStatus.SUBMITTED, "100.00"));
        BizException submitted = assertThrows(BizException.class, () -> service.pay(1L, null, actor));
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, submitted.getCode());
        verify(unionReliefRepository, never()).save(any(UnionRelief.class));
    }

    @Test
    @DisplayName("审批通过后登记发放转为已发放")
    void payShouldMarkPaidFromApproved() {
        stubRelief(relief(ReliefStatus.APPROVED, "100.00"));
        stubSave();
        ReliefPayCommand command = new ReliefPayCommand();
        command.setPaidAt(LocalDate.of(2026, 11, 6));

        UnionReliefView view = service.pay(1L, command, actor);

        assertEquals(ReliefStatus.PAID.name(), view.getStatus());
        assertNotNull(view.getPaidAt());
        verify(unionReliefRepository).save(any(UnionRelief.class));
    }

    /**
     * 桩定申请查询。
     *
     * @param relief 申请
     */
    private void stubRelief(UnionRelief relief) {
        when(unionReliefRepository.findById(1L)).thenReturn(Optional.of(relief));
    }

    /**
     * 桩定上限统计为未使用。
     */
    private void stubLimitUnused() {
        when(unionReliefRepository.countApproved(anyLong(), any(), anyInt())).thenReturn(0L);
        when(unionReliefRepository.sumApprovedAmount(anyLong(), any(), anyInt())).thenReturn(BigDecimal.ZERO);
    }

    /**
     * 桩定保存回填主键。
     */
    private void stubSave() {
        when(unionReliefRepository.save(any(UnionRelief.class))).thenAnswer(invocation -> {
            UnionRelief saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved.getId();
        });
    }

    /**
     * 构造申请样本。
     *
     * @param status 状态
     * @param amount 金额
     * @return 申请
     */
    private UnionRelief relief(ReliefStatus status, String amount) {
        UnionRelief relief = new UnionRelief();
        relief.setId(1L);
        relief.setReliefNo("RF20260001");
        relief.setUnionMemberId(11L);
        relief.setApplicantUserId(9L);
        relief.setReliefType(ReliefType.DIFFICULTY);
        relief.setApplyYear(2026);
        relief.setAmount(new BigDecimal(amount));
        relief.setReason("家庭突发困难");
        relief.setStatus(status);
        relief.setApprovalInstanceId(88L);
        return relief;
    }
}
