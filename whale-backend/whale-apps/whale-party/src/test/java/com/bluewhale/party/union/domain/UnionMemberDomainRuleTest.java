package com.bluewhale.party.union.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工会会员领域规则：入会日期、退会终态、缴至月份与会费基数。
 *
 * @author BlueWhale
 */
class UnionMemberDomainRuleTest {

    @Test
    @DisplayName("入会日期晚于今天被拒")
    void validateShouldRejectFutureJoinDate() {
        UnionMember member = member();
        member.setJoinDate(LocalDate.now().plusDays(1));

        BizException exception = assertThrows(BizException.class, member::validate);
        assertEquals(PartyErrorCode.UNION_MEMBER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("已退会是终态，再次退会报状态冲突")
    void withdrawTwiceShouldConflict() {
        UnionMember member = member();
        member.validate();
        member.withdraw(LocalDate.now());
        assertEquals(UnionMemberStatus.WITHDRAWN, member.getStatus());

        BizException exception = assertThrows(BizException.class, () -> member.withdraw(LocalDate.now()));
        assertEquals(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("退会日期早于入会日期被拒")
    void withdrawShouldRejectDateBeforeJoin() {
        UnionMember member = member();
        member.setJoinDate(LocalDate.of(2026, 1, 1));
        member.validate();

        BizException exception = assertThrows(BizException.class,
                () -> member.withdraw(LocalDate.of(2025, 12, 31)));
        assertEquals(PartyErrorCode.UNION_MEMBER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("会费缴至月份必须是 yyyy-MM")
    void validateShouldRejectBadPaidMonth() {
        UnionMember member = member();
        member.setDuesPaidTo("2026-13");
        BizException exception = assertThrows(BizException.class, member::validate);
        assertEquals(PartyErrorCode.UNION_MEMBER_INVALID, exception.getCode());

        UnionMember valid = member();
        valid.setDuesPaidTo(" 2026-11 ");
        valid.validate();
        assertEquals("2026-11", valid.getDuesPaidTo());
    }

    @Test
    @DisplayName("会费基数不能为负数")
    void validateShouldRejectNegativeDuesBase() {
        UnionMember member = member();
        member.setDuesBase(new BigDecimal("-0.01"));

        BizException exception = assertThrows(BizException.class, member::validate);
        assertEquals(PartyErrorCode.UNION_MEMBER_INVALID, exception.getCode());
    }

    /**
     * 构造在册会员样本。
     *
     * @return 会员
     */
    private UnionMember member() {
        UnionMember member = new UnionMember();
        member.setMemberNo("UN20260001");
        member.setEmployeeNo("E1001");
        member.setMemberName("张三");
        member.setJoinDate(LocalDate.of(2025, 1, 1));
        member.setStatus(UnionMemberStatus.ACTIVE);
        member.setDuesBase(new BigDecimal("4000.00"));
        return member;
    }
}
