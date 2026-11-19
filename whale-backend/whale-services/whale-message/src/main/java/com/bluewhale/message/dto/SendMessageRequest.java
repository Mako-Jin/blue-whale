package com.bluewhale.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
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
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    /** 业务标识（最长 64） */
    @Size(max = 64, message = "业务标识长度不能超过 64")
    private String bizId;

    /** 幂等键（最长 96）；同一租户内重复提交返回既有消息 */
    @Size(max = 96, message = "幂等键长度不能超过 96")
    private String idempotencyKey;

    /** 模板编码（可选）；使用模板时标题/内容可由模板渲染 */
    @Size(max = 64, message = "模板编码长度不能超过 64")
    private String templateCode;

    /** 消息标题（最长 128） */
    @Size(max = 128, message = "消息标题长度不能超过 128")
    private String title;

    /** 消息正文（最长 8000） */
    @Size(max = 8000, message = "消息正文长度不能超过 8000")
    private String content;

    /** 模板变量 */
    private Map<String, Object> payload;

    /** 渠道列表：INAPP / EMAIL / SMS / IM（元素级校验，非法渠道直接 400） */
    @NotEmpty(message = "消息渠道不能为空")
    @Size(max = 8, message = "单次渠道数量不能超过 8")
    private List<@NotBlank(message = "渠道不能为空") @Size(max = 16, message = "渠道长度不能超过 16") String> channels;

    /** 接收人列表（单次最多 100 人） */
    @NotEmpty(message = "接收人不能为空")
    @Size(max = 100, message = "单次接收人不能超过 100 人")
    private List<@Valid MessageRecipient> recipients;

    /** 回执回调地址（可选，业务方内网端点；为空表示不回传达送回执） */
    @Size(max = 512, message = "回调地址长度不能超过 512")
    @Pattern(regexp = "^https?://.+", message = "回调地址必须为 http / https 地址")
    private String callbackUrl;

    /**
     * 校验消息体：模板编码或标题/正文至少提供一个，避免投递空消息。
     *
     * @return 满足其一返回 {@code true}
     */
    @AssertTrue(message = "templateCode 或 title/content 至少提供一个")
    public boolean isMessageBodyPresent() {
        return hasText(templateCode) || hasText(title) || hasText(content);
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
