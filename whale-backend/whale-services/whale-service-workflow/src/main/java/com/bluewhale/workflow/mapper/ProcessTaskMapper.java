package com.bluewhale.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.workflow.po.ProcessTaskPo;
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
}
