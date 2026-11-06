package com.bluewhale.party.union.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.ReliefType;
import com.bluewhale.party.union.domain.model.UnionRelief;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 慰问帮扶领域规则：金额与事由、状态机单向流转。
 *
 * @author BlueWhale
 */
class UnionReliefDomainRuleTest {

    @Test
    @DisplayName("金额必须大于 0 且不超过上限")
    void validateShouldCheckAmount() {
        UnionRelief relief = draft();
        relief.setAmount(BigDecimal.ZERO);
        assertInvalid(relief);

        UnionRelief tooLarge = draft();
        tooLarge.setAmount(new BigDecimal("1000000000.00"));
        assertInvalid(tooLarge);

        UnionRelief valid = draft();
        valid.setAmount(new BigDecimal("100.005"));
        valid.validate();
        assertEquals(new BigDecimal("100.01"), valid.getAmount());
    }

    @Test
    @DisplayName("事由必填且不超过 500 字符")
    void validateShouldRequireReason() {
        UnionRelief blank = draft();
        blank.setReason("  ");
        assertInvalid(blank);

        UnionRelief tooLong = draft();
        tooLong.setReason("x".repeat(501));
        assertInvalid(tooLong);
    }

    @Test
    @DisplayName("申请年度越界被拒")
    void validateShouldCheckYear() {
        UnionRelief relief = draft();
        relief.setApplyYear(1999);
        assertInvalid(relief);
    }

    @Test
    @DisplayName("仅草稿可以提交，提交后不可重复提交")
    void submitShouldOnlyFromDraft() {
        UnionRelief relief = draft();
        relief.validate();
        relief.submit();
        assertEquals(ReliefStatus.SUBMITTED, relief.getStatus());

        BizException exception = assertThrows(BizException.class, relief::submit);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("仅审批中可以标记通过或退回")
    void approveAndRejectShouldOnlyFromSubmitted() {
        UnionRelief draft = draft();
        draft.validate();
        BizException approveDraft = assertThrows(BizException.class, draft::markApproved);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, approveDraft.getCode());

        UnionRelief approved = submitted();
        approved.markApproved();
        assertEquals(ReliefStatus.APPROVED, approved.getStatus());
        BizException approveAgain = assertThrows(BizException.class, approved::markApproved);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, approveAgain.getCode());

        UnionRelief rejectedOk = submitted();
        rejectedOk.markRejected();
        assertEquals(ReliefStatus.REJECTED, rejectedOk.getStatus());
        assertTrue(rejectedOk.getStatus().isTerminal());
        BizException approveRejected = assertThrows(BizException.class, rejectedOk::markApproved);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, approveRejected.getCode());
    }

    @Test
    @DisplayName("仅审批通过可以发放，发放后为终态")
    void payShouldOnlyFromApproved() {
        UnionRelief draft = draft();
        draft.validate();
        BizException payDraft = assertThrows(BizException.class, draft::pay);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, payDraft.getCode());

        UnionRelief submitted = submitted();
        BizException paySubmitted = assertThrows(BizException.class, submitted::pay);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, paySubmitted.getCode());

        UnionRelief approved = submitted();
        approved.markApproved();
        approved.pay();
        assertEquals(ReliefStatus.PAID, approved.getStatus());
        assertTrue(approved.getStatus().isTerminal());
        assertTrue(approved.getStatus().isApprovedLike());

        BizException payAgain = assertThrows(BizException.class, approved::pay);
        assertEquals(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, payAgain.getCode());
    }

    /**
     * 断言字段非法。
     *
     * @param relief 申请
     */
    private void assertInvalid(UnionRelief relief) {
        BizException exception = assertThrows(BizException.class, relief::validate);
        assertEquals(PartyErrorCode.UNION_RELIEF_INVALID, exception.getCode());
    }

    /**
     * 构造草稿申请。
     *
     * @return 草稿
     */
    private UnionRelief draft() {
        UnionRelief relief = new UnionRelief();
        relief.setReliefNo("RF20260001");
        relief.setUnionMemberId(11L);
        relief.setApplicantUserId(9L);
        relief.setReliefType(ReliefType.DIFFICULTY);
        relief.setApplyYear(2026);
        relief.setAmount(new BigDecimal("100.00"));
        relief.setReason("家庭突发困难");
        relief.setStatus(ReliefStatus.DRAFT);
        return relief;
    }

    /**
     * 构造审批中的申请。
     *
     * @return 审批中申请
     */
    private UnionRelief submitted() {
        UnionRelief relief = draft();
        relief.validate();
        relief.submit();
        return relief;
    }
}
