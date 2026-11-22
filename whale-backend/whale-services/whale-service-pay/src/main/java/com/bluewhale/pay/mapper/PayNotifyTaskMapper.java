package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务通知任务 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PayNotifyTaskMapper extends BaseMapper<PayNotifyTaskPo> {

    /**
     * 查询到期待重试任务（全局扫描，跨租户）。
     *
     * <p>定时任务线程没有租户上下文，这里显式忽略租户拦截器：重试器是平台级组件，
     * 必须能扫到所有租户的任务；投递时再从载荷恢复 {@code tenantId} 回调业务方。</p>
     *
     * @param now   当前时刻
     * @param limit 单轮最大条数
     * @return 到期任务
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM pay_notify_task "
            + "WHERE status = 'PENDING' AND deleted = 0 AND next_retry_at <= #{now} "
            + "ORDER BY next_retry_at ASC LIMIT #{limit}")
    List<PayNotifyTaskPo> selectDueForRetry(@Param("now") LocalDateTime now, @Param("limit") int limit);

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

    /**
     * 待投递任务数（全局指标口径）。
     *
     * @return 待投递任务数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM pay_notify_task WHERE status = 'PENDING' AND deleted = 0")
    long countPending();

    /**
     * 死信任务数（全局指标口径）。
     *
     * @return 死信任务数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM pay_notify_task WHERE status = 'DEAD' AND deleted = 0")
    long countDead();

    /**
     * 最早待投递任务的创建时间（积压时长指标口径）。
     *
     * @return 创建时间；无待投递任务时返回 {@code null}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT MIN(create_time) FROM pay_notify_task WHERE status = 'PENDING' AND deleted = 0")
    java.time.LocalDateTime selectOldestPendingCreateTime();
}
