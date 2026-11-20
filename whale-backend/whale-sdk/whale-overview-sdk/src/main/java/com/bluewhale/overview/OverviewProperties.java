package com.bluewhale.overview;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 概览 SDK 配置（前缀 {@code whale.overview}）。
 *
 * <p>三个旋钮都必须是"配置里有默认值"而不是"代码里写死"：
 * 不同服务的取数成本差一个数量级（ERP 要连查三张表，OA 只查一个计数），
 * 统一 30s/3s 只是起点，接入方按自己的成本调。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.overview")
public class OverviewProperties {

    /** 是否启用概览接口；关掉后不注册控制器，服务行为回到接入前 */
    private boolean enabled = true;

    /**
     * 概览端点路径（含服务一级前缀）。
     *
     * <p>示例：{@code /erp/overview}。放在配置里而不是写死在控制器上，
     * 是因为各服务前缀本来就不在 Java 代码里（REQ-ARCH-003：网关不剥前缀）。</p>
     */
    private String basePath = "/overview";

    /** 服务端缓存 TTL；前端轮询间隔应 ≥ 它，避免每次轮询都穿透到 provider */
    private Duration ttl = Duration.ofSeconds(30);

    /** provider 执行超时；超时即中断并降级，不允许挂住 HTTP 线程 */
    private Duration timeout = Duration.ofSeconds(3);

    /** 超时/异常时是否降级为"上次成功"（stale）；false 则直接返回 degraded 空态 */
    private boolean serveStaleOnError = true;
}
