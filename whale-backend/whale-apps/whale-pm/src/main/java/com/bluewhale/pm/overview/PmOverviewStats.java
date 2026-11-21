package com.bluewhale.pm.overview;

import java.util.List;

/**
 * 营造坊（PM）的一次性统计快照。
 *
 * <p><b>为什么单独一组计数，而不是让概览 provider 直接查 Mapper。</b>
 * 概览 provider 属于 3D 城的取数层，一旦它开始自己写查询条件，
 * "未完成工作项"的判定口径就有两份（2D 看板按状态分类逐列看，
 * 3D 屏风自己数），迟早出现"后台说 12 件在办、城里说 8 件"。
 * 这里把所有口径收在 {@link PmOverviewStatsService} 一处，
 * provider 只负责排版。</p>
 *
 * <p><b>三个域各取各的账。</b>项目（pm_project）、迭代（pm_sprint）、
 * 工作项（pm_work_item）是 PM 的三本账，屏风上一张牌对应一本账；
 * 评论 / 附件 / 活动流水不单列——它们都挂在工作项下面，
 * 数量信息对"工程推进到哪了"没有增量。</p>
 *
 * @param projectActive   在建项目数（status = ACTIVE）
 * @param projectArchived 已归档项目数（status = ARCHIVED）
 * @param sprintActive    进行中的迭代数（status = ACTIVE）
 * @param sprintFuture    未启动的迭代数（status = FUTURE）
 * @param sprintClosed    已关闭的迭代数（status = CLOSED）
 * @param itemTotal       工作项总数
 * @param itemDone        已完成工作项数（状态分类 = DONE）
 * @param itemTodo        未开始工作项数（状态分类 = TODO）
 * @param itemInProgress  进行中工作项数（状态分类 = IN_PROGRESS）
 * @param itemOverdue     逾期未完成工作项数（dueDate 早于今天且未完成）
 * @param itemOverdueHigh 逾期未完成中的高优先级数（HIGHEST / HIGH）
 * @param itemUnassigned  无负责人的未完成工作项数（assigneeId 为空且未完成）
 * @param itemByType      未完成工作项按类型计数（史诗/需求/任务/缺陷/子任务）
 * @author BlueWhale
 */
public record PmOverviewStats(
        long projectActive,
        long projectArchived,
        long sprintActive,
        long sprintFuture,
        long sprintClosed,
        long itemTotal,
        long itemDone,
        long itemTodo,
        long itemInProgress,
        long itemOverdue,
        long itemOverdueHigh,
        long itemUnassigned,
        List<TypeCount> itemByType) {

    /**
     * 工作项类型计数：图表的横轴标签在服务端定死，屏风不再翻译码值。
     *
     * @param label 中文标签（史诗 / 需求 / 任务 / 缺陷 / 子任务）
     * @param count 未完成工作项数
     */
    public record TypeCount(String label, long count) {
    }
}
