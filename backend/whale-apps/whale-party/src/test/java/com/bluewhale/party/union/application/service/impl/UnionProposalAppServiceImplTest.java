package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.ProposalHandleCommand;
import com.bluewhale.party.union.application.command.ProposalRateCommand;
import com.bluewhale.party.union.domain.model.ProposalStatus;
import com.bluewhale.party.union.domain.model.UnionProposal;
import com.bluewhale.party.union.domain.repository.UnionProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 职代会提案应用服务：评价归属、状态跳步、办理权限。
 *
 * @author BlueWhale
 */
class UnionProposalAppServiceImplTest {

    private UnionProposalRepository unionProposalRepository;

    private UnionProposalAppServiceImpl service;

    @BeforeEach
    void setUp() {
        unionProposalRepository = mock(UnionProposalRepository.class);
        service = new UnionProposalAppServiceImpl(unionProposalRepository,
                mock(PartySequenceService.class), new PartyProperties());
    }

    @Test
    @DisplayName("非提交人评价被拒")
    void rateShouldRejectNonSubmitter() {
        stubProposal(proposal(ProposalStatus.FEEDBACK, 10L));
        PartyActor other = new PartyActor(20L, "zhangsan", Set.of("employee"), Set.of());
        ProposalRateCommand command = new ProposalRateCommand();
        command.setRating(5);

        BizException exception = assertThrows(BizException.class,
                () -> service.rate(1L, command, other));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_FORBIDDEN, exception.getCode());
        verify(unionProposalRepository, never()).save(any(UnionProposal.class));
    }

    @Test
    @DisplayName("状态跳步：已提交提案直接办理被拒")
    void handleShouldRejectSkippedStatus() {
        stubProposal(proposal(ProposalStatus.SUBMITTED, 10L));
        PartyActor admin = new PartyActor(9L, "union_admin", Set.of("union_admin"), Set.of());
        ProposalHandleCommand command = new ProposalHandleCommand();
        command.setHandler("李四");

        BizException exception = assertThrows(BizException.class,
                () -> service.handle(1L, command, admin));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT, exception.getCode());
        verify(unionProposalRepository, never()).save(any(UnionProposal.class));
    }

    @Test
    @DisplayName("办理需要工会管理员权限")
    void handleShouldRequireManageRole() {
        stubProposal(proposal(ProposalStatus.FILED, 10L));
        PartyActor normal = new PartyActor(20L, "zhangsan", Set.of("employee"), Set.of());
        ProposalHandleCommand command = new ProposalHandleCommand();
        command.setHandler("李四");

        BizException exception = assertThrows(BizException.class,
                () -> service.handle(1L, command, normal));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_FORBIDDEN, exception.getCode());
        verify(unionProposalRepository, never()).save(any(UnionProposal.class));
    }

    @Test
    @DisplayName("提交人评价成功后进入终态")
    void rateShouldSucceedForSubmitter() {
        stubProposal(proposal(ProposalStatus.FEEDBACK, 10L));
        PartyActor submitter = new PartyActor(10L, "zhangsan", Set.of("employee"), Set.of());
        ProposalRateCommand command = new ProposalRateCommand();
        command.setRating(4);

        assertEquals("RATED", service.rate(1L, command, submitter).getStatus());
        verify(unionProposalRepository).save(any(UnionProposal.class));
    }

    /**
     * 桩定提案查询。
     *
     * @param proposal 提案
     */
    private void stubProposal(UnionProposal proposal) {
        when(unionProposalRepository.findById(1L)).thenReturn(Optional.of(proposal));
    }

    /**
     * 构造提案样本。
     *
     * @param status 状态
     * @param submitterUserId 提交人用户 ID
     * @return 提案
     */
    private UnionProposal proposal(ProposalStatus status, Long submitterUserId) {
        UnionProposal proposal = new UnionProposal();
        proposal.setId(1L);
        proposal.setProposalNo("PP20260001");
        proposal.setTitle("提高食堂餐补标准");
        proposal.setContent("建议将夜班餐补由 15 元提高至 20 元。");
        proposal.setSubmitterUserId(submitterUserId);
        proposal.setSubmitterName("张三");
        proposal.setStatus(status);
        return proposal;
    }
}
