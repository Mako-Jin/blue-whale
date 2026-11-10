package com.bluewhale.party.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import com.bluewhale.party.integration.domain.gateway.NotifyGateway;
import com.bluewhale.party.integration.domain.model.NotifyLog;
import com.bluewhale.party.integration.domain.model.NotifyRequest;
import com.bluewhale.party.integration.domain.model.NotifyScene;
import com.bluewhale.party.integration.domain.model.NotifyStatus;
import com.bluewhale.party.integration.domain.repository.NotifyLogRepository;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.ProbationCandidateView;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.view.UnionActivityNoticeView;
import com.bluewhale.party.union.application.view.UnionNoticeTargetView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知投递应用服务：幂等重发、失败留痕、场景容错。
 *
 * @author BlueWhale
 */
class NotifyAppServiceImplTest {

    private NotifyLogRepository notifyLogRepository;

    private NotifyGateway notifyGateway;

    private MemberAppService memberAppService;

    private FeeAppService feeAppService;

    private ActivityAppService activityAppService;

    private BranchAppService branchAppService;

    private UnionActivityAppService unionActivityAppService;

    private UnionMemberAppService unionMemberAppService;

    private NotifyAppServiceImpl service;

    private final Map<String, NotifyLog> store = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        notifyLogRepository = mock(NotifyLogRepository.class);
        notifyGateway = mock(NotifyGateway.class);
        memberAppService = mock(MemberAppService.class);
        feeAppService = mock(FeeAppService.class);
        activityAppService = mock(ActivityAppService.class);
        branchAppService = mock(BranchAppService.class);
        unionActivityAppService = mock(UnionActivityAppService.class);
        unionMemberAppService = mock(UnionMemberAppService.class);
        service = new NotifyAppServiceImpl(notifyLogRepository, notifyGateway, memberAppService,
                feeAppService, activityAppService, branchAppService, unionActivityAppService,
                unionMemberAppService, new PartyProperties());
        when(notifyLogRepository.findByKeyAndUser(anyString(), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        store.get(invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        when(notifyLogRepository.save(any(NotifyLog.class))).thenAnswer(invocation -> {
            NotifyLog saved = invocation.getArgument(0);
            store.put(saved.getIdempotencyKey() + ":" + saved.getUserId(), saved);
            return 1L;
        });
        when(feeAppService.outstandingBills(anyString())).thenReturn(List.of());
        when(activityAppService.upcoming(any())).thenReturn(List.of());
        when(branchAppService.termDue(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("重复扫描不重复发送：第二次均为 0")
    void repeatedScanShouldNotSendTwice() {
        when(memberAppService.probationCandidates(any())).thenReturn(List.of(probationCandidate()));

        NoticeScanResultView first = service.scanParty();
        NoticeScanResultView second = service.scanParty();

        assertEquals(2, first.getTotalSent());
        assertEquals(0, second.getTotalSent());
        verify(notifyGateway, times(2)).send(any(NotifyRequest.class));
    }

    @Test
    @DisplayName("投递失败记为 FAILED，扫描不抛错且下一轮可重试")
    void failureShouldBeMarkedAndRetriedNextScan() {
        when(memberAppService.probationCandidates(any())).thenReturn(List.of(probationCandidate()));
        doThrow(new IllegalStateException("消息服务不可用"))
                .when(notifyGateway).send(any(NotifyRequest.class));

        NoticeScanResultView failed = service.scanParty();

        assertEquals(0, failed.getTotalSent());
        assertEquals(NotifyStatus.FAILED, store.values().iterator().next().getSendStatus());
    }

    @Test
    @DisplayName("单条投递全部失败时抛 NOTICE_SEND_FAILED")
    void sendShouldFailWhenAllRecipientsFail() {
        doThrow(new IllegalStateException("消息服务不可用"))
                .when(notifyGateway).send(any(NotifyRequest.class));

        BizException exception = assertThrows(BizException.class,
                () -> service.send(NotifyScene.PARTY_FEE, "PARTY_FEE:1", "FEE_BILL", 1L,
                        List.of(101L), "党费提醒", "请缴费"));
        assertEquals(PartyErrorCode.NOTICE_SEND_FAILED, exception.getCode());
    }

    @Test
    @DisplayName("已成功投递的幂等键不再发送")
    void alreadySentShouldBeSkipped() {
        NotifyLog sent = new NotifyLog();
        sent.setScene(NotifyScene.PARTY_FEE);
        sent.setIdempotencyKey("PARTY_FEE:1");
        sent.setUserId(101L);
        sent.setSendStatus(NotifyStatus.SENT);
        store.put("PARTY_FEE:1:101", sent);

        int count = service.send(NotifyScene.PARTY_FEE, "PARTY_FEE:1", "FEE_BILL", 1L,
                List.of(101L), "党费提醒", "请缴费");

        assertEquals(0, count);
        verify(notifyGateway, never()).send(any(NotifyRequest.class));
    }

    @Test
    @DisplayName("无接收人的提案反馈直接跳过")
    void proposalFeedbackWithoutUserShouldSkip() {
        assertEquals(0, service.notifyProposalFeedback(1L, null, "PP20260001", "已办理"));
        verify(notifyGateway, never()).send(any(NotifyRequest.class));
    }

    @Test
    @DisplayName("工会活动通知按在册会员投递")
    void scanUnionShouldNotifyUnionMembers() {
        when(unionMemberAppService.noticeTargets()).thenReturn(List.of(unionTarget(201L), unionTarget(202L)));
        when(unionActivityAppService.upcoming(any())).thenReturn(List.of(unionActivity()));

        NoticeScanResultView result = service.scanUnion();

        assertEquals(2, result.getTotalSent());
        verify(notifyGateway, times(2)).send(any(NotifyRequest.class));
    }

    /**
     * 构造转正候选（党员 + 书记两个接收人）。
     *
     * @return 候选
     */
    private ProbationCandidateView probationCandidate() {
        ProbationCandidateView view = new ProbationCandidateView();
        view.setMemberId(1L);
        view.setMemberName("张三");
        view.setUserId(101L);
        view.setSecretaryUserId(102L);
        view.setBranchId(1L);
        view.setBranchName("第一党支部");
        view.setJoinPartyDate(LocalDate.of(2025, 11, 20));
        view.setDueDate(LocalDate.of(2026, 11, 20));
        return view;
    }

    /**
     * 构造工会会员接收人。
     *
     * @param userId 用户 ID
     * @return 接收人
     */
    private UnionNoticeTargetView unionTarget(Long userId) {
        UnionNoticeTargetView view = new UnionNoticeTargetView();
        view.setMemberId(userId);
        view.setMemberName("会员" + userId);
        view.setUserId(userId);
        return view;
    }

    /**
     * 构造工会活动通知。
     *
     * @return 通知
     */
    private UnionActivityNoticeView unionActivity() {
        UnionActivityNoticeView view = new UnionActivityNoticeView();
        view.setActivityId(1L);
        view.setTitle("秋季运动会");
        view.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        view.setLocation("公司体育场");
        return view;
    }
}
