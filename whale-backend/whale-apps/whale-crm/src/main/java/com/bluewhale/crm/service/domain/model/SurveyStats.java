package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 满意度统计。
 *
 * @author BlueWhale
 */
@Data
public class SurveyStats {

    /** 已发送问卷数 */
    private Long sentCount;

    /** 已答卷数 */
    private Long usedCount;

    /** 平均分（无答卷为 0） */
    private Double averageScore;

    /** 低分数 */
    private Long lowScoreCount;

    /** 需回访数 */
    private Long followUpCount;
}
