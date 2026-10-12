package com.bluewhale.sts.components.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明「这个接口被调用时，产生一条审计记录」。
 *
 * <p><b>为什么是显式注解，而不是"自动拦下所有非 GET 请求"：</b>
 * 后者看起来省事，实际有两个致命问题。
 * <ol>
 *   <li><b>会把不该记的一起记进去。</b>登录、上报客户端日志、上报行为事件、健康检查全是 POST，
 *       它们要么属于另一条管道，要么高频到会把审计表冲垮 ——
 *       而审计的价值恰恰建立在"里面每一条都值得逐条看"之上。</li>
 *   <li><b>答不出"这是什么操作"。</b>{@code POST /api/auth/users} 是"新建用户"，
 *       {@code POST /api/auth/users/1/password} 是"重置口令"，
 *       {@code POST /api/auth/tenants} 是"新建租户" —— 同一个 HTTP 方法之下，
 *       语义千差万别。审计要回答的是"谁对什么做了什么"，"做了什么"这一项
 *       靠 HTTP 方法推不出来，只能由人来写。</li>
 * </ol>
 *
 * <p>所以这里与 {@code @RequiresPermission} 同构：都标在 Controller 的类或方法上，
 * 都由拦截器在进入业务代码之前读取。差别只在于一个决定"放不放行"，一个决定"记不记"。
 *
 * <p><b>{@link #action} 是永久契约。</b>一旦有记录落库，看板、告警、合规报表就按它查询；
 * 改名等于把历史数据与新数据劈成两半，而且改名通常只在"上游以为"的层面完成 ——
 * 下游要到某天发现报表断崖才知道。动作名一律 {@code 域.资源.动作} 小写点分三段
 * （如 {@code auth.user.create}），与权限码 {@code auth:user:create} 同构、
 * 只把冒号换成点 —— 这样"某个权限对应的操作被调用了几次"可以直接按前缀匹配，
 * 不必维护对照表；三段自带归属，也避免了两个域的同名动作在审计表里撞成同一个值。
 * <b>必须写在各域的常量类里</b>（如 {@code AuthAuditActions}），
 * 不要在注解里直接写字符串字面量 —— 拼错的动作名只会表现为"查不到记录"。
 *
 * <p><b>{@link #resource} 与动作分开，不是冗余：</b>两者回答的是不同的问题 ——
 * "涉及用户权限的操作有哪些"（按 {@code resource} 过滤）与
 * "用户被删除过几次"（按 {@code action} 过滤）。把 {@code resource} 的取值
 * 与权限码对齐（{@code auth:user}），还能顺带回答"谁动过这个权限"。
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Audited {

    /**
     * 动作标识，形如 {@code user.create}。**永久契约**，见类注释。
     */
    String action();

    /**
     * 被操作资源的标识，形如 {@code auth:user}。约定与权限码同形。
     */
    String resource();
}
