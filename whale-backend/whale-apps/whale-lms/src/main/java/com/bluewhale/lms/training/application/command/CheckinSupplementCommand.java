package com.bluewhale.lms.training.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 补签命令：补签必须留痕（操作人取当前登录人，备注必填）。
 *
 * @author BlueWhale
 */
@Data
public class CheckinSupplementCommand {

    /** 学员用户 ID */
    @NotNull(message = "{validation.lms.checkin-supplement.user-id-required}")
    private Long userId;

    /** 学员用户名（展示用，可空） */
    @Size(max = 64, message = "{validation.lms.checkin-supplement.username-size}")
    private String username;

    /** 补签原因（必填，留痕） */
    @NotBlank(message = "{validation.lms.checkin-supplement.remark-required}")
    @Size(max = 200, message = "{validation.lms.checkin-supplement.remark-size}")
    private String remark;
}
