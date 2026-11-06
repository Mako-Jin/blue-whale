package com.bluewhale.party.content.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 党建内容实体，对应表 {@code pty_content}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_content")
public class PartyContentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 分类 */
    private String category;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 正文 */
    private String body;

    /** 附件文件 ID（逗号分隔） */
    private String attachmentFileIds;

    /** 可见范围 */
    private String visibility;

    /** 指定支部 ID（逗号分隔） */
    private String branchScope;

    /** 状态 */
    private String status;

    /** 作者用户 ID */
    private Long authorId;

    /** 作者姓名 */
    private String authorName;

    /** 最近提交审核时间 */
    private LocalDateTime submitTime;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;

    /** 下线时间 */
    private LocalDateTime offlineAt;

    /** 阅读人数 */
    private Integer readCount;

    /** 备注 */
    private String remark;
}
