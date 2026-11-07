package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.config.CrmSurveyProperties;
import com.bluewhale.crm.service.application.command.SurveyResponseCommand;
import com.bluewhale.crm.service.application.command.SurveySendCommand;
import com.bluewhale.crm.service.application.service.SatisfactionAppService;
import com.bluewhale.crm.service.application.view.SatisfactionSurveyView;
import com.bluewhale.crm.service.application.view.SurveyStatsView;
import com.bluewhale.crm.service.domain.model.SatisfactionResponse;
import com.bluewhale.crm.service.domain.model.SatisfactionSurvey;
import com.bluewhale.crm.service.domain.model.SurveyQuery;
import com.bluewhale.crm.service.domain.model.SurveyStats;
import com.bluewhale.crm.service.domain.model.SurveyStatus;
import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketAction;
import com.bluewhale.crm.service.domain.model.TicketActionType;
import com.bluewhale.crm.service.domain.model.TicketStatus;
import com.bluewhale.crm.service.domain.port.SurveyRateLimitPort;
import com.bluewhale.crm.service.domain.port.SurveyTokenPort;
import com.bluewhale.crm.service.domain.repository.SatisfactionResponseRepository;
import com.bluewhale.crm.service.domain.repository.SatisfactionSurveyRepository;
import com.bluewhale.crm.service.domain.repository.TicketActionRepository;
import com.bluewhale.crm.service.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 满意度问卷应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SatisfactionAppServiceImpl implements SatisfactionAppService {

    /** 问卷编号前缀 */
    private static final String SURVEY_NO_PREFIX = "CSAT-";

    /** 问卷编号日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 低分总分阈值（含） */
    private static final int LOW_SCORE_TOTAL = 3;

    /** 低分维度阈值（含） */
    private static final int LOW_SCORE_DIMENSION = 2;

    private final SatisfactionSurveyRepository surveyRepository;

    private final SatisfactionResponseRepository responseRepository;

    private final TicketRepository ticketRepository;

    private final TicketActionRepository ticketActionRepository;

    private final SurveyTokenPort tokenService;

    private final SurveyRateLimitPort submissionLimiter;

    private final CrmProperties properties;

    private final CrmServiceProperties serviceProperties;

    private final CrmSurveyProperties surveyProperties;

    @Override
    public PageResult<SatisfactionSurveyView> page(SurveyQuery query, CrmActor actor) {
        assertSurveyEnabled();
        actor.requireUserId();
        SurveyQuery safe = query == null ? new SurveyQuery() : query;
        List<SatisfactionSurvey> surveys = surveyRepository.page(safe);
        long total = surveyRepository.count(safe);
        Map<Long, SatisfactionResponse> responses = responsesOf(surveys);
        List<SatisfactionSurveyView> records = surveys.stream()
                .map(survey -> SatisfactionSurveyView.from(survey, responses.get(survey.getId())))
                .toList();
        return PageResult.of(records, total, safe.getCurrent(), safe.getSize());
    }

    @Override
    public SatisfactionSurveyView detail(Long id, CrmActor actor) {
        assertSurveyEnabled();
        actor.requireUserId();
        SatisfactionSurvey survey = requireSurvey(id);
        SatisfactionResponse response = responseRepository.findBySurveyIds(List.of(survey.getId())).stream()
                .findFirst().orElse(null);
        return SatisfactionSurveyView.from(survey, response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SatisfactionSurveyView send(SurveySendCommand command, CrmActor actor) {
        assertSurveyEnabled();
        actor.requireUserId();
        Ticket ticket = ticketRepository.findById(command.getTicketId());
        if (ticket == null) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_NOT_FOUND);
        }
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "仅已关闭工单允许发起满意度问卷");
        }
        LocalDateTime now = LocalDateTime.now();
        revokePending(ticket.getId(), now);
        Issued issued = issue(ticket.getId(), now);
        SatisfactionSurveyView view = SatisfactionSurveyView.from(issued.survey(), null);
        view.setToken(issued.token());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SatisfactionSurveyView submit(SurveyResponseCommand command, String clientIp) {
        assertSurveyEnabled();
        String surveyNo = tokenService.surveyNoOf(command.getToken());
        SatisfactionSurvey survey = surveyRepository.findBySurveyNo(surveyNo);
        if (survey == null || !tokenService.matches(surveyNo, command.getToken(), survey.getTokenHash())) {
            throw CrmErrorCode.exception(CrmErrorCode.SURVEY_TOKEN_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (survey.getStatus() != SurveyStatus.SENT || survey.isExpired(now)) {
            throw CrmErrorCode.exception(CrmErrorCode.SURVEY_TOKEN_INVALID);
        }
        if (!submissionLimiter.tryAcquire(clientIp, survey.getTicketId())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "提交过于频繁，请稍后再试");
        }
        boolean lowScore = isLowScore(command);
        SatisfactionResponse response = new SatisfactionResponse();
        response.setSurveyId(survey.getId());
        response.setTicketId(survey.getTicketId());
        response.setScore(command.getScore());
        response.setComment(trimToNull(command.getComment()));
        response.setSpeedScore(command.getSpeedScore());
        response.setAttitudeScore(command.getAttitudeScore());
        response.setProfessionalScore(command.getProfessionalScore());
        response.setLowScore(lowScore);
        response.setFollowUpRequired(lowScore);
        response.setSubmittedAt(now);
        responseRepository.insert(response);
        survey.markUsed(now);
        surveyRepository.update(survey);
        if (lowScore) {
            appendFollowUp(survey, response, now);
        }
        return SatisfactionSurveyView.from(survey, response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, CrmActor actor) {
        assertSurveyEnabled();
        actor.requireUserId();
        SatisfactionSurvey survey = requireSurvey(id);
        if (survey.getStatus() != SurveyStatus.SENT) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "仅待答卷问卷允许作废");
        }
        survey.revoke(LocalDateTime.now());
        surveyRepository.update(survey);
    }

    @Override
    public SurveyStatsView stats(CrmActor actor) {
        assertSurveyEnabled();
        actor.requireUserId();
        SurveyStats stats = new SurveyStats();
        stats.setSentCount(surveyRepository.count(new SurveyQuery()));
        stats.setUsedCount(responseRepository.countAll());
        stats.setAverageScore(responseRepository.averageScore());
        stats.setLowScoreCount(responseRepository.countLowScore());
        stats.setFollowUpCount(responseRepository.countFollowUp());
        return SurveyStatsView.from(stats);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SatisfactionSurveyView autoSend(Long ticketId) {
        if (!properties.isDemandGatePassed() || !serviceProperties.isEnabled() || !surveyProperties.isEnabled()) {
            return null;
        }
        try {
            Ticket ticket = ticketRepository.findById(ticketId);
            if (ticket == null || ticket.getStatus() != TicketStatus.CLOSED) {
                return null;
            }
            if (surveyRepository.findPendingByTicket(ticketId) != null) {
                return null;
            }
            Issued issued = issue(ticketId, LocalDateTime.now());
            return SatisfactionSurveyView.from(issued.survey(), null);
        } catch (RuntimeException exception) {
            log.warn("工单 {} 关闭后自动生成满意度问卷失败：{}", ticketId, exception.getMessage());
            return null;
        }
    }

    /**
     * 作废工单下待答卷问卷（重发前保证唯一有效令牌）。
     *
     * @param ticketId 工单 ID
     * @param now 当前时间
     */
    private void revokePending(Long ticketId, LocalDateTime now) {
        SatisfactionSurvey pending = surveyRepository.findPendingByTicket(ticketId);
        if (pending != null) {
            pending.revoke(now);
            surveyRepository.update(pending);
        }
    }

    /**
     * 签发问卷并落库。
     *
     * @param ticketId 工单 ID
     * @param now 签发时间
     * @return 签发结果（问卷与一次性令牌）
     */
    private Issued issue(Long ticketId, LocalDateTime now) {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        survey.setSurveyNo(nextSurveyNo());
        survey.setTicketId(ticketId);
        String token = tokenService.generate(survey.getSurveyNo());
        survey.setTokenHash(tokenService.hash(survey.getSurveyNo(), token));
        survey.issue(surveyProperties.getExpireDays(), now);
        surveyRepository.insert(survey);
        return new Issued(survey, token);
    }

    /**
     * 签发结果。
     *
     * @param survey 问卷
     * @param token 一次性令牌
     */
    private record Issued(SatisfactionSurvey survey, String token) {
    }

    /**
     * 低分时向原工单追加 FOLLOW_UP 动作。
     *
     * @param survey 问卷
     * @param response 答卷
     * @param now 当前时间
     */
    private void appendFollowUp(SatisfactionSurvey survey, SatisfactionResponse response, LocalDateTime now) {
        Ticket ticket = ticketRepository.findById(survey.getTicketId());
        if (ticket == null) {
            return;
        }
        TicketAction action = new TicketAction();
        action.setTicketId(ticket.getId());
        action.setActionType(TicketActionType.FOLLOW_UP);
        action.setFromStatus(ticket.getStatus());
        action.setToStatus(ticket.getStatus());
        action.setOperatorId(ticket.getAssigneeId() != null ? ticket.getAssigneeId() : ticket.getOwnerId());
        action.setOperatorName(ticket.getAssigneeName());
        action.setContent(followUpContent(response));
        action.setCreatedAt(now);
        ticketActionRepository.insert(action);
    }

    /**
     * 构造低分回访内容。
     *
     * @param response 答卷
     * @return 内容
     */
    private String followUpContent(SatisfactionResponse response) {
        String dimensions = "速度 " + scoreText(response.getSpeedScore())
                + " / 态度 " + scoreText(response.getAttitudeScore())
                + " / 专业 " + scoreText(response.getProfessionalScore());
        String content = "满意度低分回访：总分 " + response.getScore() + "（" + dimensions + "）";
        if (response.getComment() != null) {
            content = content + "；客户评论：" + response.getComment();
        }
        return content;
    }

    /**
     * 评分文本。
     *
     * @param score 评分
     * @return 文本
     */
    private String scoreText(Integer score) {
        return score == null ? "-" : String.valueOf(score);
    }

    /**
     * 判断是否低分（总分 ≤3 或任一维度 ≤2）。
     *
     * @param command 答卷命令
     * @return 是否低分
     */
    private boolean isLowScore(SurveyResponseCommand command) {
        boolean low = command.getScore() != null && command.getScore() <= LOW_SCORE_TOTAL;
        low = low || isLowDimension(command.getSpeedScore());
        low = low || isLowDimension(command.getAttitudeScore());
        low = low || isLowDimension(command.getProfessionalScore());
        return low;
    }

    /**
     * 判断单维度是否低分。
     *
     * @param score 维度评分
     * @return 是否低分
     */
    private boolean isLowDimension(Integer score) {
        return score != null && score <= LOW_SCORE_DIMENSION;
    }

    /**
     * 批量查询答卷。
     *
     * @param surveys 问卷列表
     * @return 问卷 ID 到答卷的映射
     */
    private Map<Long, SatisfactionResponse> responsesOf(List<SatisfactionSurvey> surveys) {
        List<Long> ids = surveys.stream().map(SatisfactionSurvey::getId).toList();
        return responseRepository.findBySurveyIds(ids).stream()
                .collect(Collectors.toMap(SatisfactionResponse::getSurveyId, Function.identity(), (left, right) -> left));
    }

    /**
     * 读取问卷，不存在按参数非法处理。
     *
     * @param id 主键
     * @return 问卷
     */
    private SatisfactionSurvey requireSurvey(Long id) {
        SatisfactionSurvey survey = surveyRepository.findById(id);
        if (survey == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "满意度问卷不存在");
        }
        return survey;
    }

    /**
     * 生成问卷编号 CSAT-yyyyMMdd-xxxx。
     *
     * @return 问卷编号
     */
    private String nextSurveyNo() {
        String prefix = SURVEY_NO_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        long sequence = surveyRepository.countByPrefix(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    /**
     * 去空白，空串转为 null。
     *
     * @param value 原始值
     * @return 文本或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 服务门禁校验。
     */
    private void assertServiceGate() {
        if (!properties.isDemandGatePassed() || !serviceProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 满意度模块开关校验。
     */
    private void assertSurveyEnabled() {
        assertServiceGate();
        if (!surveyProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED, "满意度问卷模块未启用");
        }
    }
}
