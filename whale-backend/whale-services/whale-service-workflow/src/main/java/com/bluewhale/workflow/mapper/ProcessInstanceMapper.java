package com.bluewhale.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.stat.ProcessDayCountPo;
import com.bluewhale.workflow.po.stat.ProcessInstanceStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一注册。</p>
 *
 * <p>统计类方法（政事殿概览 T5.6）一律**显式带 {@code tenant_id}**：概览会被数枢殿总控
 * 在聚合线程里调用，线程上下文的租户是"调用者"，靠上下文取租户等于把别人的数算进来。</p>
 *
 * <p>时间条件一律写成 {@code column >= ? AND column < ?}，函数不套在条件列上——
 * {@code DATE(create_time) = ?} 会让 {@code idx_tenant_create_time} 直接失效。
 * {@code DATE(...)} 只出现在 SELECT / GROUP BY 里做分组标签。</p>
 *
 * @author BlueWhale
 */
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstancePo> {

    /**
     * 运行中 / 今日发起 / 今日办结（政事殿概览 T5.6）
     *
     * <p>三个数一次查出来，理由同 {@code JobInfoMapper#countJobs}：分三条查会让
     * "运行中 12、今日发起 30、今日办结 40"这种自相矛盾的组合同时出现在一屏上。</p>
     *
     * <p>"办结"只算审批通过（{@code status = 1}）：{@code finished_at} 本就只在通过时写入，
     * 驳回与撤销走的是 {@code update_time}，把它们算进"办结"会让通过率看起来虚高。</p>
     *
     * @param tenantId 租户 ID
     * @param from     当日窗口起点（含）
     * @param to       当日窗口终点（不含）
     * @return 三个即时数；无数据时均为 0
     */
    @Select("SELECT "
            + "COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS running, "
            + "COALESCE(SUM(CASE WHEN create_time >= #{from} AND create_time < #{to} "
            + "THEN 1 ELSE 0 END), 0) AS startedToday, "
            + "COALESCE(SUM(CASE WHEN status = 1 AND finished_at >= #{from} "
            + "AND finished_at < #{to} THEN 1 ELSE 0 END), 0) AS finishedToday "
            + "FROM process_instance WHERE tenant_id = #{tenantId} AND deleted = 0")
    ProcessInstanceStatPo statOverview(@Param("tenantId") Long tenantId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * 按天聚合的发起数（政事殿概览 T5.6）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS total FROM process_instance "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY DATE(create_time) ORDER BY day")
    List<ProcessDayCountPo> countStartedByDay(@Param("tenantId") Long tenantId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * 按天聚合的办结数（政事殿概览 T5.6）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    @Select("SELECT DATE(finished_at) AS day, COUNT(*) AS total FROM process_instance "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 "
            + "AND finished_at >= #{from} AND finished_at < #{to} "
            + "GROUP BY DATE(finished_at) ORDER BY day")
    List<ProcessDayCountPo> countFinishedByDay(@Param("tenantId") Long tenantId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /**
     * 运行中却没有任何待办任务的实例数（政事殿概览 T5.6）
     *
     * <p>这是审批链最典型的**静默失效**：实例停在"运行中"，但没人有待办，于是谁也不会
     * 收到催办，流程永远不动、也不报错。判据用 {@code NOT EXISTS} 而不是
     * {@code LEFT JOIN + COUNT(*) = 0}——后者在任务表行数上来后会把整张表拉进 join，
     * 而这里只需要"有没有"。</p>
     *
     * @param tenantId 租户 ID
     * @return 卡住的实例数
     */
    @Select("SELECT COUNT(*) FROM process_instance i "
            + "WHERE i.tenant_id = #{tenantId} AND i.deleted = 0 AND i.status = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM process_task t "
            + "WHERE t.instance_id = i.id AND t.tenant_id = i.tenant_id "
            + "AND t.deleted = 0 AND t.status = 0)")
    long countStuck(@Param("tenantId") Long tenantId);
}
