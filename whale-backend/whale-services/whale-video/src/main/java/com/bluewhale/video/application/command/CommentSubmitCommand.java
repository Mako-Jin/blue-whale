package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论发表命令。
 *
 * @author BlueWhale
 */
@Data
public class CommentSubmitCommand {

    /** 评论正文（纯文本，≤500 字） */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过 500 个字符")
    private String content;

    /** 父评论 ID，0 或空表示顶级评论 */
    private Long parentId;
}
