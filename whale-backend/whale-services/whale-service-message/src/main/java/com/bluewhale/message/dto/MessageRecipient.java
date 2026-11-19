package com.bluewhale.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
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
    @Email(message = "{validation.message.recipient.email-pattern}")
    @Size(max = 128, message = "{validation.message.recipient.email-size}")
    private String email;

    /** 手机号（SMS）；宽松校验（允许国际区号与分隔符），运营商号段由短信网关兜底 */
    @Size(max = 32, message = "{validation.message.recipient.phone-size}")
    @Pattern(regexp = "^\\+?[0-9][0-9\\-\\s]{4,31}$", message = "{validation.message.recipient.phone-pattern}")
    private String phone;

    /**
     * 校验至少提供一个可达标识，避免落库 recipient="-" 的脏投递记录。
     *
     * <p>{@code @JsonIgnore}：这是校验派生属性而非接收字段，不参与异步投递的
     * 接收人 JSON 序列化/反序列化。</p>
     *
     * @return 任一标识非空白返回 {@code true}
     */
    @JsonIgnore
    @AssertTrue(message = "{validation.message.recipient.identifier-required}")
    public boolean isReachable() {
        return userId != null || hasText(email) || hasText(phone);
    }

    /**
     * 判断文本非空白。
     *
     * @param value 文本
     * @return 非空白返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
