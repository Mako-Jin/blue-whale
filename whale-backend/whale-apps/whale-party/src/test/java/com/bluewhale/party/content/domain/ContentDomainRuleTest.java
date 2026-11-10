package com.bluewhale.party.content.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.content.domain.model.ContentScope;
import com.bluewhale.party.content.domain.model.ContentStatus;
import com.bluewhale.party.content.domain.model.ContentVisibility;
import com.bluewhale.party.content.domain.model.PartyContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 党建内容领域规则：先审后发状态机、可见范围、退回意见必填、阅读计数。
 *
 * @author BlueWhale
 */
class ContentDomainRuleTest {

    @Test
    @DisplayName("状态与可见范围未知值按保守口径回落")
    void enumParseShouldFallBackSafely() {
        assertEquals(ContentStatus.DRAFT, ContentStatus.parse(null));
        assertEquals(ContentStatus.DRAFT, ContentStatus.parse("unknown"));
        assertEquals(ContentStatus.PUBLISHED, ContentStatus.parse(" published "));
        assertEquals(ContentVisibility.ALL_MEMBERS, ContentVisibility.parse(null));
        assertEquals(ContentVisibility.ALL_STAFF, ContentVisibility.parse("all_staff"));
    }

    @Test
    @DisplayName("字段归一：标题去空白、附件去重、非指定支部清空支部范围")
    void validateShouldNormalizeFields() {
        PartyContent content = draft();
        content.setTitle(" 学习二十大精神 ");
        content.setAttachmentFileIds(List.of(1L, 1L, 0L, 2L));
        content.setVisibility(ContentVisibility.ALL_STAFF);
        content.setBranchIds(List.of(7L));
        content.validate();
        assertEquals("学习二十大精神", content.getTitle());
        assertEquals(List.of(1L, 2L), content.getAttachmentFileIds());
        assertTrue(content.getBranchIds().isEmpty());
        assertEquals(ContentStatus.DRAFT, content.getStatus());
    }

    @Test
    @DisplayName("指定支部未选支部被拒")
    void validateShouldRejectBranchesWithoutScope() {
        PartyContent content = draft();
        content.setVisibility(ContentVisibility.BRANCHES);
        content.setBranchIds(List.of());
        BizException exception = assertThrows(BizException.class, content::validate);
        assertEquals(PartyErrorCode.CONTENT_VISIBILITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("提交审核要求正文非空")
    void submitShouldRequireBody() {
        PartyContent content = draft();
        content.setBody(null);
        content.validate();
        BizException exception = assertThrows(BizException.class, content::submit);
        assertEquals(PartyErrorCode.CONTENT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("单向流转：草稿→待审→已发布→已下线，下线后不可再操作")
    void stateMachineShouldBeForwardOnly() {
        PartyContent content = draft();
        content.validate();
        content.submit();
        assertEquals(ContentStatus.PENDING, content.getStatus());
        content.approve();
        assertEquals(ContentStatus.PUBLISHED, content.getStatus());
        content.offline();
        assertEquals(ContentStatus.OFFLINE, content.getStatus());
        assertTrue(content.getStatus().isTerminal());

        BizException exception = assertThrows(BizException.class, content::offline);
        assertEquals(PartyErrorCode.CONTENT_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("退回必须填意见，退回后回到草稿")
    void rejectShouldRequireOpinion() {
        PartyContent content = submitted();
        BizException blank = assertThrows(BizException.class, () -> content.reject("  "));
        assertEquals(PartyErrorCode.CONTENT_AUDIT_OPINION_REQUIRED, blank.getCode());

        content.reject("请补充活动照片");
        assertEquals(ContentStatus.DRAFT, content.getStatus());
    }

    @Test
    @DisplayName("发布态修改自动退回待审；待审与已下线不可编辑")
    void prepareEditShouldResubmitPublishedContent() {
        PartyContent content = draft();
        content.validate();
        assertFalse(content.prepareEdit());

        content.submit();
        BizException pending = assertThrows(BizException.class, content::prepareEdit);
        assertEquals(PartyErrorCode.CONTENT_STATE_CONFLICT, pending.getCode());

        content.approve();
        assertTrue(content.prepareEdit());
        assertEquals(ContentStatus.PENDING, content.getStatus());

        content.approve();
        content.offline();
        BizException offline = assertThrows(BizException.class, content::prepareEdit);
        assertEquals(PartyErrorCode.CONTENT_STATE_CONFLICT, offline.getCode());
    }

    @Test
    @DisplayName("可见范围：未发布不可见；全员对任何人可见；全体党员需党员身份；指定支部需命中")
    void visibilityShouldFollowScope() {
        PartyContent draft = draft();
        draft.validate();
        assertFalse(draft.isVisibleTo(ContentScope.none()));
        assertFalse(draft.isVisibleTo(ContentScope.manager()));

        draft.setStatus(ContentStatus.PUBLISHED);
        assertFalse(draft.isVisibleTo(ContentScope.none()));
        assertFalse(draft.isVisibleTo(ContentScope.of(false, List.of())));
        assertTrue(draft.isVisibleTo(ContentScope.of(true, List.of())));
        assertTrue(draft.isVisibleTo(ContentScope.manager()));

        draft.setVisibility(ContentVisibility.ALL_STAFF);
        assertTrue(draft.isVisibleTo(ContentScope.none()));

        draft.setVisibility(ContentVisibility.BRANCHES);
        draft.setBranchIds(List.of(7L));
        assertFalse(draft.isVisibleTo(ContentScope.of(true, List.of(8L))));
        assertTrue(draft.isVisibleTo(ContentScope.of(true, List.of(7L))));
    }

    @Test
    @DisplayName("阅读计数按人数累加")
    void markReadShouldIncrementCount() {
        PartyContent content = draft();
        content.setReadCount(null);
        content.markRead();
        assertEquals(1, content.getReadCount());
    }

    /**
     * 草稿样本。
     *
     * @return 草稿
     */
    private PartyContent draft() {
        PartyContent content = new PartyContent();
        content.setCategory("理论学习");
        content.setTitle("主题党日学习材料");
        content.setBody("正文内容");
        content.setAuthorId(9L);
        content.setAuthorName("张三");
        content.setVisibility(ContentVisibility.ALL_MEMBERS);
        content.setStatus(ContentStatus.DRAFT);
        return content;
    }

    /**
     * 待审样本。
     *
     * @return 待审内容
     */
    private PartyContent submitted() {
        PartyContent content = draft();
        content.validate();
        content.submit();
        return content;
    }
}
