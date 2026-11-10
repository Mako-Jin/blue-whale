package com.bluewhale.party.fee.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.command.FeePayCommand;
import com.bluewhale.party.fee.application.command.FeeRuleSaveCommand;
import com.bluewhale.party.fee.application.view.FeeBillView;
import com.bluewhale.party.fee.application.view.FeeGenerateResultView;
import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillStatus;
import com.bluewhale.party.fee.domain.model.FeeRule;
import com.bluewhale.party.fee.domain.repository.FeeBillRepository;
import com.bluewhale.party.fee.domain.repository.FeeRuleRepository;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
 * 党费台账应用服务：账单生成幂等、未匹配档位计数、档位重叠拦截、缴费登记。
 *
 * @author BlueWhale
 */
class FeeAppServiceImplTest {

    private FeeRuleRepository feeRuleRepository;

    private FeeBillRepository feeBillRepository;

    private PartyMemberRepository partyMemberRepository;

    private FeeAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        feeRuleRepository = mock(FeeRuleRepository.class);
        feeBillRepository = mock(FeeBillRepository.class);
        partyMemberRepository = mock(PartyMemberRepository.class);
        service = new FeeAppServiceImpl(feeRuleRepository, feeBillRepository, partyMemberRepository,
                new PartyProperties());
        actor = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
    }

    @Test
    @DisplayName("未配置档位时生成账单直接报错")
    void generateShouldFailWithoutRules() {
        when(feeRuleRepository.listActive()).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> service.generate("202611", null, actor));
        assertEquals(PartyErrorCode.FEE_RULE_MISSING, exception.getCode());
    }

    @Test
    @DisplayName("月份格式非法被拒")
    void generateShouldRejectBadPeriod() {
        BizException exception = assertThrows(BizException.class,
                () -> service.generate("2026-11", null, actor));
        assertEquals(PartyErrorCode.FEE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("生成账单：已存在的跳过、缺档位的单独计数、其余正常生成")
    void generateShouldSkipExistingAndCountNoRule() {
        when(feeRuleRepository.listActive()).thenReturn(List.of(rule(1L, "3000-5000", "3000", "5000", "0.01")));
        PartyMember first = member(1L, "张三", "4000.00", MemberStatus.ACTIVE);
        PartyMember second = member(2L, "李四", "1000.00", MemberStatus.ACTIVE);
        PartyMember third = member(3L, "王五", "4000.00", MemberStatus.ACTIVE);
        PartyMember transferred = member(4L, "赵六", "4000.00", MemberStatus.TRANSFERRED);
        when(partyMemberRepository.listPayable(null)).thenReturn(List.of(first, second, third, transferred));
        when(feeBillRepository.findByMemberAndPeriod(1L, "202611")).thenReturn(Optional.empty());
        when(feeBillRepository.findByMemberAndPeriod(2L, "202611")).thenReturn(Optional.empty());
        when(feeBillRepository.findByMemberAndPeriod(3L, "202611"))
                .thenReturn(Optional.of(existingBill(3L)));

        FeeGenerateResultView result = service.generate("202611", null, actor);

        assertEquals(1, result.getCreated());
        assertEquals(2, result.getSkipped());
        assertEquals(1, result.getNoRule());
        assertEquals(1, result.getNoRuleMembers().size());
        assertEquals("李四", result.getNoRuleMembers().get(0));
        assertEquals(new BigDecimal("40.00"), result.getDueTotal());
        verify(feeBillRepository).save(any(FeeBill.class));
    }

    @Test
    @DisplayName("档位区间重叠被拒")
    void saveRuleShouldRejectOverlap() {
        when(feeRuleRepository.listActive()).thenReturn(List.of(rule(1L, "低档", "0", "3000", "0.005")));
        FeeRuleSaveCommand command = new FeeRuleSaveCommand();
        command.setRuleName("交叉档");
        command.setMinBase(new BigDecimal("2000"));
        command.setMaxBase(new BigDecimal("6000"));
        command.setRate(new BigDecimal("0.01"));

        BizException exception = assertThrows(BizException.class, () -> service.saveRule(null, command, actor));
        assertEquals(PartyErrorCode.FEE_RULE_OVERLAP, exception.getCode());
        verify(feeRuleRepository, never()).save(any(FeeRule.class));
    }

    @Test
    @DisplayName("相接区间的档位可以保存")
    void saveRuleShouldAcceptTouchingRange() {
        when(feeRuleRepository.listActive()).thenReturn(List.of(rule(1L, "低档", "0", "3000", "0.005")));
        when(feeRuleRepository.save(any(FeeRule.class))).thenAnswer(invocation -> {
            FeeRule saved = invocation.getArgument(0);
            saved.setId(2L);
            return 2L;
        });
        FeeRuleSaveCommand command = new FeeRuleSaveCommand();
        command.setRuleName("次档");
        command.setMinBase(new BigDecimal("3000"));
        command.setMaxBase(new BigDecimal("5000"));
        command.setRate(new BigDecimal("0.01"));

        assertEquals("次档", service.saveRule(null, command, actor).getRuleName());
        verify(feeRuleRepository).save(any(FeeRule.class));
    }

    @Test
    @DisplayName("登记缴费后账单状态与欠缴金额同步")
    void payShouldUpdateStatus() {
        FeeBill bill = bill(1L, "4000.00", "0.01", "40.00");
        when(feeBillRepository.findById(1L)).thenReturn(Optional.of(bill));
        FeePayCommand command = new FeePayCommand();
        command.setAmount(new BigDecimal("40.00"));
        command.setPaidDate(LocalDate.of(2026, 11, 6));

        FeeBillView view = service.pay(1L, command, actor);

        assertEquals(FeeBillStatus.PAID.name(), view.getStatus());
        assertEquals(new BigDecimal("0.00"), view.getOutstandingAmount());
        verify(feeBillRepository).save(bill);
    }

    @Test
    @DisplayName("账单不存在时报错")
    void payShouldFailWhenBillMissing() {
        when(feeBillRepository.findById(404L)).thenReturn(Optional.empty());
        FeePayCommand command = new FeePayCommand();
        command.setAmount(new BigDecimal("1.00"));
        BizException exception = assertThrows(BizException.class, () -> service.pay(404L, command, actor));
        assertEquals(PartyErrorCode.FEE_BILL_NOT_FOUND, exception.getCode());
        verify(feeBillRepository, never()).save(any(FeeBill.class));
    }

    /**
     * 构造党费档位。
     *
     * @param id 主键
     * @param name 名称
     * @param min 下限
     * @param max 上限
     * @param rate 比例
     * @return 档位
     */
    private FeeRule rule(Long id, String name, String min, String max, String rate) {
        FeeRule rule = new FeeRule();
        rule.setId(id);
        rule.setRuleName(name);
        rule.setMinBase(new BigDecimal(min));
        rule.setMaxBase(max == null ? null : new BigDecimal(max));
        rule.setRate(new BigDecimal(rate));
        rule.setStatus("ACTIVE");
        return rule;
    }

    /**
     * 构造党员档案。
     *
     * @param id 主键
     * @param name 姓名
     * @param base 党费基数
     * @param status 状态
     * @return 档案
     */
    private PartyMember member(Long id, String name, String base, MemberStatus status) {
        PartyMember member = new PartyMember();
        member.setId(id);
        member.setMemberNo("MB2026000" + id);
        member.setEmployeeNo("E100" + id);
        member.setMemberName(name);
        member.setBranchId(1L);
        member.setDuesBase(new BigDecimal(base));
        member.setStatus(status);
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        return member;
    }

    /**
     * 构造既有账单。
     *
     * @param memberId 党员 ID
     * @return 账单
     */
    private FeeBill existingBill(Long memberId) {
        return bill(memberId, "4000.00", "0.01", "40.00");
    }

    /**
     * 构造账单。
     *
     * @param memberId 党员 ID
     * @param base 基数
     * @param rate 比例
     * @param due 应缴
     * @return 账单
     */
    private FeeBill bill(Long memberId, String base, String rate, String due) {
        FeeBill bill = new FeeBill();
        bill.setId(1000L + memberId);
        bill.setMemberId(memberId);
        bill.setBranchId(1L);
        bill.setMemberName("成员" + memberId);
        bill.setPeriodMonth("202611");
        bill.setDuesBase(new BigDecimal(base));
        bill.setRate(new BigDecimal(rate));
        bill.setDueAmount(new BigDecimal(due));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(FeeBillStatus.UNPAID);
        return bill;
    }
}
