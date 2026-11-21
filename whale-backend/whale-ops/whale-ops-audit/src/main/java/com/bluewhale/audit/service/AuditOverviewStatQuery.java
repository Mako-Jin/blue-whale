package com.bluewhale.audit.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 起居殿概览的统计口径（S5 / T5.9，只读）。
 *
 * <p><b>这一屏回答的问题：</b>审计留痕本身还活着吗？——一个审计服务最怕的不是"抓到坏人"，
 * 而是"该记的没记上"：摄入链路断了、归档作业停了，都很安静，不报错也不告警，等到要查账
 * 才发现那段时间是空白。因此除了常规的今日审计数/失败数，还专门给两个"静默失效"指标：
 * <b>留痕断流时长</b>（最近一条审计距今多久）与<b>归档作业陈旧天数</b>（心跳最后成功距今多久）。</p>
 *
 * <p><b>租户显式传参。</b>provider 在 SDK 独立线程池执行，没有请求线程的
 * {@code TenantContext}，靠插件隐式取租户会把别人的审计算进这一屏（还不会报错）。</p>
 *
 * @author BlueWhale
 */
public interface AuditOverviewStatQuery {

    /**
     * 按天聚合的审计条数
     *
     * @param day    日期
     * @param audits 当日审计条数
     */
    record DayAudit(LocalDate day, long audits) {
    }

    /**
     * 按操作类型聚合的条数
     *
     * @param operateType 操作类型（空值归 OTHER）
     * @param audits      条数
     */
    record OperateTypeCount(String operateType, long audits) {
    }

    /**
     * 时间窗内的审计条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 条数；参数缺失时返回 0
     */
    long todayCount(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内的失败操作数（biz_status = 0）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 失败条数；参数缺失时返回 0
     */
    long failedCount(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 本租户最近一条审计记录的写入时刻
     *
     * @param tenantId 租户 ID
     * @return 最近写入时刻；无租户或无记录时返回 null
     */
    LocalDateTime lastAuditTime(Long tenantId);

    /**
     * 按天聚合的审计条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺日不补，由调用方补 0）
     */
    List<DayAudit> dailyAudits(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 操作类型 TopN
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按条数降序；参数缺失时返回空表
     */
    List<OperateTypeCount> topOperateTypes(Long tenantId, LocalDateTime from, LocalDateTime to, int limit);

    /**
     * 归档作业最近一次成功时刻（平台级作业，无租户维度）
     *
     * @return 最近成功时刻；从未成功或无记录时返回 null
     */
    LocalDateTime archiveLastSuccessAt();
}
