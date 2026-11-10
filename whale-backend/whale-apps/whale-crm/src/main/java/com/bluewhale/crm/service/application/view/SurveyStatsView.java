package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.SurveyStats;
import lombok.Data;

/**
 * 满意度统计视图。
 *
 * @author BlueWhale
 */
@Data
public class SurveyStatsView {

    /** 已发送问卷数 */
    private Long sentCount;

    /** 已答卷数 */
    private Long usedCount;

    /** 平均分 */
    private Double averageScore;

    /** 低分数 */
    private Long lowScoreCount;

    /** 需回访数 */
    private Long followUpCount;

    /**
     * 领域对象转视图。
     *
     * @param stats 统计
     * @return 视图
     */
    public static SurveyStatsView from(SurveyStats stats) {
        SurveyStatsView view = new SurveyStatsView();
        view.setSentCount(stats.getSentCount());
        view.setUsedCount(stats.getUsedCount());
        view.setAverageScore(stats.getAverageScore());
        view.setLowScoreCount(stats.getLowScoreCount());
        view.setFollowUpCount(stats.getFollowUpCount());
        return view;
    }
}
