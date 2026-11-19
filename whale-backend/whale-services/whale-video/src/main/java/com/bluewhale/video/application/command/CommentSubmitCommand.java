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
    @NotBlank(message = "{validation.video.comment.content-required}")
    @Size(max = 500, message = "{validation.video.comment.content-size}")
    private String content;

    /** 父评论 ID，0 或空表示顶级评论 */
    private Long parentId;
}
