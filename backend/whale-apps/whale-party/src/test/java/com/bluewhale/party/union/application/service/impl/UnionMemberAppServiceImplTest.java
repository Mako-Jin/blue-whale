package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.UnionMemberSaveCommand;
import com.bluewhale.party.union.application.command.UnionMemberWithdrawCommand;
import com.bluewhale.party.union.application.view.UnionMemberView;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工会会员应用服务：取号入会、在册唯一、编辑保留编号、退会终态。
 *
 * @author BlueWhale
 */
class UnionMemberAppServiceImplTest {

    private UnionMemberRepository unionMemberRepository;

    private PartySequenceService sequenceService;

    private UnionMemberAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        unionMemberRepository = mock(UnionMemberRepository.class);
        sequenceService = mock(PartySequenceService.class);
        service = new UnionMemberAppServiceImpl(unionMemberRepository, sequenceService, new PartyProperties());
        actor = new PartyActor(9L, "union_admin", Set.of("union_admin"), Set.of());
    }

    @Test
    @DisplayName("入会取号建会员且状态为在册")
    void createShouldUseSequenceAndSetActive() {
        when(sequenceService.nextUnionMemberNo()).thenReturn("UN20260001");
        when(unionMemberRepository.findActiveByEmployeeNo("E1001")).thenReturn(Optional.empty());
        when(unionMemberRepository.save(any(UnionMember.class))).thenAnswer(invocation -> {
            UnionMember saved = invocation.getArgument(0);
            saved.setId(101L);
            return 101L;
        });

        UnionMemberView view = service.create(command(), actor);

        assertEquals(101L, view.getId());
        assertEquals("UN20260001", view.getMemberNo());
        assertEquals(UnionMemberStatus.ACTIVE.name(), view.getStatus());
        assertEquals("E1001", view.getEmployeeNo());
        verify(unionMemberRepository).save(any(UnionMember.class));
    }

    @Test
    @DisplayName("该员工已有在册会籍时拒绝再入会")
    void createShouldRejectDuplicatedActiveMember() {
        when(unionMemberRepository.findActiveByEmployeeNo("E1001"))
                .thenReturn(Optional.of(member(101L, UnionMemberStatus.ACTIVE)));

        BizException exception = assertThrows(BizException.class, () -> service.create(command(), actor));
        assertEquals(PartyErrorCode.UNION_MEMBER_DUPLICATED, exception.getCode());
        verify(unionMemberRepository, never()).save(any(UnionMember.class));
    }

    @Test
    @DisplayName("编辑只改可写字段，保留会员编号与状态")
    void updateShouldPreserveMemberNoAndStatus() {
        UnionMember existing = member(101L, UnionMemberStatus.ACTIVE);
        when(unionMemberRepository.findById(101L)).thenReturn(Optional.of(existing));

        UnionMemberView view = service.update(101L, command(), actor);

        assertEquals("UN20260001", view.getMemberNo());
        assertEquals(UnionMemberStatus.ACTIVE.name(), view.getStatus());
        assertEquals("李四", view.getMemberName());
        assertEquals(new BigDecimal("5000.00"), view.getDuesBase());
        verify(unionMemberRepository).save(existing);
    }

    @Test
    @DisplayName("退会写入退会日期并转为终态")
    void withdrawShouldMarkWithdrawn() {
        UnionMember existing = member(101L, UnionMemberStatus.ACTIVE);
        when(unionMemberRepository.findById(101L)).thenReturn(Optional.of(existing));
        UnionMemberWithdrawCommand command = new UnionMemberWithdrawCommand();
        command.setWithdrawDate(LocalDate.now());

        UnionMemberView view = service.withdraw(101L, command, actor);

        assertEquals(UnionMemberStatus.WITHDRAWN.name(), view.getStatus());
        assertNotNull(view.getWithdrawDate());
        verify(unionMemberRepository).save(existing);
    }

    /**
     * 构造在册会员样本。
     *
     * @param id 主键
     * @param status 状态
     * @return 会员
     */
    private UnionMember member(Long id, UnionMemberStatus status) {
        UnionMember member = new UnionMember();
        member.setId(id);
        member.setMemberNo("UN20260001");
        member.setEmployeeNo("E1001");
        member.setMemberName("张三");
        member.setDeptName("生产部");
        member.setJoinDate(LocalDate.of(2025, 1, 1));
        member.setStatus(status);
        member.setDuesBase(new BigDecimal("4000.00"));
        return member;
    }

    /**
     * 构造保存命令。
     *
     * @return 命令
     */
    private UnionMemberSaveCommand command() {
        UnionMemberSaveCommand command = new UnionMemberSaveCommand();
        command.setEmployeeNo("E1001");
        command.setMemberName("李四");
        command.setDeptName("生产部");
        command.setJoinDate(LocalDate.of(2025, 1, 1));
        command.setDuesBase(new BigDecimal("5000.00"));
        command.setDuesPaidTo("2026-11");
        return command;
    }
}
