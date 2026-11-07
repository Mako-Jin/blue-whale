package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.SatisfactionSurvey;
import com.bluewhale.crm.service.domain.model.SurveyQuery;

import java.util.List;

/**
 * 满意度问卷仓储。
 *
 * @author BlueWhale
 */
public interface SatisfactionSurveyRepository {

    /**
     * 分页查询问卷。
     *
     * @param query 查询条件
     * @return 当前页数据
     */
    List<SatisfactionSurvey> page(SurveyQuery query);

    /**
     * 统计问卷总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SurveyQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 问卷
     */
    SatisfactionSurvey findById(Long id);

    /**
     * 按问卷编号查询。
     *
     * @param surveyNo 问卷编号
     * @return 问卷
     */
    SatisfactionSurvey findBySurveyNo(String surveyNo);

    /**
     * 查询工单最近一条未答卷问卷。
     *
     * @param ticketId 工单 ID
     * @return 问卷
     */
    SatisfactionSurvey findPendingByTicket(Long ticketId);

    /**
     * 新增问卷。
     *
     * @param survey 问卷
     */
    void insert(SatisfactionSurvey survey);

    /**
     * 更新问卷。
     *
     * @param survey 问卷
     */
    void update(SatisfactionSurvey survey);

    /**
     * 统计指定前缀的问卷编号数量。
     *
     * @param prefix 问卷编号前缀
     * @return 数量
     */
    long countByPrefix(String prefix);
}
