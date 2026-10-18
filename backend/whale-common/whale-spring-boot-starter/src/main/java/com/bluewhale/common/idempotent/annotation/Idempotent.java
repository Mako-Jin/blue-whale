package com.bluewhale.common.idempotent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解（基于 Redisson {@code RBucket.trySet} 的 SETNX 语义）。
 * <p>Key 结构：{@code {prefix}:{biz}:{token}}，默认前缀 {@code whale}，与命名规范
 * REQ-ARCH-004 的 {@code whale:<域>:<用途>:<标识>} 对齐；token 来源优先级为注解 {@code key} SpEL 求值
 * → 当前请求头 {@code tokenHeader}。首次调用成功后缓存返回体（JSON 文本），
 * 相同 token 的重复调用直接返回缓存结果；首次执行抛异常时释放 Key 以允许重试。</p>
 * <p>token 为空（SpEL 求值为空且请求头缺失）时不做幂等约束，直接放行目标方法。</p>
 *
 * @author BlueWhale
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务标识（必填），如 {@code auth:user:create} */
    String biz();

    /** SpEL 表达式（如 {@code #request.username}），非空时优先于请求头 */
    String key() default "";

    /** 读取幂等 token 的请求头名称 */
    String tokenHeader() default "X-Idempotent-Token";

    /** 幂等键与结果缓存的过期时长（秒） */
    long expireSeconds() default 3600;

    /** Redis Key 前缀，默认 {@code whale}，与命名规范 REQ-ARCH-004 对齐 */
    String prefix() default "whale";
}
