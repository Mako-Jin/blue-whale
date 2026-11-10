package com.bluewhale.party.union.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.union.domain.model.ProposalStatus;
import com.bluewhale.party.union.domain.model.UnionProposal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 职代会提案领域规则：单向流转、评分范围与终态不可改。
 *
 * @author BlueWhale
 */
class UnionProposalDomainRuleTest {

    @Test
    @DisplayName("标题与内容必填且不超长")
    void validateShouldCheckText() {
        UnionProposal blankTitle = proposal();
        blankTitle.setTitle("  ");
        assertInvalid(blankTitle);

        UnionProposal blankContent = proposal();
        blankContent.setContent(" ");
        assertInvalid(blankContent);

        UnionProposal longContent = proposal();
        longContent.setContent("x".repeat(5001));
        assertInvalid(longContent);
    }

    @Test
    @DisplayName("完整流转依次写入各节点字段与时间")
    void fullFlowShouldWriteEachNode() {
        UnionProposal proposal = proposal();
        proposal.validate();
        proposal.file("张三");
        assertEquals(ProposalStatus.FILED, proposal.getStatus());
        assertEquals("张三", proposal.getHandler());
        assertNotNull(proposal.getFiledAt());

        proposal.handle("李四");
        assertEquals(ProposalStatus.HANDLING, proposal.getStatus());
        assertEquals("李四", proposal.getHandler());
        assertNotNull(proposal.getHandledAt());

        proposal.feedback("已纳入明年预算");
        assertEquals(ProposalStatus.FEEDBACK, proposal.getStatus());
        assertNotNull(proposal.getFeedback());
        assertNotNull(proposal.getFeedbackAt());

        proposal.rate(5);
        assertEquals(ProposalStatus.RATED, proposal.getStatus());
        assertEquals(5, proposal.getRating());
        assertNotNull(proposal.getRatedAt());
        assertTrue(proposal.isTerminal());
    }

    @Test
    @DisplayName("不可跳步：未立案不能办理，未办理不能反馈，未反馈不能评价")
    void transitionsShouldNotSkipSteps() {
        UnionProposal submitted = proposal();
        BizException handleSubmitted = assertThrows(BizException.class,
                () -> submitted.handle("李四"));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT, handleSubmitted.getCode());

        UnionProposal handling = proposal();
        handling.file(null);
        BizException feedbackFiled = assertThrows(BizException.class,
                () -> handling.feedback("反馈"));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT, feedbackFiled.getCode());

        UnionProposal feedback = proposal();
        feedback.file(null);
        feedback.handle("李四");
        BizException rateHandling = assertThrows(BizException.class, () -> feedback.rate(5));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT, rateHandling.getCode());
    }

    @Test
    @DisplayName("评分必须 1–5")
    void rateShouldCheckScoreRange() {
        UnionProposal proposal = proposal();
        proposal.file(null);
        proposal.handle("李四");
        proposal.feedback("已处理");

        BizException zero = assertThrows(BizException.class, () -> proposal.rate(0));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_INVALID, zero.getCode());

        BizException six = assertThrows(BizException.class, () -> proposal.rate(6));
        assertEquals(PartyErrorCode.UNION_PROPOSAL_INVALID, six.getCode());
    }

    @Test
    @DisplayName("终态不可回退或跳步")
    void ratedShouldBeTerminal() {
        UnionProposal proposal = proposal();
        proposal.file(null);
        proposal.handle("李四");
        proposal.feedback("已处理");
        proposal.rate(4);

        assertThrows(BizException.class, () -> proposal.rate(5));
        assertThrows(BizException.class, () -> proposal.file(null));
        assertThrows(BizException.class, () -> proposal.handle("王五"));
        assertThrows(BizException.class, () -> proposal.feedback("再反馈"));
    }

    /**
     * 断言字段非法。
     *
     * @param proposal 提案
     */
    private void assertInvalid(UnionProposal proposal) {
        BizException exception = assertThrows(BizException.class, proposal::validate);
        assertEquals(PartyErrorCode.UNION_PROPOSAL_INVALID, exception.getCode());
    }

    /**
     * 构造已提交提案。
     *
     * @return 提案
     */
    private UnionProposal proposal() {
        UnionProposal proposal = new UnionProposal();
        proposal.setProposalNo("PP20260001");
        proposal.setTitle("提高食堂餐补标准");
        proposal.setContent("建议将夜班餐补由 15 元提高至 20 元。");
        proposal.setSubmitterUserId(9L);
        proposal.setSubmitterName("张三");
        proposal.setStatus(ProposalStatus.SUBMITTED);
        return proposal;
    }
}
