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
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过 2000 字")
    private String content;

    /** 父评论ID，0 表示主评论 */
    @Min(value = 0, message = "父评论ID非法")
    private Long parentId = 0L;

    /** TipTap 块ID（行内锚点） */
    @Size(max = 64, message = "锚点块ID不能超过 64 字")
    private String anchorBlockId;

    /** 锚点摘要 */
    @Size(max = 500, message = "锚点摘要不能超过 500 字")
    private String anchorText;

    /** 被 @ 的用户ID（M3 通知）；缺省时从正文中的 {@code @用户ID} 解析 */
    @Size(max = 10, message = "单次最多提及 10 人")
    private List<Long> mentionedUserIds;
}
