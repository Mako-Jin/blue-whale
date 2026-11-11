package com.bluewhale.auth.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 二次认证标记注解（Step-Up Authentication，Wave 6.22）
 *
 * <p>标记在危险操作端点（凭证类 / 不可逆 / 影响他人）上，要求请求头
 * {@code X-Reauth-Token} 携带有效期内（默认 5 分钟）的 step-up 凭证，
 * 否则由 {@code ReauthInterceptor} 返回 HTTP 428 + 业务码 {@code 42801}。</p>
 *
 * <p>与 {@link RequirePermission} 的关系：权限回答「能不能做」，本注解回答
 * 「此刻是否本人确认过」，二者叠加生效；内网端点不得标注本注解。</p>
 *
 * @author BlueWhale
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireReauth {
}
