package com.bluewhale.party.analytics.application.service;

import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.common.PartyActor;

import java.time.LocalDate;
import java.util.List;

/**
 * 看板与统计应用服务（M-P4）。
 *
 * <p>所有口径都从各上下文的 {@code metrics()} 聚合而来，本服务不直接读别人的表：
 * 口径调整只需要改归属上下文的聚合方法，看板与导出自然跟随。</p>
 *
 * @author BlueWhale
 */
public interface AnalyticsAppService {

    /**
     * 党务看板总览（无快照时实时计算）。
     *
     * @param statDate 统计日（空表示当天）
     * @param branchId 支部 ID（空表示全租户；支部书记强制本支部）
     * @param actor 操作人
     * @return 总览
     */
    MetricOverviewView partyOverview(LocalDate statDate, Long branchId, PartyActor actor);

    /**
     * 刷新党务快照（全租户 + 每个在册支部），返回全租户指标。
     *
     * @param statDate 统计日（空表示当天）
     * @param actor 操作人
     * @return 全租户指标
     */
    List<MetricItemView> refreshParty(LocalDate statDate, PartyActor actor);

    /**
     * 党务数据脱敏导出（CSV 文本）。
     *
     * @param from 起始统计日
     * @param to 结束统计日
     * @param branchId 支部 ID（空表示全租户）
     * @param actor 操作人
     * @return CSV 文本
     */
    String exportPartyCsv(LocalDate from, LocalDate to, Long branchId, PartyActor actor);

    /**
     * 工会看板总览（无快照时实时计算）。
     *
     * @param statDate 统计日（空表示当天）
     * @param actor 操作人
     * @return 总览
     */
    MetricOverviewView unionOverview(LocalDate statDate, PartyActor actor);

    /**
     * 刷新工会快照。
     *
     * @param statDate 统计日（空表示当天）
     * @param actor 操作人
     * @return 指标
     */
    List<MetricItemView> refreshUnion(LocalDate statDate, PartyActor actor);

    /**
     * 工会数据脱敏导出（CSV 文本）。
     *
     * @param from 起始统计日
     * @param to 结束统计日
     * @param actor 操作人
     * @return CSV 文本
     */
    String exportUnionCsv(LocalDate from, LocalDate to, PartyActor actor);
}
