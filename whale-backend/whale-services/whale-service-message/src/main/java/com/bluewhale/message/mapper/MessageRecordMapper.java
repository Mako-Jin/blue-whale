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

    /**
     * 积压未发的消息条数（通政殿概览 T5.8）
     *
     * <p>「积压」= 状态还停在 {@code PENDING / DISPATCHING}，且创建时间已超过阈值。
     * 这是消息服务最典型的静默失效：接口返回成功、业务方认为已通知，收件人什么也没收到，
     * 而 {@code PENDING} 本身既不报错也不告警。</p>
     *
     * <p>阈值由调用方给（概览取 10 分钟，是异步补偿阈值 120 秒的数倍余量）：
     * 卡住 30 秒是正常排队，卡住 10 分钟就是补偿任务也没救回来。</p>
     *
     * @param tenantId 租户 ID
     * @param deadline 创建时间早于该值视为积压
     * @return 积压条数；无数据时为 0
     */
    @Select("SELECT COUNT(*) FROM msg_record "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN ('PENDING', 'DISPATCHING') "
            + "AND create_time < #{deadline}")
    long countBacklog(@Param("tenantId") Long tenantId, @Param("deadline") LocalDateTime deadline);
}
