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
 * 页面动态实体，对应表 {@code doc_page_activity}。
 *
 * <p>append-only：不设 {@code update_time} / {@code deleted}，不使用 {@code BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("doc_page_activity")
public class DocPageActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 动作 */
    private String action;

    /** 操作人ID */
    private Long actorId;

    /** 动作摘要（不含正文） */
    private String detail;

    /** 发生时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
