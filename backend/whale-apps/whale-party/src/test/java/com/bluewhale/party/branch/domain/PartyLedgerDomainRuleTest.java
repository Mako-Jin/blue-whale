package com.bluewhale.party.branch.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.activity.domain.model.ActivityStatus;
import com.bluewhale.party.activity.domain.model.ActivityType;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.fee.domain.model.DuesCalculator;
import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillStatus;
import com.bluewhale.party.fee.domain.model.FeeRule;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.model.PoliticalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 党建台账领域规则：支部状态、党员档案状态机、组织生活流转、党费档位与账单。
 *
 * @author BlueWhale
 */
class PartyLedgerDomainRuleTest {

    /** 基准日 */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Test
    @DisplayName("支部状态未知值按停用处理（失败关闭）")
    void branchStatusParseShouldFallBackToSuspended() {
        assertEquals(BranchStatus.ACTIVE, BranchStatus.parse("ACTIVE"));
        // 大小写与首尾空白按宽松处理：库里存的历史值常带空格，不该因此变成停用
        assertEquals(BranchStatus.ACTIVE, BranchStatus.parse(" active "));
        assertEquals(BranchStatus.SUSPENDED, BranchStatus.parse("unknown"));
        assertEquals(BranchStatus.SUSPENDED, BranchStatus.parse(null));
        assertFalse(BranchStatus.parse(null).isActive());
    }

    @Test
    @DisplayName("支部校验：上级不能是自身，层级必须为正")
    void branchValidateShouldRejectSelfParentAndBadLevel() {
        Branch branch = new Branch();
        branch.setId(10L);
        branch.setBranchCode("br20260001");
        branch.setBranchName(" 第一党支部 ");
        branch.setParentId(10L);
        branch.setBranchLevel(2);
        BizException exception = assertThrows(BizException.class, branch::validate);
        assertEquals(PartyErrorCode.BRANCH_PARENT_CYCLE, exception.getCode());

        branch.setParentId(0L);
        branch.setBranchLevel(0);
        BizException levelException = assertThrows(BizException.class, branch::validate);
        assertEquals(PartyErrorCode.BRANCH_PARENT_CYCLE, levelException.getCode());
    }

    @Test
    @DisplayName("支部编码归一为大写、名称去空白")
    void branchValidateShouldNormalizeText() {
        Branch branch = new Branch();
        branch.setBranchCode(" br20260002 ");
        branch.setBranchName(" 第二党支部 ");
        branch.setBranchLevel(1);
        branch.setStatus(BranchStatus.ACTIVE);
        branch.validate();
        assertEquals("BR20260002", branch.getBranchCode());
        assertEquals("第二党支部", branch.getBranchName());
        assertEquals(0L, branch.getParentId());
        assertTrue(branch.isActive());
    }

