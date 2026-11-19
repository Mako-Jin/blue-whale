package com.bluewhale.message.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 接收人组解析配置。
 *
 * <p>接收人组编码对应权限中心的角色编码，发送时展开为该角色下的启用用户。
 * 展开失败直接拒绝发送（接收人是投递主目标，静默发空会掩盖配置错误）。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whale.message.recipient")
public class MessageRecipientProperties {

    /** 是否允许使用接收人组；关闭时请求携带 recipientGroup 直接 400 */
    private boolean enabled = true;

    /** auth 内网基地址（服务发现直连） */
    private String authBaseUrl = "http://127.0.0.1:8081";

    /** 展开后接收人总数上限（含显式接收人） */
    @Min(1)
    private int maxMembers = 100;
}
