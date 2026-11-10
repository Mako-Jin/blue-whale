package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.WelfareIssueCommand;
import com.bluewhale.party.union.application.view.UnionWelfareItemView;
import com.bluewhale.party.union.application.view.UnionWelfareView;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.WelfareSignStatus;
import com.bluewhale.party.union.domain.model.WelfareStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.domain.repository.UnionWelfareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节日福利应用服务：发放快照、指定会员校验、明细幂等、签收权限。
 *
 * @author BlueWhale
 */
class UnionWelfareAppServiceImplTest {

    private UnionWelfareRepository unionWelfareRepository;

    private UnionMemberRepository unionMemberRepository;

    private UnionWelfareAppServiceImpl service;

    private PartyActor admin;

    @BeforeEach
    void setUp() {
        unionWelfareRepository = mock(UnionWelfareRepository.class);
        unionMemberRepository = mock(UnionMemberRepository.class);
        service = new UnionWelfareAppServiceImpl(unionWelfareRepository, unionMemberRepository,
                mock(PartySequenceService.class), new PartyProperties());
        admin = new PartyActor(9L, "union_admin", Set.of("union_admin"), Set.of());
    }

    @Test
    @DisplayName("名单为空时向全部在册会员发放并快照标准金额")
    void issueShouldSnapshotAllActiveMembers() {
        stubWelfare(welfare(WelfareStatus.DRAFT));
        when(unionWelfareRepository.listItems(1L)).thenReturn(List.of());
        when(unionMemberRepository.listActive()).thenReturn(List.of(
                member(11L, "E001", "张三"), member(12L, "E002", "李四")));

        UnionWelfareView view = service.issue(1L, new WelfareIssueCommand(), admin);

        assertEquals(WelfareStatus.ISSUED.name(), view.getStatus());
        assertEquals(2, view.getIssueCount());
        assertNotNull(view.getIssuedAt());

        ArgumentCaptor<UnionWelfareItem> captor = ArgumentCaptor.forClass(UnionWelfareItem.class);
        verify(unionWelfareRepository, times(2)).saveItem(captor.capture());
        List<UnionWelfareItem> items = captor.getAllValues();
        assertEquals(new BigDecimal("200.00"), items.get(0).getAmount());
        assertEquals("E001", items.get(0).getEmployeeNo());
        assertEquals("张三", items.get(0).getMemberName());
        assertEquals(WelfareSignStatus.UNSIGNED, items.get(0).getSignStatus());
        assertEquals(11L, items.get(0).getUnionMemberId());
        verify(unionWelfareRepository).save(any(UnionWelfare.class));
    }

    @Test
    @DisplayName("指定会员不存在时拒绝发放")
    void issueShouldRejectMissingMember() {
        stubWelfare(welfare(WelfareStatus.DRAFT));
        when(unionMemberRepository.findById(11L)).thenReturn(Optional.of(member(11L, "E001", "张三")));
        when(unionMemberRepository.findById(99L)).thenReturn(Optional.empty());
        WelfareIssueCommand command = new WelfareIssueCommand();
        command.setMemberIds(List.of(11L, 99L));

        BizException exception = assertThrows(BizException.class,
                () -> service.issue(1L, command, admin));
        assertEquals(PartyErrorCode.UNION_MEMBER_NOT_FOUND, exception.getCode());
        verify(unionWelfareRepository, never()).saveItem(any(UnionWelfareItem.class));
    }

    @Test
    @DisplayName("指定已退会会员时拒绝发放")
    void issueShouldRejectWithdrawnMember() {
        stubWelfare(welfare(WelfareStatus.DRAFT));
        when(unionMemberRepository.findById(11L))
                .thenReturn(Optional.of(member(11L, "E001", "张三", UnionMemberStatus.WITHDRAWN)));
        WelfareIssueCommand command = new WelfareIssueCommand();
        command.setMemberIds(List.of(11L));

        BizException exception = assertThrows(BizException.class,
                () -> service.issue(1L, command, admin));
        assertEquals(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT, exception.getCode());
        verify(unionWelfareRepository, never()).saveItem(any(UnionWelfareItem.class));
    }

    @Test
    @DisplayName("已存在明细的会员跳过，不追加重名记录")
    void issueShouldSkipExistingItems() {
        stubWelfare(welfare(WelfareStatus.DRAFT));
        when(unionMemberRepository.listActive()).thenReturn(List.of(
                member(11L, "E001", "张三"), member(12L, "E002", "李四")));
        when(unionWelfareRepository.listItems(1L)).thenReturn(List.of(item(11L)));

        UnionWelfareView view = service.issue(1L, new WelfareIssueCommand(), admin);

        assertEquals(1, view.getIssueCount());
        ArgumentCaptor<UnionWelfareItem> captor = ArgumentCaptor.forClass(UnionWelfareItem.class);
        verify(unionWelfareRepository, times(1)).saveItem(captor.capture());
        assertEquals(12L, captor.getValue().getUnionMemberId());
    }

