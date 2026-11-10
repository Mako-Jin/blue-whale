package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.SatisfactionResponse;

import java.util.Collection;
import java.util.List;

/**
 * 满意度答卷仓储。
 *
 * @author BlueWhale
 */
public interface SatisfactionResponseRepository {

    /**
     * 新增答卷。
     *
     * @param response 答卷
     */
    void insert(SatisfactionResponse response);

    /**
     * 按问卷批量查询答卷。
     *
     * @param surveyIds 问卷 ID 集合
     * @return 答卷列表
     */
    List<SatisfactionResponse> findBySurveyIds(Collection<Long> surveyIds);

    /**
     * 答卷总数。
     *
     * @return 总数
     */
    long countAll();

    /**
     * 平均分。
     *
     * @return 平均分，无答卷返回 0
     */
    double averageScore();

    /**
     * 低分数。
     *
     * @return 低分数
     */
    long countLowScore();

    /**
     * 需回访数。
     *
     * @return 需回访数
     */
    long countFollowUp();
}
