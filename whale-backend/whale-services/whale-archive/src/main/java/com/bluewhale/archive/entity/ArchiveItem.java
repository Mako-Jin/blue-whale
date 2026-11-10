package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 档案条目实体，对应表 {@code archive_item}。
 *
 * <p>{@code (sourceType, sourceId)} 唯一，保证同一审批实例只归档一次；
 * 确认归档时分配唯一 {@code archiveNo}，快照生成后不再随源实例变化而改写。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_item")
public class ArchiveItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 档号（确认归档时分配，唯一） */
    private String archiveNo;

    /** 档案标题 */
    private String title;

    /** 门类ID */
    private Long categoryId;

    /** 门类路径（编码以 / 连接） */
    private String categoryPath;

    /** 全宗号 */
    private String fondsCode;

    /** 归档年度 */
    private Integer year;

    /** 保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 到期时间（长期为空） */
    private LocalDateTime expireAt;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 来源类型：WORKFLOW 工作流实例 */
    private String sourceType;

    /** 来源实例ID（字符串承载） */
    private String sourceId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 业务单号 / 表单数据ID */
    private String businessKey;

    /** 发起人账号（工作流实例发起人，字符串账号） */
    private String initiator;

    /** 来源实例办结时间 */
    private LocalDateTime finishedAt;

    /** 状态：0 待整理 / 1 已归档 / 2 已销毁 */
    private Integer itemStatus;

    /** 档案摘要 */
    private String summary;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 不可变快照文件ID */
    private Long snapshotFileId;

    /** 快照 SHA-256 摘要 */
    private String snapshotHash;

    /** 确认归档人ID */
    private Long archivedBy;

    /** 确认归档时间 */
    private LocalDateTime archivedAt;
}
