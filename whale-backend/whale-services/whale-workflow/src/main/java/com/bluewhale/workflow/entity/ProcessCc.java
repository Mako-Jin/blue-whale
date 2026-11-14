package com.bluewhale.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程抄送实体，对应表 {@code process_cc}。
 *
 * <p>抄送为“通知”语义，不产生待办；发起级抄送 {@code nodeKey} 固定为 {@code START}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_cc")
public class ProcessCc extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属流程实例ID */
    private Long instanceId;

    /** 抄送节点键（发起级为 {@code START}） */
    private String nodeKey;

    /** 抄送节点名称 */
    private String nodeName;

    /** 收件人账号（发起级与节点级统一口径） */
    private String recipient;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;
}
