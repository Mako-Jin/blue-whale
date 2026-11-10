package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.entity.PayNotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 业务通知任务 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PayNotifyTaskMapper extends BaseMapper<PayNotifyTask> {

    /**
     * 抢占式领取任务（多实例并发下只允许一个投递者）。
     *
     * <p>条件更新按影响行数判成败：返回 0 表示任务已被其他实例领取或状态已变更。</p>
     *
     * @param id       任务 ID
     * @param expected 期望的当前重试次数（乐观锁）
     * @return 影响行数
     */
    @Update("UPDATE pay_notify_task SET retry_count = retry_count + 1, update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING' AND retry_count = #{expected}")
    int claimForDelivery(@Param("id") Long id, @Param("expected") int expected);

    /**
     * 投递成功
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Update("UPDATE pay_notify_task SET status = 'SUCCESS', last_error = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSuccess(@Param("id") Long id);

    /**
     * 投递失败：未超上限则安排下次重试，超上限置 DEAD
     *
     * @param id          任务 ID
     * @param maxRetries  最大重试次数
     * @param nextRetryAt 下次重试时间
     * @param errorMessage 失败原因
     * @return 影响行数
     */
    @Update("UPDATE pay_notify_task SET status = IF(retry_count >= #{maxRetries}, 'DEAD', 'PENDING'), "
            + "next_retry_at = #{nextRetryAt}, last_error = #{errorMessage}, update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markFailure(@Param("id") Long id, @Param("maxRetries") int maxRetries,
            @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt,
            @Param("errorMessage") String errorMessage);
}