    @Test
    @DisplayName("党龄自入党日期起算，预备期计入")
    void partyAgeShouldStartFromJoinDate() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.setPoliticalStatus(PoliticalStatus.PROBATIONARY);
        assertEquals(6L, member.partyAgeYears(TODAY));
        // 入党日期晚于基准日时党龄为 0，不出现负数
        member.setJoinPartyDate(LocalDate.of(2027, 1, 1));
        assertEquals(0L, member.partyAgeYears(TODAY));
    }

    @Test
    @DisplayName("转出后档案只读，二次转出被拒")
    void transferShouldMakeMemberReadOnly() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.transferOut(LocalDate.of(2026, 6, 30));
        assertEquals(MemberStatus.TRANSFERRED, member.getStatus());
        assertEquals(LocalDate.of(2026, 6, 30), member.getTransferOutDate());
        assertTrue(member.getStatus().isReadOnly());
        assertFalse(member.getStatus().shouldPayDues());

        BizException exception = assertThrows(BizException.class, () -> member.transferOut(TODAY));
        assertEquals(PartyErrorCode.MEMBER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("正式党员不能再转正")
    void promoteShouldRejectFullMember() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.setPoliticalStatus(PoliticalStatus.FULL_MEMBER);
        BizException exception = assertThrows(BizException.class, () -> member.promoteToFull(TODAY));
        assertEquals(PartyErrorCode.MEMBER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("预备党员转正后写入转正日期并变为正式党员")
    void promoteShouldSetFullMemberDate() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2025, 11, 1));
        member.setPoliticalStatus(PoliticalStatus.PROBATIONARY);
        assertTrue(member.isProbationaryPending());
        member.promoteToFull(LocalDate.of(2026, 11, 1));
        assertEquals(PoliticalStatus.FULL_MEMBER, member.getPoliticalStatus());
        assertEquals(LocalDate.of(2026, 11, 1), member.getFullMemberDate());
        assertFalse(member.isProbationaryPending());
    }

    @Test
    @DisplayName("预备党员带转正日期视为非法档案")
    void probationaryMemberWithFullDateShouldFail() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2025, 11, 1));
        member.setPoliticalStatus(PoliticalStatus.PROBATIONARY);
        member.setFullMemberDate(LocalDate.of(2026, 11, 1));
        BizException exception = assertThrows(BizException.class, member::validate);
        assertEquals(PartyErrorCode.MEMBER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("转正日期早于入党日期被拒")
    void fullMemberDateBeforeJoinDateShouldFail() {
        PartyMember member = newMember();
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.setFullMemberDate(LocalDate.of(2020, 1, 1));
        BizException exception = assertThrows(BizException.class, member::validate);
        assertEquals(PartyErrorCode.MEMBER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("组织生活：计划 → 完成 → 归档，归档后不可再签到")
    void activityShouldFlowToArchiveAndLock() {
        PartyActivity activity = newActivity();
        activity.validate();
        assertEquals(ActivityStatus.PLANNED, activity.getStatus());

        activity.complete(LocalDate.of(2026, 11, 6), " 学习二十届三中全会精神 ");
        assertEquals(ActivityStatus.COMPLETED, activity.getStatus());
        assertEquals("学习二十届三中全会精神", activity.getSummary());

        activity.archive();
        assertEquals(ActivityStatus.ARCHIVED, activity.getStatus());
        BizException exception = assertThrows(BizException.class, activity::assertSignable);
        assertEquals(PartyErrorCode.ACTIVITY_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("未完成的组织生活不能归档，缺纪要也不能归档")
    void archiveShouldRequireCompletedAndSummary() {
        PartyActivity planned = newActivity();
        planned.validate();
        BizException plannedException = assertThrows(BizException.class, planned::archive);
        assertEquals(PartyErrorCode.ACTIVITY_STATE_CONFLICT, plannedException.getCode());

        PartyActivity noSummary = newActivity();
        noSummary.validate();
        noSummary.complete(LocalDate.of(2026, 11, 6), "   ");
        BizException summaryException = assertThrows(BizException.class, noSummary::archive);
        assertEquals(PartyErrorCode.ACTIVITY_STATE_CONFLICT, summaryException.getCode());
    }

    @Test
    @DisplayName("党费档位按左闭右开匹配，边界落在高档")
    void feeRuleShouldMatchLeftClosedRange() {
        List<FeeRule> rules = defaultRules();
        assertEquals("3000 元以下", DuesCalculator.match(new BigDecimal("2999.99"), rules).getRuleName());
        assertEquals("3000-5000 元", DuesCalculator.match(new BigDecimal("3000"), rules).getRuleName());
        assertEquals("3000-5000 元", DuesCalculator.match(new BigDecimal("4999.99"), rules).getRuleName());
        assertEquals("5000-10000 元", DuesCalculator.match(new BigDecimal("5000"), rules).getRuleName());
        assertEquals("10000 元以上", DuesCalculator.match(new BigDecimal("10000"), rules).getRuleName());
    }

    @Test
    @DisplayName("档位不重叠检测：相接不算重叠，交叉算重叠")
    void feeRuleOverlapShouldDetectCrossingButNotTouching() {
        FeeRule lower = rule("低档", "0", "3000", "0.005");
        FeeRule next = rule("次档", "3000", "5000", "0.01");
        FeeRule crossing = rule("交叉", "2500", "6000", "0.02");
        assertFalse(lower.overlaps(next));
        assertTrue(lower.overlaps(crossing));
        assertTrue(next.overlaps(crossing));
    }

    @Test
    @DisplayName("党费金额四舍五入到分（HALF_UP）")
    void duesShouldRoundHalfUp() {
        List<FeeRule> rules = defaultRules();
        assertEquals(new BigDecimal("5.00"), DuesCalculator.dueOf(new BigDecimal("1000"), rules));
        assertEquals(new BigDecimal("30.01"), DuesCalculator.dueOf(new BigDecimal("3001"), rules));
        assertEquals(new BigDecimal("200.00"), DuesCalculator.dueOf(new BigDecimal("10000"), rules));
    }

    @Test
    @DisplayName("基数未匹配档位时报错而不是按 0 处理")
    void duesShouldFailWhenNoRuleMatched() {
        FeeRule rule = rule("仅高档", "5000", null, "0.01");
        BizException exception = assertThrows(BizException.class,
                () -> DuesCalculator.match(new BigDecimal("100"), List.of(rule)));
        assertEquals(PartyErrorCode.FEE_RULE_MISSING, exception.getCode());
    }

    @Test
    @DisplayName("账单分次缴纳：部分 → 缴清，超额被拒")
    void billPayShouldSupportPartialThenFull() {
        FeeBill bill = newBill();
        bill.pay(new BigDecimal("5.00"), LocalDate.of(2026, 11, 5));
        assertEquals(FeeBillStatus.PARTIAL, bill.getStatus());
        assertEquals(new BigDecimal("5.00"), bill.getPaidAmount());

        bill.pay(new BigDecimal("5.00"), LocalDate.of(2026, 11, 6));
        assertEquals(FeeBillStatus.PAID, bill.getStatus());
        assertEquals(new BigDecimal("10.00"), bill.getPaidAmount());

        BizException exception = assertThrows(BizException.class,
                () -> bill.pay(new BigDecimal("1.00"), TODAY));
        assertEquals(PartyErrorCode.FEE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("免缴账单不可再登记缴费，已缴清不可改免缴")
    void billWaiveShouldLockPayment() {
        FeeBill waived = newBill();
        waived.waive("困难党员");
        assertEquals(FeeBillStatus.WAIVED, waived.getStatus());
        assertFalse(waived.isOutstanding());
        BizException payException = assertThrows(BizException.class,
                () -> waived.pay(new BigDecimal("1.00"), TODAY));
        assertEquals(PartyErrorCode.FEE_INVALID, payException.getCode());

        FeeBill paid = newBill();
        paid.pay(new BigDecimal("10.00"), TODAY);
        BizException waiveException = assertThrows(BizException.class, () -> paid.waive("x"));
        assertEquals(PartyErrorCode.FEE_INVALID, waiveException.getCode());
    }

    @Test
    @DisplayName("月份格式校验：必须 yyyyMM 且月份合法")
    void billPeriodShouldBeValidated() {
        assertTrue(FeeBill.isValidPeriod("202611"));
        assertFalse(FeeBill.isValidPeriod("2026-11"));
        assertFalse(FeeBill.isValidPeriod("202613"));
        assertFalse(FeeBill.isValidPeriod("20261"));
        assertFalse(FeeBill.isValidPeriod("199912"));
        assertNull(newBill().getPaidDate());
    }

    /**
     * 构造一个字段齐全的党员档案。
     *
     * @return 档案
     */
    private PartyMember newMember() {
        PartyMember member = new PartyMember();
        member.setMemberNo("mb20260001");
        member.setEmployeeNo("E1001");
        member.setMemberName("张三");
        member.setBranchId(1L);
        member.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        member.setDuesBase(new BigDecimal("1000.00"));
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }

    /**
     * 构造组织生活。
     *
     * @return 组织生活
     */
    private PartyActivity newActivity() {
        PartyActivity activity = new PartyActivity();
        activity.setBranchId(1L);
        activity.setActivityType(ActivityType.THEME_DAY);
        activity.setTitle(" 主题党日 ");
        activity.setPlannedDate(LocalDate.of(2026, 11, 6));
        activity.setExpectedCount(10);
        return activity;
    }

    /**
     * 构造一档党费标准。
     *
     * @param name 名称
     * @param min 下限
     * @param max 上限（可空）
     * @param rate 比例
     * @return 档位
     */
    private FeeRule rule(String name, String min, String max, String rate) {
        FeeRule rule = new FeeRule();
        rule.setRuleName(name);
        rule.setMinBase(new BigDecimal(min));
        rule.setMaxBase(max == null ? null : new BigDecimal(max));
        rule.setRate(new BigDecimal(rate));
        rule.setStatus("ACTIVE");
        rule.validate();
        return rule;
    }

    /**
     * 构造标准四档。
     *
     * @return 档位列表
     */
    private List<FeeRule> defaultRules() {
        return List.of(
                rule("3000 元以下", "0", "3000", "0.005"),
                rule("3000-5000 元", "3000", "5000", "0.01"),
                rule("5000-10000 元", "5000", "10000", "0.015"),
                rule("10000 元以上", "10000", null, "0.02"));
    }

    /**
     * 构造一张 10 元应缴账单。
     *
     * @return 账单
     */
    private FeeBill newBill() {
        return FeeBill.of(1L, 1L, "张三", "202611", new BigDecimal("1000.00"),
                rule("档位", "0", null, "0.01"));
    }
}
