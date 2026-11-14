package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抄送记录视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ProcessCcVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 抄送记录ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 抄送节点键（发起级为 START） */
    private String nodeKey;

    /** 抄送节点名称 */
    private String nodeName;

    /** 收件人 */
    private String recipient;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
