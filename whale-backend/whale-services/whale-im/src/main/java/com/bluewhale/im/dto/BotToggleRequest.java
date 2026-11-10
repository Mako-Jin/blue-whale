package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 群聊机器人开关请求。
 *
 * @author BlueWhale
 */
@Data
public class BotToggleRequest {

    /** 是否启用（true 启用 / false 停用） */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
