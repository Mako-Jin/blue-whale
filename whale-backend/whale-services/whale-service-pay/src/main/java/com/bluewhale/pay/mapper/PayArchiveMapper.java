package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 数据归档 Mapper。
 *
 * <p>归档是平台级任务：定时线程没有租户上下文，必须显式忽略租户拦截器。
 * 每批先 {@code INSERT IGNORE ... SELECT} 再 {@code DELETE ... LIMIT}：
 * 中断后重跑时已搬迁的行被唯一键忽略，随后删除，不丢不重。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface PayArchiveMapper {

    /**
     * 搬迁一批支付流水到归档表。
     *
     * @param before 创建时间早于该时刻
     * @param limit  单批行数
     * @return 实际写入行数（已存在的归档行不计）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO pay_transaction_history (id, tenant_id, order_id, order_no, type, channel, "
            + "amount, currency, provider_trade_no, status, error_message, create_time, update_time, deleted) "
            + "SELECT id, tenant_id, order_id, order_no, type, channel, amount, currency, provider_trade_no, "
            + "status, error_message, create_time, update_time, deleted FROM pay_transaction "
            + "WHERE create_time < #{before} ORDER BY id LIMIT #{limit}")
    int archiveTransactions(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 删除已归档的支付流水。
     *
     * @param before 创建时间早于该时刻
     * @param limit  单批行数
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM pay_transaction WHERE create_time < #{before} ORDER BY id LIMIT #{limit}")
    int deleteArchivedTransactions(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 搬迁一批成功通知任务到归档表。
     *
     * @param before 创建时间早于该时刻
     * @param limit  单批行数
     * @return 实际写入行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO pay_notify_task_history (id, tenant_id, biz_type, biz_order_no, event, "
            + "notify_url, payload, status, retry_count, next_retry_at, last_error, create_time, update_time, deleted) "
            + "SELECT id, tenant_id, biz_type, biz_order_no, event, notify_url, payload, status, retry_count, "
            + "next_retry_at, last_error, create_time, update_time, deleted FROM pay_notify_task "
            + "WHERE status = 'SUCCESS' AND create_time < #{before} ORDER BY id LIMIT #{limit}")
    int archiveNotifyTasks(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 删除已归档的成功通知任务。
     *
     * @param before 创建时间早于该时刻
     * @param limit  单批行数
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM pay_notify_task WHERE status = 'SUCCESS' AND create_time < #{before} "
            + "ORDER BY id LIMIT #{limit}")
    int deleteArchivedNotifyTasks(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
