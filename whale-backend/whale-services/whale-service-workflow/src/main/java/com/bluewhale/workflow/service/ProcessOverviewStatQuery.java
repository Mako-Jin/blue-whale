package com.bluewhale.workflow.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 政事殿概览的统计口径（T5.6）。
 *
 * <p><b>为什么单独开一个端口，而不是在 provider 里直接写 Mapper：</b>概览要在 5 秒内
 * 回答六个问题（运行中 / 今日发起 / 今日办结 / 待办 / 超时 / 卡住），而 2D 的实例页
 * 是把整页 <em>PO</em> 捞回内存再算。屏风每 30 秒轮询一次，拿列表接口当数据源等于
 * 每分钟拉几千行 PO。这里只做 count 型聚合，一条 SQL 出一个数。</p>
 *
 * <p><b>口径必须只有一处。</b>"今日办结几次"若屏风一套、2D 报表另一套，迟早出现
 * "城里说 12、后台说 20"。所以"办结只算 {@code status = 1} 且看 {@code finished_at}"
 * 这样的定义写进实现，并由单测钉住。</p>
 *
 * <p><b>租户显式传参。</b>数枢殿总控在聚合线程里调用各殿概览，线程上下文挂的是
 * "调用者"身份；靠 {@code TenantContext} 取租户等于把别人的数算进这一屏。
 * 显式传参让这种漏数在编译期就写不出来。</p>
 *
 * @author BlueWhale
 */
public interface ProcessOverviewStatQuery {

    /**
     * 实例的三个即时数
     *
     * @param running      运行中的实例数
     * @param startedToday 今日发起的实例数（不限状态）
     * @param finishedToday 今日办结（审批通过）的实例数
     */
    record InstanceStat(long running, long startedToday, long finishedToday) {
    }

    /**
     * 按天聚合的发起与办结
     *
     * @param day      日期
     * @param started  当日发起数
     * @param finished 当日办结数
     */
    record DayFlow(LocalDate day, long started, long finished) {
    }

    /**
     * 待办积压
     *
     * @param nodeName 审批节点名（为空的历史行折成"未命名节点"）
     * @param pending  该节点的待办数
     */
    record Backlog(String nodeName, long pending) {
    }

    /**
     * 超时待办的规模与最坏情况
     *
     * @param overdue      已超时的待办数
     * @param worstMinutes 最久超时多少分钟；无超时时为 0
     */
    record OverdueStat(long overdue, long worstMinutes) {
    }

    /**
     * 运行中 / 今日发起 / 今日办结
     *
     * @param tenantId 租户 ID
     * @param from     当日窗口起点（含）
     * @param to       当日窗口终点（不含）
     * @return 统计；参数缺失时返回零值
     */
    InstanceStat instanceStat(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 待办任务总数
     *
     * @param tenantId 租户 ID
     * @return 待办数；参数缺失时返回 0
     */
    long pendingCount(Long tenantId);

    /**
     * 超时待办的规模与最坏情况
     *
     * @param tenantId 租户 ID
     * @param now      当前时间
     * @return 统计；参数缺失时返回零值
     */
    OverdueStat overdue(Long tenantId, LocalDateTime now);

    /**
     * 运行中却没有任何待办的实例数（审批链卡死）
     *
     * @param tenantId 租户 ID
     * @return 卡住的实例数；参数缺失时返回 0
     */
    long stuckInstances(Long tenantId);

    /**
     * 按天聚合的发起与办结
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行、按日期升序（缺的日期不补，由调用方补 0）
     */
    List<DayFlow> dailyFlow(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 待办积压最多的审批节点
     *
     * @param tenantId 租户 ID
     * @param limit    取前几名
     * @return 按待办数降序；无待办时返回空表
     */
    List<Backlog> pendingBacklog(Long tenantId, int limit);
}
