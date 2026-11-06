package com.bluewhale.party.union.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.WelfareSignStatus;
import com.bluewhale.party.union.domain.model.WelfareStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节日福利领域规则：金额归一、状态单向、签收幂等与关闭后不可签。
 *
 * @author BlueWhale
 */
class UnionWelfareDomainRuleTest {

    @Test
    @DisplayName("名称、年度、标准金额非法时拒绝保存")
    void validateShouldCheckBasicFields() {
        UnionWelfare blankTitle = welfare();
        blankTitle.setTitle("  ");
        assertInvalid(blankTitle);

        UnionWelfare longTitle = welfare();
        longTitle.setTitle("x".repeat(129));
        assertInvalid(longTitle);

        UnionWelfare badYear = welfare();
        badYear.setWelfareYear(1999);
        assertInvalid(badYear);

        UnionWelfare futureYear = welfare();
        futureYear.setWelfareYear(2101);
        assertInvalid(futureYear);

        UnionWelfare negative = welfare();
        negative.setStandardAmount(new BigDecimal("-0.01"));
        assertInvalid(negative);
    }

    @Test
    @DisplayName("标准金额归一到两位小数（四舍五入）")
    void validateShouldScaleAmount() {
        UnionWelfare welfare = welfare();
        welfare.setStandardAmount(new BigDecimal("100.005"));
        welfare.validate();
        assertEquals(new BigDecimal("100.01"), welfare.getStandardAmount());
        assertEquals(WelfareStatus.DRAFT, welfare.getStatus());
    }

    @Test
    @DisplayName("发放仅草稿可执行，发放后冻结且记录发放时间")
    void issueShouldOnlyFromDraft() {
        UnionWelfare welfare = welfare();
        welfare.validate();
        welfare.issue();
        assertEquals(WelfareStatus.ISSUED, welfare.getStatus());
        assertNotNull(welfare.getIssuedAt());
        assertFalse(welfare.isEditable());

        BizException again = assertThrows(BizException.class, welfare::issue);
        assertEquals(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT, again.getCode());

        UnionWelfare closed = welfare();
        closed.setStatus(WelfareStatus.CLOSED);
        BizException issueClosed = assertThrows(BizException.class, closed::issue);
        assertEquals(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT, issueClosed.getCode());
    }

    @Test
    @DisplayName("关闭仅已发放可执行，关闭后为终态")
    void closeShouldOnlyFromIssued() {
        UnionWelfare draft = welfare();
        draft.validate();
        BizException closeDraft = assertThrows(BizException.class, draft::close);
        assertEquals(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT, closeDraft.getCode());

        UnionWelfare issued = welfare();
        issued.issue();
        issued.close();
        assertEquals(WelfareStatus.CLOSED, issued.getStatus());
        assertNotNull(issued.getClosedAt());
        assertFalse(issued.isIssued());

        BizException closeAgain = assertThrows(BizException.class, issued::close);
        assertEquals(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT, closeAgain.getCode());
    }

    @Test
    @DisplayName("签收幂等：重复签收不改写签收时间与签收人")
    void signShouldBeIdempotent() {
        UnionWelfareItem item = item();
        item.sign(9L);
        assertEquals(WelfareSignStatus.SIGNED, item.getSignStatus());
        assertNotNull(item.getSignedAt());
        assertEquals(9L, item.getSignedBy());

        LocalDateTime firstSignedAt = item.getSignedAt();
        item.sign(20L);
        assertEquals(firstSignedAt, item.getSignedAt());
        assertEquals(9L, item.getSignedBy());
        assertTrue(item.isSigned());
    }

    @Test
    @DisplayName("关闭后的批次不可再签收")
    void closedWelfareShouldNotBeSignable() {
        UnionWelfare welfare = welfare();
        welfare.issue();
        welfare.close();
        assertFalse(welfare.isIssued(), "关闭后批次不再处于可签收状态");
    }

    /**
     * 断言字段非法。
     *
     * @param welfare 批次
     */
    private void assertInvalid(UnionWelfare welfare) {
        BizException exception = assertThrows(BizException.class, welfare::validate);
        assertEquals(PartyErrorCode.UNION_WELFARE_INVALID, exception.getCode());
    }

    /**
     * 构造草稿批次。
     *
     * @return 批次
     */
    private UnionWelfare welfare() {
        UnionWelfare welfare = new UnionWelfare();
        welfare.setWelfareNo("WF20260001");
        welfare.setTitle("中秋福利");
        welfare.setWelfareYear(2026);
        welfare.setStandardAmount(new BigDecimal("200.00"));
        welfare.setRemark("实物发放");
        welfare.setStatus(WelfareStatus.DRAFT);
        return welfare;
    }

    /**
     * 构造未签收明细。
     *
     * @return 明细
     */
    private UnionWelfareItem item() {
        UnionWelfareItem item = new UnionWelfareItem();
        item.setId(5L);
        item.setWelfareId(1L);
        item.setUnionMemberId(11L);
        item.setEmployeeNo("E001");
        item.setMemberName("张三");
        item.setAmount(new BigDecimal("200.00"));
        item.setSignStatus(WelfareSignStatus.UNSIGNED);
        return item;
    }
}
