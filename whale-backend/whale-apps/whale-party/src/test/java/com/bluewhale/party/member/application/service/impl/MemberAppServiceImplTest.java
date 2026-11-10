package com.bluewhale.party.member.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.command.MemberTransferCommand;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 党员台账应用服务：一人一档、支部在册校验、转出后只读。
 *
 * @author BlueWhale
 */
class MemberAppServiceImplTest {

    private PartyMemberRepository memberRepository;

    private BranchRepository branchRepository;

    private PartySequenceService sequenceService;

    private MemberAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        memberRepository = mock(PartyMemberRepository.class);
        branchRepository = mock(BranchRepository.class);
        sequenceService = mock(PartySequenceService.class);
        service = new MemberAppServiceImpl(memberRepository, branchRepository, sequenceService,
                new PartyProperties());
        actor = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
    }

    @Test
    @DisplayName("同一员工重复建档被拒")
    void createShouldRejectDuplicatedEmployee() {
        when(memberRepository.findByEmployeeNo("E1001")).thenReturn(Optional.of(newMember()));

        BizException exception = assertThrows(BizException.class, () -> service.create(command(), actor));
        assertEquals(PartyErrorCode.MEMBER_DUPLICATED, exception.getCode());
        verify(memberRepository, never()).save(any(PartyMember.class));
    }

    @Test
    @DisplayName("目标支部停用时不允许挂入党员")
    void createShouldRejectInactiveBranch() {
        when(memberRepository.findByEmployeeNo("E1001")).thenReturn(Optional.empty());
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(BranchStatus.SUSPENDED)));

        BizException exception = assertThrows(BizException.class, () -> service.create(command(), actor));
        assertEquals(PartyErrorCode.BRANCH_INACTIVE, exception.getCode());
    }

    @Test
    @DisplayName("建档成功：编号取流水、状态在册")
    void createShouldGenerateMemberNo() {
        when(memberRepository.findByEmployeeNo("E1001")).thenReturn(Optional.empty());
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(BranchStatus.ACTIVE)));
        when(sequenceService.nextMemberNo()).thenReturn("MB20260001");
        when(memberRepository.save(any(PartyMember.class))).thenAnswer(invocation -> {
            PartyMember saved = invocation.getArgument(0);
            saved.setId(88L);
            return 88L;
        });

        MemberView view = service.create(command(), actor);

        assertEquals("MB20260001", view.getMemberNo());
        assertEquals("ACTIVE", view.getStatus());
        assertEquals("第一党支部", view.getBranchName());
    }

    @Test
    @DisplayName("已转出档案不可编辑")
    void updateShouldRejectTransferredMember() {
        PartyMember member = newMember();
        member.transferOut(LocalDate.of(2026, 6, 30));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        BizException exception = assertThrows(BizException.class, () -> service.update(1L, command(), actor));
        assertEquals(PartyErrorCode.MEMBER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("转出成功：状态转 TRANSFERRED 并记录日期与说明")
    void transferShouldRecordDateAndRemark() {
        PartyMember member = newMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(BranchStatus.ACTIVE)));

        MemberTransferCommand command = new MemberTransferCommand();
        command.setTransferDate(LocalDate.of(2026, 11, 6));
        command.setRemark("转至集团党委");
        MemberView view = service.transfer(1L, command, actor);

        assertEquals(MemberStatus.TRANSFERRED.name(), view.getStatus());
        assertEquals(LocalDate.of(2026, 11, 6), view.getTransferOutDate());
        assertEquals("转至集团党委", view.getRemark());
    }

    @Test
    @DisplayName("档案不存在时报错")
    void detailShouldFailWhenMissing() {
        when(memberRepository.findById(404L)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class, () -> service.detail(404L, actor));
        assertEquals(PartyErrorCode.MEMBER_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造保存命令。
     *
     * @return 命令
     */
    private MemberSaveCommand command() {
        MemberSaveCommand command = new MemberSaveCommand();
        command.setEmployeeNo("E1001");
        command.setMemberName("张三");
        command.setBranchId(1L);
        command.setPoliticalStatus("FULL_MEMBER");
        command.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        command.setDuesBase(new BigDecimal("1000.00"));
        return command;
    }

    /**
     * 构造支部。
     *
     * @param status 状态
     * @return 支部
     */
    private Branch branch(BranchStatus status) {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("BR20260001");
        branch.setBranchName("第一党支部");
        branch.setBranchLevel(1);
        branch.setStatus(status);
        branch.setParentId(0L);
        return branch;
    }

    /**
     * 构造党员档案。
     *
     * @return 档案
     */
    private PartyMember newMember() {
        PartyMember member = new PartyMember();
        member.setId(1L);
        member.setMemberNo("MB20260001");
        member.setEmployeeNo("E1001");
        member.setMemberName("张三");
        member.setBranchId(1L);
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.setDuesBase(new BigDecimal("1000.00"));
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }
}
