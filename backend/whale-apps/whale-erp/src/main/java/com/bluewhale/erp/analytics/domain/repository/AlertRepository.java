package com.bluewhale.erp.analytics.domain.repository;

import com.bluewhale.erp.analytics.domain.model.AlertEvent;
import com.bluewhale.erp.analytics.domain.model.AlertRule;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 预警规则与事件仓储。
 *
 * @author BlueWhale
 */
public interface AlertRepository {

    /**
     * 分页查询预警规则。
     *
     * @param query 查询条件
     * @return 规则列表
     */
    List<AlertRule> pageRules(AnalyticsQuery query);

    /**
     * 统计预警规则。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countRules(AnalyticsQuery query);

    /**
     * 查询全部启用规则。
     *
     * @return 启用规则
     */
    List<AlertRule> listEnabledRules();

    /**
     * 按规则编号查询。
     *
     * @param ruleNo 规则编号
     * @return 规则
     */
    Optional<AlertRule> findRuleByNo(String ruleNo);

    /**
     * 按主键查询规则。
     *
     * @param id 主键
     * @return 规则
     */
    Optional<AlertRule> findRuleById(Long id);

    /**
     * 新增规则。
     *
     * @param rule 规则
     */
    void insertRule(AlertRule rule);

    /**
     * 更新规则。
     *
     * @param rule 规则
     */
    void updateRule(AlertRule rule);

    /**
     * 分页查询预警事件。
     *
     * @param query 查询条件
     * @return 事件列表
     */
    List<AlertEvent> pageEvents(AnalyticsQuery query);

    /**
     * 统计预警事件。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countEvents(AnalyticsQuery query);

    /**
     * 统计未关闭预警事件数。
     *
     * @return 未关闭数
     */
    long countOpenEvents();

    /**
     * 按主键查询事件。
     *
     * @param id 主键
     * @return 事件
     */
    Optional<AlertEvent> findEventById(Long id);

    /**
     * 判断同规则同对象同周期是否已存在事件。
     *
     * @param ruleId 规则ID
     * @param objectKey 对象键
     * @param periodCode 周期编码
     * @return 已存在返回 true
     */
    boolean existsEvent(Long ruleId, String objectKey, String periodCode);

    /**
     * 生成事件编号。
     *
     * @param date 触发日期
     * @return 事件编号
     */
    String nextEventNo(LocalDate date);

    /**
     * 新增事件。
     *
     * @param event 事件
     */
    void insertEvent(AlertEvent event);

    /**
     * 更新事件。
     *
     * @param event 事件
     */
    void updateEvent(AlertEvent event);
}
