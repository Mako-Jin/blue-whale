package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.po.MessageCallbackTaskPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 回执回调任务 Mapper。
 *
 * @author BlueWhale
 */
public interface MessageCallbackTaskMapper extends BaseMapper<MessageCallbackTaskPo> {

    /**
     * 原子抢占待派发任务：多实例部署时只有一个实例能更新成功。
     *
     * @param id    任务ID
     * @param owner 实例标识
     * @param now   抢占时间
     * @return 更新行数；0 表示已被其它实例抢占或状态已变化
     */
    @Update("UPDATE msg_callback_task SET status = 'DISPATCHING', dispatch_owner = #{owner}, "
            + "dispatch_at = #{now}, attempts = attempts + 1, update_time = #{now} "
            + "WHERE id = #{id} AND deleted = 0 AND status IN ('PENDING', 'FAILED')")
    int claim(@Param("id") Long id, @Param("owner") String owner, @Param("now") LocalDateTime now);

    /**
     * 回收派发超时的任务（进程崩溃或网络挂起导致状态长期停留在 DISPATCHING）。
     *
     * @param deadline 超时判定阈值（抢占时间早于该值视为超时）
     * @param now      当前时间
     * @return 回收行数
     */
    @Update("UPDATE msg_callback_task SET status = 'FAILED', next_retry_at = #{now}, "
            + "dispatch_owner = NULL, dispatch_at = NULL, update_time = #{now} "
            + "WHERE deleted = 0 AND status = 'DISPATCHING' "
            + "AND (dispatch_at IS NULL OR dispatch_at < #{deadline})")
    int recoverStale(@Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now);
}
