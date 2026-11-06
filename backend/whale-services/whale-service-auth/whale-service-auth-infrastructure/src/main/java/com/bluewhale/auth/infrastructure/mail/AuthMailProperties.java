package com.bluewhale.auth.infrastructure.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 建用户后自动开邮箱的集成配置。
 *
 * <p>默认关闭。关闭时代码路径完全不触发，建用户行为与之前逐字节一致——
 * 这是刻意设计：邮件是可选能力，不能因为邮件服务没部署就让「建用户」这一个核心动作变慢或变脆。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.auth.mail")
public class AuthMailProperties {

    /** 是否启用建用户后自动开邮箱 */
    private boolean provisionEnabled = false;

    /** whale-mail 内网代开地址（不经网关） */
    private String provisionUrl = "http://whale-mail:8099/mail/internal/provision";

    /** 建连超时（毫秒）：这个值只影响后台任务，不影响创建用户的接口响应 */
    private int connectTimeoutMillis = 2_000;

    /** 读取超时（毫秒）：Mailcow 建邮箱含 TLS 校验，8 秒足够 */
    private int readTimeoutMillis = 8_000;
}
