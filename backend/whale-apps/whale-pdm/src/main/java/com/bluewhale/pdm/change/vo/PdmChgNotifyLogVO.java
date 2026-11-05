package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更通知投递日志视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgNotifyLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 变更通知ID */
    private Long noticeId;

    /** 事件类型 */
    private String eventType;

    /** 标题 */
    private String title;

    /** 状态：SUCCESS/FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最后失败原因 */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createTime;
}
