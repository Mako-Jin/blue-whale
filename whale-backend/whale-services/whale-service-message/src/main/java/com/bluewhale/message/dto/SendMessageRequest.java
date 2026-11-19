package com.bluewhale.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服务间发送消息请求。
 *
 * <p>生产者负责业务判断，只把“已决定要发送”的消息交给本服务；本服务不解释业务状态。</p>
 *
 * @author BlueWhale
 */
@Data
public class SendMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户ID；内网调用必须显式传入，网关调用可省略并由租户上下文补齐 */
    private Long tenantId;

    /** 业务类型（最长 32） */
    @Size(max = 32, message = "{validation.message.send.biz-type-size}")
    private String bizType;

    /** 业务标识（最长 64） */
    @Size(max = 64, message = "{validation.message.send.biz-id-size}")
    private String bizId;

    /** 幂等键（最长 96）；同一租户内重复提交返回既有消息 */
    @Size(max = 96, message = "{validation.message.send.idempotency-key-size}")
    private String idempotencyKey;

    /** 模板编码（可选）；使用模板时标题/内容可由模板渲染 */
    @Size(max = 64, message = "{validation.message.send.template-code-size}")
    private String templateCode;

    /** 消息标题（最长 128） */
    @Size(max = 128, message = "{validation.message.send.title-size}")
    private String title;

    /** 消息正文（最长 8000） */
    @Size(max = 8000, message = "{validation.message.send.content-size}")
    private String content;

    /** 模板变量 */
    private Map<String, Object> payload;

    /** 渠道列表：INAPP / EMAIL / SMS / IM（元素级校验，非法渠道直接 400） */
    @NotEmpty(message = "{validation.message.send.channels-required}")
    @Size(max = 8, message = "{validation.message.send.channels-size}")
    private List<@NotBlank(message = "{validation.message.send.channel-required}")
            @Size(max = 16, message = "{validation.message.send.channel-size}") String> channels;

    /** 接收人列表（单次最多 100 人；与 recipientGroup 至少提供一个） */
    @Size(max = 100, message = "{validation.message.send.recipients-size}")
    private List<@Valid MessageRecipient> recipients;

    /**
     * 接收人组编码（可选）
     *
     * <p>由权限中心按角色编码展开为接收人（仅启用用户，按 userId 投递），
     * 与 {@link #recipients} 合并去重；组内成员数受
     * {@code whale.message.recipient.max-members} 限制。</p>
     */
    @Size(max = 64, message = "{validation.message.send.recipient-group-size}")
    private String recipientGroup;

    /**
     * 消息优先级（可选）：HIGH / NORMAL / LOW
     *
     * <p>仅影响异步投递的排队顺序，高优先级先出队；未设置时按 NORMAL 处理。</p>
     */
    @Pattern(regexp = "(?i)^(HIGH|NORMAL|LOW)$", message = "{validation.message.send.priority-pattern}")
    private String priority;

    /** 回执回调地址（可选，业务方内网端点；为空表示不回传达送回执） */
    @Size(max = 512, message = "{validation.message.send.callback-url-size}")
    @Pattern(regexp = "^https?://.+", message = "{validation.message.send.callback-url-pattern}")
    private String callbackUrl;

    /**
     * 消息过期时间（可选）
     *
     * <p>超过该时间后不再发起投递（含失败重试），对应投递记 {@code EXPIRED}，
     * 避免过期的通知/验证码在恢复后补发。</p>
     */
    private LocalDateTime expireAt;

    /**
     * 是否异步投递（可选）
     *
     * <p>{@code true} 时立即返回 {@code PENDING}，由后台线程池完成渠道调用；
     * 未设置时按 {@code whale.message.send.enabled} 决定，默认同步。</p>
     */
    private Boolean async;

    /**
     * 校验消息体：模板编码或标题/正文至少提供一个，避免投递空消息。
     *
     * @return 满足其一返回 {@code true}
     */
    @AssertTrue(message = "{validation.message.send.message-body-required}")
    public boolean isMessageBodyPresent() {
        return hasText(templateCode) || hasText(title) || hasText(content);
    }

    /**
     * 校验接收人：显式接收人列表与接收人组至少提供一个。
     *
     * @return 满足其一返回 {@code true}
     */
    @AssertTrue(message = "{validation.message.send.recipient-required}")
    public boolean isRecipientsPresent() {
        return (recipients != null && !recipients.isEmpty()) || hasText(recipientGroup);
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
