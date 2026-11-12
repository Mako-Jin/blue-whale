package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交申诉请求。
 *
 * @author BlueWhale
 */
@Data
public class AppealCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对象类型：THREAD / POST / BAN */
    @NotBlank(message = "申诉对象类型不能为空")
    private String targetType;

    /** 对象ID（BAN 时为本人用户ID） */
    @NotNull(message = "申诉对象ID不能为空")
    private Long targetId;

    /** 申诉理由 */
    @NotBlank(message = "申诉理由不能为空")
    @Size(max = 500, message = "申诉理由不能超过 500 字")
    private String reason;
}
