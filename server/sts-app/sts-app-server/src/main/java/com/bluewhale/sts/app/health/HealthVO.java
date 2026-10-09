package com.bluewhale.sts.app.health;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 健康检查出参。
 *
 * <p>对外只暴露<b>无敏感信息</b>的运行时事实。刻意不返回依赖组件的连接串、
 * 构建机器、环境变量等内容 —— 健康检查端点常常无需认证即可访问，
 * 任何返回字段都等同于公开信息，是内网信息泄露最常见的入口之一。
 *
 * <p>{@code dependencies} 里只放"依赖名 → UP/DOWN"这样的判定结论，
 * 不放耗时、错误详情、连接地址：那些信息对运维有用，但对任何能访问本端点的人同样有用。
 *
 * @param application  服务名，取自 {@code spring.application.name}
 * @param status       聚合状态，任一关键依赖不可用即为 {@code DOWN}
 * @param profile      激活的环境标识，未指定时为 {@code default}
 * @param uptimeMillis 已运行毫秒数，用于判断是否发生过重启
 * @param serverTime   服务端当前时间，用于排查客户端时钟偏差
 * @param dependencies 依赖组件状态
 */
@Schema(name = "HealthVO", description = "服务健康状态")
public record HealthVO(
        String application,
        String status,
        String profile,
        long uptimeMillis,
        String serverTime,
        Map<String, String> dependencies
) {
}
