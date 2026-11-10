package com.bluewhale.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解（基于 Redisson {@code RLock}）。
 * <p>锁 Key 结构：{@code {prefix}:{biz}:{SpEL 求值结果}}，如
 * {@code lock:auth:user:register:13800000000}；{@code key} 为 SpEL 表达式时
 * 以方法参数为根对象求值，为空则退化为 {@code {类名}.{方法名}}（全局串行）。</p>
 * <p>使用约束：加锁切面优先级高于 {@code @Transactional}，使锁覆盖整个事务，
 * 避免"锁已释放但事务未提交"；{@code leaseTime} 显式给定（非 -1）时 Redisson
 * <b>不启用看门狗续期</b>，须评估业务耗时。</p>
 *
 * @author BlueWhale
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** Key 前缀，默认 {@code lock}，与设计约定 {@code lock:{biz}:{id}} 对齐 */
    String prefix() default "lock";

    /** 业务标识（必填），如 {@code auth:user:register} */
    String biz();

    /** SpEL 表达式（如 {@code #phone} / {@code #request.userId}），为空时退化为方法签名 */
    String key() default "";

    /** 最长等待时长（秒），0 表示不等待（获取即失败） */
    long waitTime() default 0;

    /** 持有锁时长（秒），-1 表示启用 Redisson 看门狗自动续期 */
    long leaseTime() default 30;

    /**
     * 获取失败策略：true 抛 {@code BizException}，false 记录告警后放行。
     * <p>默认必须保持 true。{@code false} 仅允许用于「幂等且可退化」的场景（典型如
     * 权限缓存重建互斥：抢锁失败即视为已有并发重建，直接执行原逻辑即可），并且调用点
     * 必须注释说明放行理由；目前全仓仅 {@code PermissionCacheMutex} 一处，已由
     * 架构测试守护，新增放行点须同步评审。</p>
     */
    boolean failFast() default true;
}
