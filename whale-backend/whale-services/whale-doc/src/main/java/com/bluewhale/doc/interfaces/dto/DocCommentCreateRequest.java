package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建评论请求。
 *
 * @author BlueWhale
 */
@Data
public class DocCommentCreateRequest {

    /** 评论内容 */
    @NotBlank(message = "{validation.doc.comment.content-required}")
    @Size(max = 2000, message = "{validation.doc.comment.content-size}")
    private String content;

    /** 父评论ID，0 表示主评论 */
    @Min(value = 0, message = "{validation.doc.comment.parent-id-min}")
    private Long parentId = 0L;

    /** TipTap 块ID（行内锚点） */
    @Size(max = 64, message = "{validation.doc.comment.anchor-block-id-size}")
    private String anchorBlockId;

    /** 锚点摘要 */
    @Size(max = 500, message = "{validation.doc.comment.anchor-text-size}")
    private String anchorText;

    /** 被 @ 的用户ID（M3 通知）；缺省时从正文中的 {@code @用户ID} 解析 */
    @Size(max = 10, message = "{validation.doc.comment.mentioned-user-ids-size}")
    private List<Long> mentionedUserIds;
}
