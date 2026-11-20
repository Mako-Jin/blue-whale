package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 处置请求（删帖/禁言/封号共用）。
 *
 * @author BlueWhale
 */
@Data
public class ModerationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处置原因（必填） */
    @NotBlank(message = "{validation.forum.moderation.reason-required}")
    @Size(max = 512, message = "{validation.forum.moderation.reason-size}")
    private String reason;

    /** 处置类型：MUTE 禁言 / BAN 封号（仅用户处置用） */
    @Pattern(regexp = "MUTE|BAN", message = "{validation.forum.moderation.action-pattern}")
    private String banType;

    /** 天数（≤0 或空表示永久，仅用户处置用） */
    private Integer days;
}
