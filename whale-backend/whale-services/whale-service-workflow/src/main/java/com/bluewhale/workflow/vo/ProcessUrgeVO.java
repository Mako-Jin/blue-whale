package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 催办记录视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ProcessUrgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 任务ID */
    private Long taskId;

    /** 催办人账号 */
    private String urger;

    /** 被催办人账号 */
    private String targetAccount;

    /** 催办说明 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;
}
