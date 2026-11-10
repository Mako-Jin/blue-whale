package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 变更通知投递日志
 *
 * <p>每次调用 whale-message 都落一条记录；失败保留失败原因并可重试，
 * 重试以 {@code idempotencyKey} 保证消息服务侧不重复投递。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_notify_log")
public class PdmChgNotifyLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 变更通知ID */
    private Long noticeId;

    /** 事件类型：ECN_NOTIFIED/ECN_EFFECTIVE/ECN_ALERT */
    private String eventType;

    /** 接收人 JSON */
    private String recipients;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 状态：SUCCESS/FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最后失败原因 */
    private String lastError;

    /** 幂等键 */
    private String idempotencyKey;
}
