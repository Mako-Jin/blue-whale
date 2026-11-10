package com.bluewhale.lms.integration.domain.model;

import lombok.Data;

/**
 * 通知投递日志查询条件。
 *
 * @author BlueWhale
 */
@Data
public class NotifyLogQuery {

    /** 通知场景 */
    private String scene;

    /** 接收人用户 ID */
    private Long userId;

    /** 投递状态：SENT / FAILED */
    private String sendStatus;

    /** 幂等键（精确） */
    private String idempotencyKey;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
