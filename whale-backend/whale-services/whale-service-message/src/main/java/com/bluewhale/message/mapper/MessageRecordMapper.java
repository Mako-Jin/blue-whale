package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.po.MessageRecordPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息记录 Mapper。
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface MessageRecordMapper extends BaseMapper<MessageRecordPo> {

    /**
     * 查询存在超时未投递消息的租户。
     *
     * <p>忽略租户插件：补偿是跨租户的运维动作，取到租户后由调用方逐租户回填上下文。</p>
     *
     * @param deadline 创建时间早于该值视为超时
     * @return 租户ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM msg_record "
            + "WHERE deleted = 0 AND status = 'PENDING' AND create_time < #{deadline}")
    List<Long> selectPendingTenantIds(@Param("deadline") LocalDateTime deadline);

    /**
     * 回收抢占超时的异步投递：进程崩溃会让记录停留在 {@code DISPATCHING}。
     *
     * <p>忽略租户插件：回收是跨租户的运维动作。</p>
     *
     * @param deadline 更新时间早于该值视为抢占超时
     * @param now      当前时间
     * @return 回收行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE msg_record SET status = 'PENDING', update_time = #{now} "
            + "WHERE deleted = 0 AND status = 'DISPATCHING' AND update_time < #{deadline}")
    int recoverStaleDispatching(@Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now);

    /**
     * 查询存在过期消息的租户。
     *
     * <p>忽略租户插件：归档是跨租户的运维动作，取到租户后由调用方逐租户回填上下文。</p>
     *
     * @param deadline 创建时间早于该值视为过期
     * @return 租户ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM msg_record WHERE deleted = 0 AND create_time < #{deadline}")
    List<Long> selectExpiredTenantIds(@Param("deadline") LocalDateTime deadline);
}
