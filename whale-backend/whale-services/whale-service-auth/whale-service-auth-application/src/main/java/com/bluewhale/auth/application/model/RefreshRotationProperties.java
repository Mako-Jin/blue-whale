package com.bluewhale.auth.application.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * refreshToken 一次性轮换配置（AUTH-39 / ADR-001）。
 *
 * <p>{@code whale.auth.refresh.rotation.enabled}：灰度开关，<b>默认 {@code false}</b>。
 * 因前端 token 持久化替换（AUTH-32 / AUTH-34）尚未就绪，若默认开启会把「多标签页/并发刷新
 * 复用同一旧 refreshToken」的旧客户端误判为泄露并整族吊销，故须与前端改造同批发布后再开启；
 * 开关变更秒级生效，可快速回滚（ADR-001 R1）。</p>
 *
 * @author BlueWhale
 *
 * TODO 配置类都应该放到基础设施层
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.refresh.rotation")
public class RefreshRotationProperties {

    /** 是否启用 refreshToken 一次性轮换（默认关闭，避免旧客户端被误判复用） */
    private boolean enabled = false;
}
