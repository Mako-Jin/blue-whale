package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 机器人回答反馈请求。
 *
 * @author BlueWhale
 */
@Data
public class FeedbackRequest {

    /** 评价：UP 有用 / DOWN 无用 */
    @NotBlank(message = "{validation.im.feedback.rating-required}")
    @Pattern(regexp = "UP|DOWN", message = "{validation.im.feedback.rating-pattern}")
    private String rating;
}
