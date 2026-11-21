package com.bluewhale.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.po.stat.ProcessNodeBacklogPo;
import com.bluewhale.workflow.po.stat.ProcessOverdueStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办任务 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface ProcessTaskMapper extends BaseMapper<ProcessTaskPo> {

    /**
     * 查询已到期的待办任务（超时扫描用）。
     *
     * <p>手写 SQL 不走逻辑删除自动注入，需显式带 {@code deleted = 0}；
     * 后台线程无租户上下文时租户插件自动忽略，因此可跨租户扫描，
     * 由返回行的 {@code tenant_id} 还原租户。</p>
     *
     * @param now 当前时间（{@code due_at <= now} 视为已到期）
     * @param limit 单次扫描上限（防止一次拉全表）
     * @return 已到期待办（按到期时间升序）
     */
    @Select("SELECT id, tenant_id, instance_id, node_name, assignee, due_at FROM process_task "
            + "WHERE status = 0 AND deleted = 0 AND due_at IS NOT NULL AND due_at <= #{now} "
            + "ORDER BY due_at ASC LIMIT #{limit}")
    List<OverdueTaskRow> selectOverdue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 待办任务总数（政事殿概览 T5.6）
     *
     * <p>只数 {@code status = 0}：已同意 / 已驳回 / 已取消都是历史行，
     * 把它们算进"待办"会让这个数字只涨不跌，看的人很快就不再信它。</p>
     *
     * @param tenantId 租户 ID
     * @return 待办任务数
     */
    @Select("SELECT COUNT(*) FROM process_task "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 0")
    long countPending(@Param("tenantId") Long tenantId);

    /**
     * 已超时的待办数与最久超时时长（政事殿概览 T5.6）
     *
     * <p>时长取 minutes 而不是 hours：{@code TIMESTAMPDIFF(HOUR, ...)} 会把 30 分钟
     * 截成 0 小时，告警文案就成了"最久超时 0 小时"——看起来像没超时。</p>
     *
     * <p>{@code due_at IS NULL} 的任务表示未配置超时（节点没写 {@code timeoutHours}），
     * 不参与超时统计，这是 V13 的既定口径。</p>
     *
     * @param tenantId 租户 ID
     * @param now      当前时间（{@code due_at <= now} 视为已超时）
     * @return 超时数与最久超时分钟数；无超时时两者均为 0
     */
    @Select("SELECT COUNT(*) AS overdue, "
            + "COALESCE(MAX(TIMESTAMPDIFF(MINUTE, due_at, #{now})), 0) AS worstMinutes "
            + "FROM process_task WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 0 "
            + "AND due_at IS NOT NULL AND due_at <= #{now}")
    ProcessOverdueStatPo statOverdue(@Param("tenantId") Long tenantId,
                                     @Param("now") LocalDateTime now);

    /**
     * 待办积压最多的审批节点（政事殿概览 T5.6）
     *
     * <p>按 {@code node_name} 分组：这是给人看的柱图，节点键是 {@code n2} 这类机器名，
     * 读不出"堵在哪个环节"。名字为空的历史行由调用方折成"未命名节点"。</p>
     *
     * @param tenantId 租户 ID
     * @param limit    取前几名
     * @return 按待办数降序；无待办时返回空表
     */
    @Select("SELECT node_name AS nodeName, COUNT(*) AS pending FROM process_task "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 0 "
            + "GROUP BY node_name ORDER BY pending DESC LIMIT #{limit}")
    List<ProcessNodeBacklogPo> topPendingNodes(@Param("tenantId") Long tenantId,
                                               @Param("limit") int limit);
}
