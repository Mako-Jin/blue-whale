package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关内网接口屏蔽配置属性（T6）。
 *
 * <p>{@code whale.gateway.internal-deny.*}：{@code enabled} 控制内网接口屏蔽是否生效；
 * {@code patterns} 为禁止外部访问的路径模式列表（Ant 通配），命中即返回 HTTP 403。
 * 默认「单段星号 + /internal/」通配（星号不跨段），覆盖全部服务的
 * {@code /<service>/internal/**} 内网端点（上线必检 network-boundary 收口，
 * 2026-11-09 第九批起全量生效）。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.internal-deny")
public class InternalApiDenyProperties {

    /** 是否启用内网接口屏蔽，默认开启；关闭后不拦截任何路径 */
    private boolean enabled = true;

    /** 禁止外部访问的路径模式列表（Ant 通配），默认单段通配所有服务内网前缀 */
    private List<String> patterns = new ArrayList<>(List.of("/*/internal/**"));
}
