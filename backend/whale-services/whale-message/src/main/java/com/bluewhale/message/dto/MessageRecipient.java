package com.bluewhale.message.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息接收人。
 *
 * <p>按渠道取用对应字段：INAPP/IM 使用 {@code userId}，EMAIL 使用 {@code email}，
 * SMS 使用 {@code phone}。缺少对应字段时该渠道记为 {@code SKIPPED}。</p>
 *
 * @author BlueWhale
 */
@Data
public class MessageRecipient implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID（INAPP / IM） */
    private Long userId;

    /** 邮箱（EMAIL） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 手机号（SMS） */
    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;
}
