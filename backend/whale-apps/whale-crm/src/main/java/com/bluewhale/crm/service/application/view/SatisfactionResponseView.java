package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.SatisfactionResponse;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满意度答卷视图。
 *
 * @author BlueWhale
 */
@Data
public class SatisfactionResponseView {

    /** 总分 */
    private Integer score;

    /** 客户评论 */
    private String comment;

    /** 响应速度评分 */
    private Integer speedScore;

    /** 服务态度评分 */
    private Integer attitudeScore;

    /** 专业程度评分 */
    private Integer professionalScore;

    /** 是否低分 */
    private Boolean lowScore;

    /** 是否需回访 */
    private Boolean followUpRequired;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /**
     * 领域对象转视图。
     *
     * @param response 答卷
     * @return 视图
     */
    public static SatisfactionResponseView from(SatisfactionResponse response) {
        SatisfactionResponseView view = new SatisfactionResponseView();
        view.setScore(response.getScore());
        view.setComment(response.getComment());
        view.setSpeedScore(response.getSpeedScore());
        view.setAttitudeScore(response.getAttitudeScore());
        view.setProfessionalScore(response.getProfessionalScore());
        view.setLowScore(Boolean.TRUE.equals(response.getLowScore()));
        view.setFollowUpRequired(Boolean.TRUE.equals(response.getFollowUpRequired()));
        view.setSubmittedAt(response.getSubmittedAt());
        return view;
    }
}
