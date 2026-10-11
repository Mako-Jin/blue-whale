package com.bluewhale.sts.auth.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明"访问该接口需要哪些权限"。
 *
 * <p>标注在 Controller 方法或类上，{@code value} 是资源标识
 * （即 {@code bw_auth_resource.resource_code}）。
 * 多个标识之间是**全部满足**（AND）语义 —— 需要"任一满足"时，
 * 应把它建模成一个新的组合资源，而不是在注解上加开关：
 * 组合逻辑（AND / OR / NOT）一多，注解就会变成一门没人记得住的小语言。
 *
 * <p><b>这是参照实现里完全缺失的一块。</b>{@code SystemTerminator} 的权限服务
 * 只有领域方法（{@code RoleInfoAggregate.hasPermission}），**没有任何运行时判定入口** ——
 * 注解、AOP、拦截器一概没有，也没有缓存。也就是说它的 RBAC 表建好了，
 * 但没有任何一条接口真的被它保护过。本切片把这条链路补上：
 * {@code AuthTokenFilter}（认证）→ {@code AuthInterceptor}（鉴权）→ 调用处。
 *
 * <p>用法：
 * <pre>{@code
 * @PostMapping
 * @RequiresPermission("auth:user:create")
 * public R<UserVO> create(@Valid @RequestBody UserSaveRequest request) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 需要的资源标识，多个之间为 AND */
    String[] value();

}
