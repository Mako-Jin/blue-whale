package com.bluewhale.message.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 回执回调任务实体（outbox），对应表 {@code msg_callback_task}。
 *
 * <p>回执落库后同步回调业务方会把回执上报接口的可用性绑到业务方可用性上，
 * 因此改为先落任务再异步派发，失败按指数退避重试。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_callback_task")
public class MessageCallbackTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（派发时作为 X-Tenant-Id 回传，后台线程无租户上下文时必须显式落值） */
    private Long tenantId;

    /** 消息ID */
    private Long messageId;

    /** 投递记录ID */
    private Long deliveryId;

    /** 触发本回调的回执流水ID */
    private Long receiptId;

    /**
     * 回调地址（落任务时从 msg_record.callback_url 快照）。
     *
     * <p>业务方后续修改回调地址只影响新产生的任务，历史任务仍投递到快照地址——
     * 这是刻意行为，避免回执已排队后地址漂移导致投递目标不可控。</p>
     */
    private String callbackUrl;

    /** 回调报文 JSON */
    private String payloadJson;

    /** 回调状态：PENDING / DISPATCHING / SENT / FAILED / GAVE_UP */
    private String status;

    /** 派发实例标识（抢占成功时写入，结束或超时回收后清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dispatchOwner;

    /** 派发抢占时间（多实例超时回收依据） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dispatchAt;

    /** 已尝试次数 */
    private Integer attempts;

    /** 下次重试时间（成功后需清空，故用 ALWAYS 更新策略） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRetryAt;

    /** 最近一次失败原因（成功后需清空，故用 ALWAYS 更新策略） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;
}
