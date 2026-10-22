package com.bluewhale.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回答评价请求（{@code POST /agent/feedback}）。
 *
 * @author BlueWhale
 */
@Data
public class FeedbackRequest {

    /** 被评价的助手消息ID */
    @NotNull
    private Long messageId;

    /** 评价：1 有用 / -1 无用 */
    @NotNull
    private Integer rating;

    /** 评价备注 */
    @Size(max = 512)
    private String comment;
}
