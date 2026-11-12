package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子编辑历史实体，对应表 {@code fm_thread_edit_history}。
 *
 * <p>append-only：只插入、不更新、不删除，故<b>不继承</b> {@code BaseEntity}
 * （没有 {@code update_time / deleted}）。审核版块内编辑必须留原文快照，
 * 否则「审核通过后改正文」就能绕过审核且无据可查。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_thread_edit_history")
public class ForumThreadEditHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 编辑人用户ID */
    private Long editorId;

    /** 编辑前标题 */
    private String titleBefore;

    /** 编辑前正文快照 */
    private String contentBefore;

    /** 编辑原因 */
    private String reason;

    /** 编辑时间 */
    private LocalDateTime createTime;
}