    @Test
    @DisplayName("批次关闭后签收被拒")
    void signShouldRejectClosedWelfare() {
        when(unionWelfareRepository.findItemById(5L)).thenReturn(Optional.of(item(11L)));
        when(unionWelfareRepository.findById(1L)).thenReturn(Optional.of(welfare(WelfareStatus.CLOSED)));

        BizException exception = assertThrows(BizException.class, () -> service.sign(5L, admin));
        assertEquals(PartyErrorCode.UNION_WELFARE_ITEM_STATE_CONFLICT, exception.getCode());
        verify(unionWelfareRepository, never()).saveItem(any(UnionWelfareItem.class));
    }

    @Test
    @DisplayName("非本人且非管理员签收被拒")
    void signShouldRejectOtherMember() {
        PartyActor other = new PartyActor(20L, "zhangsan", Set.of("employee"), Set.of());
        when(unionWelfareRepository.findItemById(5L)).thenReturn(Optional.of(item(11L)));
        when(unionWelfareRepository.findById(1L)).thenReturn(Optional.of(welfare(WelfareStatus.ISSUED)));
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(12L, "E002", "李四")));

        BizException exception = assertThrows(BizException.class, () -> service.sign(5L, other));
        assertEquals(PartyErrorCode.UNION_WELFARE_ITEM_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("本人签收成功并记录签收人")
    void signShouldSucceedForOwner() {
        PartyActor owner = new PartyActor(20L, "zhangsan", Set.of("employee"), Set.of());
        when(unionWelfareRepository.findItemById(5L)).thenReturn(Optional.of(item(11L)));
        when(unionWelfareRepository.findById(1L)).thenReturn(Optional.of(welfare(WelfareStatus.ISSUED)));
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(11L, "E001", "张三")));

        UnionWelfareItemView view = service.sign(5L, owner);

        assertEquals(WelfareSignStatus.SIGNED.name(), view.getSignStatus());
        assertNotNull(view.getSignedAt());
        assertEquals("中秋福利", view.getWelfareTitle());
        ArgumentCaptor<UnionWelfareItem> captor = ArgumentCaptor.forClass(UnionWelfareItem.class);
        verify(unionWelfareRepository).saveItem(captor.capture());
        assertEquals(20L, captor.getValue().getSignedBy());
    }

    @Test
    @DisplayName("已签收明细再次签收按幂等返回，不重复落库")
    void signShouldBeIdempotent() {
        UnionWelfareItem signed = item(11L);
        signed.sign(9L);
        when(unionWelfareRepository.findItemById(5L)).thenReturn(Optional.of(signed));
        when(unionWelfareRepository.findById(1L)).thenReturn(Optional.of(welfare(WelfareStatus.ISSUED)));

        UnionWelfareItemView view = service.sign(5L, admin);

        assertEquals(WelfareSignStatus.SIGNED.name(), view.getSignStatus());
        verify(unionWelfareRepository, never()).saveItem(any(UnionWelfareItem.class));
    }

    /**
     * 桩定批次查询。
     *
     * @param welfare 批次
     */
    private void stubWelfare(UnionWelfare welfare) {
        when(unionWelfareRepository.findById(1L)).thenReturn(Optional.of(welfare));
    }

    /**
     * 构造批次样本。
     *
     * @param status 状态
     * @return 批次
     */
    private UnionWelfare welfare(WelfareStatus status) {
        UnionWelfare welfare = new UnionWelfare();
        welfare.setId(1L);
        welfare.setWelfareNo("WF20260001");
        welfare.setTitle("中秋福利");
        welfare.setWelfareYear(2026);
        welfare.setStandardAmount(new BigDecimal("200.00"));
        welfare.setStatus(status);
        return welfare;
    }

    /**
     * 构造明细样本。
     *
     * @param memberId 会员 ID
     * @return 明细
     */
    private UnionWelfareItem item(Long memberId) {
        UnionWelfareItem item = new UnionWelfareItem();
        item.setId(5L);
        item.setWelfareId(1L);
        item.setUnionMemberId(memberId);
        item.setEmployeeNo("E001");
        item.setMemberName("张三");
        item.setAmount(new BigDecimal("200.00"));
        item.setSignStatus(WelfareSignStatus.UNSIGNED);
        return item;
    }

    /**
     * 构造在册会员。
     *
     * @param id 会员 ID
     * @param employeeNo 员工编号
     * @param name 姓名
     * @return 会员
     */
    private UnionMember member(Long id, String employeeNo, String name) {
        return member(id, employeeNo, name, UnionMemberStatus.ACTIVE);
    }

    /**
     * 构造会员。
     *
     * @param id 会员 ID
     * @param employeeNo 员工编号
     * @param name 姓名
     * @param status 会籍状态
     * @return 会员
     */
    private UnionMember member(Long id, String employeeNo, String name, UnionMemberStatus status) {
        UnionMember member = new UnionMember();
        member.setId(id);
        member.setEmployeeNo(employeeNo);
        member.setMemberName(name);
        member.setStatus(status);
        return member;
    }
}
