package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.config.CrmSurveyProperties;
import com.bluewhale.crm.service.application.command.SurveyResponseCommand;
import com.bluewhale.crm.service.application.command.SurveySendCommand;
import com.bluewhale.crm.service.application.view.SatisfactionSurveyView;
import com.bluewhale.crm.service.application.view.SurveyStatsView;
import com.bluewhale.crm.service.domain.model.SatisfactionResponse;
import com.bluewhale.crm.service.domain.model.SatisfactionSurvey;
import com.bluewhale.crm.service.domain.model.SurveyStatus;
import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketAction;
import com.bluewhale.crm.service.domain.model.TicketActionType;
import com.bluewhale.crm.service.domain.model.TicketPriority;
import com.bluewhale.crm.service.domain.model.TicketStatus;
import com.bluewhale.crm.service.domain.port.SurveyRateLimitPort;
import com.bluewhale.crm.service.domain.port.SurveyTokenPort;
import com.bluewhale.crm.service.domain.repository.SatisfactionResponseRepository;
import com.bluewhale.crm.service.domain.repository.SatisfactionSurveyRepository;
import com.bluewhale.crm.service.domain.repository.TicketActionRepository;
import com.bluewhale.crm.service.domain.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SatisfactionAppServiceImpl} 应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SatisfactionAppServiceImplTest {

    @Mock
    private SatisfactionSurveyRepository surveyRepository;

    @Mock
    private SatisfactionResponseRepository responseRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketActionRepository ticketActionRepository;

    @Mock
    private SurveyTokenPort tokenService;

    @Mock
    private SurveyRateLimitPort submissionLimiter;

    private CrmSurveyProperties surveyProperties;

    private SatisfactionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        surveyProperties = new CrmSurveyProperties();
        surveyProperties.setEnabled(true);
        service = new SatisfactionAppServiceImpl(surveyRepository, responseRepository, ticketRepository,
                ticketActionRepository, tokenService, submissionLimiter, new CrmProperties(),
                new CrmServiceProperties(), surveyProperties);
    }

    @Test
    @DisplayName("生成问卷：已关闭工单签发一次性令牌并返回")
    void shouldSendSurveyForClosedTicket() {
        when(ticketRepository.findById(5L)).thenReturn(ticket(TicketStatus.CLOSED));
        when(surveyRepository.findPendingByTicket(5L)).thenReturn(null);
        when(surveyRepository.countByPrefix(anyString())).thenReturn(0L);
        when(tokenService.generate(anyString())).thenReturn("CSAT-20261107-0001.nonce");
        when(tokenService.hash(anyString(), eq("CSAT-20261107-0001.nonce"))).thenReturn("hash");
        doAnswer(invocation -> {
            SatisfactionSurvey survey = invocation.getArgument(0);
            survey.setId(8L);
            return null;
        }).when(surveyRepository).insert(any(SatisfactionSurvey.class));
        SurveySendCommand command = new SurveySendCommand();
        command.setTicketId(5L);

        SatisfactionSurveyView view = service.send(command, adminActor());

        assertEquals(8L, view.getId());
        assertTrue(view.getSurveyNo().endsWith("-0001"));
        assertEquals("SENT", view.getStatus());
        assertEquals("CSAT-20261107-0001.nonce", view.getToken());
        verify(surveyRepository).insert(any(SatisfactionSurvey.class));
    }

    @Test
    @DisplayName("生成问卷：未关闭工单拒绝生成")
    void shouldRejectSendForOpenTicket() {
        when(ticketRepository.findById(5L)).thenReturn(ticket(TicketStatus.PROCESSING));
        SurveySendCommand command = new SurveySendCommand();
        command.setTicketId(5L);

        BizException exception = assertThrows(BizException.class, () -> service.send(command, adminActor()));

        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, exception.getCode());
        verify(surveyRepository, never()).insert(any(SatisfactionSurvey.class));
    }

    @Test
    @DisplayName("提交答卷：问卷不存在或令牌不匹配按令牌无效处理")
    void shouldRejectUnknownSurvey() {
        when(tokenService.surveyNoOf("bad-token")).thenReturn("CSAT-20261107-0001");
        when(surveyRepository.findBySurveyNo("CSAT-20261107-0001")).thenReturn(null);
        SurveyResponseCommand command = new SurveyResponseCommand();
        command.setToken("bad-token");
        command.setScore(5);

        BizException exception = assertThrows(BizException.class, () -> service.submit(command, "10.0.0.1"));

        assertEquals(CrmErrorCode.SURVEY_TOKEN_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("提交答卷：已过期问卷按令牌无效处理")
    void shouldRejectExpiredSurvey() {
        SatisfactionSurvey survey = survey(SurveyStatus.SENT, LocalDateTime.now().minusMinutes(1));
        when(tokenService.surveyNoOf("token")).thenReturn(survey.getSurveyNo());
        when(surveyRepository.findBySurveyNo(survey.getSurveyNo())).thenReturn(survey);
        when(tokenService.matches(survey.getSurveyNo(), "token", survey.getTokenHash())).thenReturn(true);
        SurveyResponseCommand command = new SurveyResponseCommand();
        command.setToken("token");
        command.setScore(5);

        BizException exception = assertThrows(BizException.class, () -> service.submit(command, "10.0.0.1"));

        assertEquals(CrmErrorCode.SURVEY_TOKEN_INVALID, exception.getCode());
        verify(responseRepository, never()).insert(any(SatisfactionResponse.class));
    }

    @Test
    @DisplayName("提交答卷：低分写回访标记并向工单追加 FOLLOW_UP 动作")
    void shouldFlagLowScoreAndAppendFollowUp() {
        SatisfactionSurvey survey = survey(SurveyStatus.SENT, LocalDateTime.now().plusDays(7));
        when(tokenService.surveyNoOf("token")).thenReturn(survey.getSurveyNo());
        when(surveyRepository.findBySurveyNo(survey.getSurveyNo())).thenReturn(survey);
        when(tokenService.matches(survey.getSurveyNo(), "token", survey.getTokenHash())).thenReturn(true);
        when(submissionLimiter.tryAcquire("10.0.0.1", 5L)).thenReturn(true);
        Ticket ticket = ticket(TicketStatus.CLOSED);
        ticket.setAssigneeId(100L);
        ticket.setAssigneeName("张三");
        when(ticketRepository.findById(5L)).thenReturn(ticket);
        SurveyResponseCommand command = new SurveyResponseCommand();
        command.setToken("token");
        command.setScore(2);
        command.setSpeedScore(4);
        command.setAttitudeScore(3);
        command.setProfessionalScore(5);
        command.setComment(" 响应太慢 ");

        SatisfactionSurveyView view = service.submit(command, "10.0.0.1");

        assertTrue(view.getResponse().getLowScore());
        assertTrue(view.getResponse().getFollowUpRequired());
        assertEquals("响应太慢", view.getResponse().getComment());
        assertEquals("USED", view.getStatus());
        ArgumentCaptor<SatisfactionResponse> responseCaptor = ArgumentCaptor.forClass(SatisfactionResponse.class);
        verify(responseRepository).insert(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().getLowScore());
        verify(surveyRepository).update(survey);
        ArgumentCaptor<TicketAction> actionCaptor = ArgumentCaptor.forClass(TicketAction.class);
        verify(ticketActionRepository).insert(actionCaptor.capture());
        assertEquals(TicketActionType.FOLLOW_UP, actionCaptor.getValue().getActionType());
        assertEquals(100L, actionCaptor.getValue().getOperatorId());
    }

    @Test
    @DisplayName("提交答卷：高分不追加回访动作")
    void shouldNotAppendFollowUpForHighScore() {
        SatisfactionSurvey survey = survey(SurveyStatus.SENT, LocalDateTime.now().plusDays(7));
        when(tokenService.surveyNoOf("token")).thenReturn(survey.getSurveyNo());
        when(surveyRepository.findBySurveyNo(survey.getSurveyNo())).thenReturn(survey);
        when(tokenService.matches(survey.getSurveyNo(), "token", survey.getTokenHash())).thenReturn(true);
        when(submissionLimiter.tryAcquire("10.0.0.1", 5L)).thenReturn(true);
        SurveyResponseCommand command = new SurveyResponseCommand();
        command.setToken("token");
        command.setScore(5);
        command.setSpeedScore(4);
        command.setAttitudeScore(5);
        command.setProfessionalScore(5);

        SatisfactionSurveyView view = service.submit(command, "10.0.0.1");

        assertEquals(Boolean.FALSE, view.getResponse().getLowScore());
        assertEquals(Boolean.FALSE, view.getResponse().getFollowUpRequired());
        verify(ticketActionRepository, never()).insert(any(TicketAction.class));
    }

    @Test
    @DisplayName("统计：汇总发送量、答卷量与低分回访")
    void shouldAggregateStats() {
        when(surveyRepository.count(any())).thenReturn(10L);
        when(responseRepository.countAll()).thenReturn(6L);
        when(responseRepository.averageScore()).thenReturn(4.2D);
        when(responseRepository.countLowScore()).thenReturn(1L);
        when(responseRepository.countFollowUp()).thenReturn(1L);

        SurveyStatsView view = service.stats(adminActor());

        assertEquals(10L, view.getSentCount());
        assertEquals(6L, view.getUsedCount());
        assertEquals(4.2D, view.getAverageScore());
        assertEquals(1L, view.getLowScoreCount());
        assertEquals(1L, view.getFollowUpCount());
    }

    @Test
    @DisplayName("自动生成：满意度开关关闭时静默跳过")
    void shouldSkipAutoSendWhenDisabled() {
        surveyProperties.setEnabled(false);

        SatisfactionSurveyView view = service.autoSend(5L);

        assertNull(view);
        verifyNoInteractions(ticketRepository, surveyRepository);
    }

    /**
     * 构造管理员操作人。
     *
     * @return 操作人
     */
    private static CrmActor adminActor() {
        return new CrmActor(1L, "admin", Set.of("crm_admin"), Set.of());
    }

    /**
     * 构造工单。
     *
     * @param status 状态
     * @return 工单
     */
    private static Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        ticket.setTicketNo("TICKET-20261107-0001");
        ticket.setPriority(TicketPriority.P2);
        ticket.setTitle("设备离线");
        ticket.setStatus(status);
        ticket.setOwnerId(1L);
        return ticket;
    }

    /**
     * 构造问卷。
     *
     * @param status 状态
     * @param expiresAt 有效期截止时间
     * @return 问卷
     */
    private static SatisfactionSurvey survey(SurveyStatus status, LocalDateTime expiresAt) {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        survey.setId(8L);
        survey.setSurveyNo("CSAT-20261107-0001");
        survey.setTicketId(5L);
        survey.setTokenHash("hash");
        survey.setStatus(status);
        survey.setSentAt(expiresAt.minusDays(7));
        survey.setExpiresAt(expiresAt);
        return survey;
    }
}
