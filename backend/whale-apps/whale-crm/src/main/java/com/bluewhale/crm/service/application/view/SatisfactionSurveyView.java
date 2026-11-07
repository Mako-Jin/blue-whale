package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.SatisfactionResponse;
import com.bluewhale.crm.service.domain.model.SatisfactionSurvey;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满意度问卷视图。
 *
 * @author BlueWhale
 */
@Data
public class SatisfactionSurveyView {

    /** 主键 */
    private Long id;

    /** 问卷编号 */
    private String surveyNo;

    /** 工单 ID */
    private Long ticketId;

    /** 状态 */
    private String status;

    /** 生成 / 发送时间 */
    private LocalDateTime sentAt;

    /** 令牌有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 答卷提交时间 */
    private LocalDateTime usedAt;

    /** 一次性令牌（仅生成 / 重发时返回，后续查询为空） */
    private String token;

    /** 答卷（未提交为空） */
    private SatisfactionResponseView response;

    /**
     * 领域对象转视图。
     *
     * @param survey 问卷
     * @param response 答卷
     * @return 视图
     */
    public static SatisfactionSurveyView from(SatisfactionSurvey survey, SatisfactionResponse response) {
        SatisfactionSurveyView view = new SatisfactionSurveyView();
        view.setId(survey.getId());
        view.setSurveyNo(survey.getSurveyNo());
        view.setTicketId(survey.getTicketId());
        view.setStatus(survey.getStatus() == null ? null : survey.getStatus().name());
        view.setSentAt(survey.getSentAt());
        view.setExpiresAt(survey.getExpiresAt());
        view.setUsedAt(survey.getUsedAt());
        view.setResponse(response == null ? null : SatisfactionResponseView.from(response));
        return view;
    }
}
