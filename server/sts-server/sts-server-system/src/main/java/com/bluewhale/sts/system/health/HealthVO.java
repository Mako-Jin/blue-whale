package com.bluewhale.sts.system.health;

/**
 * 健康检查出参。
 *
 * <p>对外只暴露<b>无敏感信息</b>的运行时事实。刻意不返回依赖组件的连接串、
 * 构建机器、环境变量等内容 —— 健康检查端点常常无需认证即可访问，
 * 任何返回字段都等同于公开信息，是内网信息泄露最常见的入口之一。
 *
 * @param application  服务名，取自 {@code spring.application.name}
 * @param status       服务状态，当前恒为 {@code UP}
 * @param profile      激活的环境标识，未指定时为 {@code default}
 * @param uptimeMillis 已运行毫秒数，用于判断是否发生过重启
 * @param serverTime   服务端当前时间，用于排查客户端时钟偏差
 */
public record HealthVO(
        String application,
        String status,
        String profile,
        long uptimeMillis,
        String serverTime
) {
}
