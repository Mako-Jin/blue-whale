package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面版本快照实体，对应表 {@code doc_page_version}。
 *
 * <p>append-only：不设 {@code update_time} / {@code deleted}，不使用 {@code BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("doc_page_version")
public class DocPageVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 快照对应的 revision */
    private Long revisionNo;

    /** 快照标题 */
    private String title;

    /** 快照正文 JSON */
    private String contentJson;

    /** 快照纯文本投影 */
    private String contentText;

    /** 快照类型：MANUAL / PUBLISH / AUTO / RESTORE / IMPORT */
    private String versionKind;

    /** 变更说明 */
    private String changeSummary;

    /** 编辑人ID */
    private Long editorId;

    /** SHA-256(content_json) */
    private String contentHash;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
