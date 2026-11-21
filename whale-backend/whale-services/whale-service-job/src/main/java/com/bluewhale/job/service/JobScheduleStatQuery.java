package com.bluewhale.job.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 时辰殿概览的统计口径（T5.5）。
 *
 * <p><b>为什么不直接复用 {@code JobLogService#stats}：</b>那个方法是为 2D 统计页服务的，
 * 它把窗口内明细**全部回捞到内存**再算 TopN 与 P95（上限 10 万行）。屏风页每 60 秒轮询一次，
 * 拿它当数据源等于每分钟拉 10 万行。本端口只做 count 型聚合，一条 SQL 出一个数。</p>
 *
 * <p><b>口径必须只有一处。</b>"今日执行几次、失败几次"如果屏风一套、2D 统计页另一套，
 * 迟早出现"城里说失败 3 次、后台说 5 次"。所以这里也把 {@code status IN (1, -1)}
 * 与"只数已结束执行"写进实现，并由单测钉住。</p>
 *
 * <p><b>租户显式传参。</b>聚合总控时线程上下文挂的是"调用者"身份，
 * 靠 {@code TenantContext} 取租户等于把别人的数算进这一屏。</p>
 *
 * @author BlueWhale
 */
public interface JobScheduleStatQuery {

    /**
     * 任务总数与运行中数量
     *
     * @param jobs    在册任务数
     * @param running 其中运行中的任务数
     */
    record JobCount(long jobs, long running) {
    }

    /**
     * 时间窗内的执行统计（只含已结束的执行）
     *
     * @param total   已结束的执行次数（成功 + 失败）
     * @param success 成功次数
     * @param failure 失败次数
     */
    record RunStat(long total, long success, long failure) {
    }

    /**
     * 按天聚合的执行次数
     *
     * @param day  日期
     * @param runs 当日已结束的执行次数
     */
    record DayRuns(LocalDate day, long runs) {
    }

    /**
     * 按任务聚合的失败次数
     *
     * @param jobName  任务名称（为空时按"未命名任务"归并）
     * @param failures 失败次数
     */
    record FailTop(String jobName, long failures) {
    }

    /**
     * 在册任务数与运行中数量
     *
     * @param tenantId 租户 ID
     * @return 统计；参数缺失时返回零值
     */
    JobCount jobCount(Long tenantId);

    /**
     * 时间窗内的执行统计
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 统计；参数缺失时返回零值
     */
    RunStat runsBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内出现过失败的任务数（去重）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 任务数
     */
    long failedJobCount(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 观察窗口内一次都没执行过的运行中任务数
     *
     * @param tenantId 租户 ID
     * @param since    观察窗口起点（含）
     * @return 静默任务数
     */
    long silentRunning(Long tenantId, LocalDateTime since);

    /**
     * 按天聚合的执行次数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    List<DayRuns> dailyRuns(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内失败次数最多的任务
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按失败次数降序；无失败时返回空表
     */
    List<FailTop> failTop(Long tenantId, LocalDateTime from, LocalDateTime to, int limit);
}
