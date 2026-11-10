package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发表评论命令。
 *
 * @author BlueWhale
 */
@Data
public class CommentSubmitCommand {

    /** 评论正文（纯文本；空与长度统一由领域校验，返回 40014） */
    private String content;

    /** 父评论 ID；顶级评论传 0 或不传 */
    @NotNull(message = "父评论 ID 不能为空")
    private Long parentId = 0L;
}
