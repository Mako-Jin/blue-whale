package com.bluewhale.party.union.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.union.domain.model.SignupStatus;
import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityStatus;
import com.bluewhale.party.union.domain.model.UnionSignup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工会活动领域规则：时间校验、状态单向、报名取消与再报名。
 *
 * @author BlueWhale
 */
class UnionActivityDomainRuleTest {

    @Test
    @DisplayName("结束时间必须晚于开始时间")
    void validateShouldCheckTimeRange() {
        UnionActivity sameTime = activity();
        sameTime.setEndTime(sameTime.getStartTime());
        assertInvalid(sameTime);

        UnionActivity reversed = activity();
        reversed.setEndTime(reversed.getStartTime().minusHours(1));
        assertInvalid(reversed);
    }

    @Test
    @DisplayName("名称必填、名额非负、地点超长被拒")
    void validateShouldCheckTextAndQuota() {
        UnionActivity blank = activity();
        blank.setTitle("  ");
        assertInvalid(blank);

        UnionActivity negativeQuota = activity();
        negativeQuota.setQuota(-1);
        assertInvalid(negativeQuota);

        UnionActivity longLocation = activity();
        longLocation.setLocation("x".repeat(256));
        assertInvalid(longLocation);
    }

    @Test
    @DisplayName("草稿默认状态且可编辑")
    void draftShouldBeEditable() {
        UnionActivity activity = activity();
        activity.setStatus(null);
        activity.validate();
        assertEquals(UnionActivityStatus.DRAFT, activity.getStatus());
        assertTrue(activity.isEditable());
    }

    @Test
    @DisplayName("发布仅草稿可执行，重复发布被拒")
    void publishShouldOnlyFromDraft() {
        UnionActivity activity = activity();
        activity.validate();
        activity.publish();
        assertEquals(UnionActivityStatus.PUBLISHED, activity.getStatus());

        BizException again = assertThrows(BizException.class, activity::publish);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, again.getCode());
    }

    @Test
    @DisplayName("结束仅已发布可执行，结束后终态不可取消")
    void closeShouldOnlyFromPublished() {
        UnionActivity draft = activity();
        draft.validate();
        BizException closeDraft = assertThrows(BizException.class, draft::close);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, closeDraft.getCode());

        UnionActivity published = activity();
        published.publish();
        published.close();
        assertEquals(UnionActivityStatus.CLOSED, published.getStatus());
        assertTrue(published.getStatus().isTerminal());

        BizException closeAgain = assertThrows(BizException.class, published::close);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, closeAgain.getCode());
        BizException cancelClosed = assertThrows(BizException.class, published::cancel);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, cancelClosed.getCode());
    }

    @Test
    @DisplayName("取消可从草稿或已发布执行，取消后终态")
    void cancelShouldFromDraftOrPublished() {
        UnionActivity draft = activity();
        draft.validate();
        draft.cancel();
        assertEquals(UnionActivityStatus.CANCELLED, draft.getStatus());
        assertTrue(draft.getStatus().isTerminal());

        UnionActivity published = activity();
        published.publish();
        published.cancel();
        assertEquals(UnionActivityStatus.CANCELLED, published.getStatus());

        BizException publishCancelled = assertThrows(BizException.class, published::publish);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, publishCancelled.getCode());
    }

    @Test
    @DisplayName("报名取消后再报名复用同一记录，取消时间被清空")
    void signupShouldCancelAndResign() {
        UnionSignup signup = signup();
        signup.cancel();
        assertEquals(SignupStatus.CANCELLED, signup.getStatus());
        assertNotNull(signup.getCancelledAt());

        BizException cancelAgain = assertThrows(BizException.class, signup::cancel);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, cancelAgain.getCode());

        signup.resign();
        assertEquals(SignupStatus.SIGNED, signup.getStatus());
        assertNotNull(signup.getSignupAt());
        assertNull(signup.getCancelledAt());

        BizException resignAgain = assertThrows(BizException.class, signup::resign);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, resignAgain.getCode());
    }

    /**
     * 断言字段非法。
     *
     * @param activity 活动
     */
    private void assertInvalid(UnionActivity activity) {
        BizException exception = assertThrows(BizException.class, activity::validate);
        assertEquals(PartyErrorCode.UNION_ACTIVITY_INVALID, exception.getCode());
    }

    /**
     * 构造草稿活动。
     *
     * @return 活动
     */
    private UnionActivity activity() {
        UnionActivity activity = new UnionActivity();
        activity.setActivityNo("AC20260001");
        activity.setTitle("秋季团建");
        activity.setActivityType("团建");
        activity.setStartTime(LocalDateTime.now().plusDays(2));
        activity.setEndTime(LocalDateTime.now().plusDays(3));
        activity.setLocation("园区会议室");
        activity.setQuota(30);
        activity.setRemark("自愿报名");
        activity.setStatus(UnionActivityStatus.DRAFT);
        return activity;
    }

    /**
     * 构造已报名记录。
     *
     * @return 报名记录
     */
    private UnionSignup signup() {
        UnionSignup signup = new UnionSignup();
        signup.setId(5L);
        signup.setActivityId(1L);
        signup.setUnionMemberId(11L);
        signup.setUserId(9L);
        signup.setStatus(SignupStatus.SIGNED);
        signup.setSignupAt(LocalDateTime.now().minusDays(1));
        return signup;
    }
}
