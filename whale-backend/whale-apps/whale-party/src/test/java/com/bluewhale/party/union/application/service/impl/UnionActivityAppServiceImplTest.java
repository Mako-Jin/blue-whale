package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.view.UnionSignupView;
import com.bluewhale.party.union.domain.model.SignupStatus;
import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityStatus;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import com.bluewhale.party.union.domain.model.UnionSignup;
import com.bluewhale.party.union.domain.repository.UnionActivityRepository;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工会活动应用服务：名额、重复报名、取消后重报、发布状态与园地可见性。
 *
 * @author BlueWhale
 */
class UnionActivityAppServiceImplTest {

    private UnionActivityRepository unionActivityRepository;

    private UnionMemberRepository unionMemberRepository;

    private UnionActivityAppServiceImpl service;

    private PartyActor memberActor;

    @BeforeEach
    void setUp() {
        unionActivityRepository = mock(UnionActivityRepository.class);
        unionMemberRepository = mock(UnionMemberRepository.class);
        service = new UnionActivityAppServiceImpl(unionActivityRepository, unionMemberRepository,
                mock(PartySequenceService.class), new PartyProperties());
        memberActor = new PartyActor(20L, "zhangsan", Set.of("employee"), Set.of());
    }

    @Test
    @DisplayName("名额已满时报名被拒")
    void signupShouldRejectWhenQuotaExceeded() {
        UnionActivity activity = activity(UnionActivityStatus.PUBLISHED, 2);
        stubActivity(activity);
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(11L, "张三")));
        when(unionActivityRepository.findSignup(1L, 11L)).thenReturn(Optional.empty());
        when(unionActivityRepository.countSigned(1L)).thenReturn(2L);

        BizException exception = assertThrows(BizException.class,
                () -> service.signup(1L, memberActor));
        assertEquals(PartyErrorCode.UNION_SIGNUP_QUOTA_EXCEEDED, exception.getCode());
        verify(unionActivityRepository, never()).saveSignup(any(UnionSignup.class));
    }

    @Test
    @DisplayName("已报名时重复报名被拒")
    void signupShouldRejectDuplicate() {
        UnionActivity activity = activity(UnionActivityStatus.PUBLISHED, 0);
        stubActivity(activity);
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(11L, "张三")));
        UnionSignup signed = signup(SignupStatus.SIGNED);
        when(unionActivityRepository.findSignup(1L, 11L)).thenReturn(Optional.of(signed));

        BizException exception = assertThrows(BizException.class,
                () -> service.signup(1L, memberActor));
        assertEquals(PartyErrorCode.UNION_SIGNUP_DUPLICATED, exception.getCode());
        verify(unionActivityRepository, never()).saveSignup(any(UnionSignup.class));
    }

    @Test
    @DisplayName("取消后再报名复用原记录，不新增行")
    void signupShouldReuseCancelledRecord() {
        UnionActivity activity = activity(UnionActivityStatus.PUBLISHED, 0);
        stubActivity(activity);
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(11L, "张三")));
        UnionSignup cancelled = signup(SignupStatus.CANCELLED);
        cancelled.setCancelledAt(LocalDateTime.now().minusDays(1));
        when(unionActivityRepository.findSignup(1L, 11L)).thenReturn(Optional.of(cancelled));
        when(unionActivityRepository.saveSignup(any(UnionSignup.class)))
                .thenAnswer(invocation -> {
                    UnionSignup saved = invocation.getArgument(0);
                    saved.setId(5L);
                    return 5L;
                });

        UnionSignupView view = service.signup(1L, memberActor);

        assertEquals(SignupStatus.SIGNED.name(), view.getStatus());
        assertEquals(5L, view.getId());
        assertNull(view.getCancelledAt());
        ArgumentCaptor<UnionSignup> captor = ArgumentCaptor.forClass(UnionSignup.class);
        verify(unionActivityRepository).saveSignup(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(SignupStatus.SIGNED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("未发布活动不可报名")
    void signupShouldRejectUnpublishedActivity() {
        UnionActivity activity = activity(UnionActivityStatus.DRAFT, 0);
        stubActivity(activity);

        BizException exception = assertThrows(BizException.class,
                () -> service.signup(1L, memberActor));
        assertEquals(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT, exception.getCode());
        verify(unionMemberRepository, never()).findActiveByUserId(20L);
    }

    @Test
    @DisplayName("园地详情对普通用户隐藏未发布活动")
    void portalDetailShouldHideUnpublishedActivity() {
        UnionActivity activity = activity(UnionActivityStatus.DRAFT, 0);
        stubActivity(activity);

        BizException exception = assertThrows(BizException.class,
                () -> service.portalDetail(1L, memberActor));
        assertEquals(PartyErrorCode.UNION_ACTIVITY_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("取消报名后状态变为已取消")
    void cancelSignupShouldMarkCancelled() {
        UnionActivity activity = activity(UnionActivityStatus.PUBLISHED, 0);
        stubActivity(activity);
        UnionSignup signed = signup(SignupStatus.SIGNED);
        when(unionMemberRepository.findActiveByUserId(20L))
                .thenReturn(Optional.of(member(11L, "张三")));
        when(unionActivityRepository.findSignup(1L, 11L)).thenReturn(Optional.of(signed));

        UnionSignupView view = service.cancelSignup(1L, memberActor);

        assertEquals(SignupStatus.CANCELLED.name(), view.getStatus());
        verify(unionActivityRepository).saveSignup(any(UnionSignup.class));
    }

    /**
     * 桩定活动查询。
     *
     * @param activity 活动
     */
    private void stubActivity(UnionActivity activity) {
        when(unionActivityRepository.findById(1L)).thenReturn(Optional.of(activity));
    }

    /**
     * 构造活动样本。
     *
     * @param status 状态
     * @param quota 名额
     * @return 活动
     */
    private UnionActivity activity(UnionActivityStatus status, int quota) {
        UnionActivity activity = new UnionActivity();
        activity.setId(1L);
        activity.setActivityNo("AC20260001");
        activity.setTitle("秋季团建");
        activity.setActivityType("团建");
        activity.setStartTime(LocalDateTime.now().plusDays(2));
        activity.setEndTime(LocalDateTime.now().plusDays(3));
        activity.setLocation("园区会议室");
        activity.setQuota(quota);
        activity.setStatus(status);
        return activity;
    }

    /**
     * 构造报名记录。
     *
     * @param status 状态
     * @return 报名记录
     */
    private UnionSignup signup(SignupStatus status) {
        UnionSignup signup = new UnionSignup();
        signup.setId(5L);
        signup.setActivityId(1L);
        signup.setUnionMemberId(11L);
        signup.setUserId(20L);
        signup.setStatus(status);
        signup.setSignupAt(LocalDateTime.now().minusDays(1));
        return signup;
    }

    /**
     * 构造在册会员。
     *
     * @param id 会员 ID
     * @param name 姓名
     * @return 会员
     */
    private UnionMember member(Long id, String name) {
        UnionMember member = new UnionMember();
        member.setId(id);
        member.setEmployeeNo("E001");
        member.setMemberName(name);
        member.setStatus(UnionMemberStatus.ACTIVE);
        return member;
    }
}
