package com.bluewhale.sts.auth.common;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 「不参与权限判定」的端点登记处。
 *
 * <p><b>为什么需要这个类：</b>{@code AuthInterceptor} 的规则是**白名单反着写的** ——
 * 方法或类上**没有** {@code @RequiresPermission} 就一律放行（见 {@code docs/decisions/0026}，
 * 那里把它点成了"日志域 / 事件域当年完全公开"的根因）。这条规则本身是**有意**的：
 * 登录、健康检查、上报都不能要求权限码，否则会形成"要登录先得有令牌"或
 * "登录页出错时连错误都报不出来"的死锁。但它的代价是**新加一个端点而忘了写注解
 * 就等于静默公开** —— 不报错、不告警，评审时也看不出来。
 *
 * <p>本类是那半边白名单的**唯一登记处**：{@code PublicEndpointRegistryTest} 用框架自己的
 * 映射表（{@code RequestMappingHandlerMapping}）枚举全部 {@code /api/**} 端点，
 * 断言「有权限注解」与「登记在本表里」**恰好二选一**。于是"忘了写注解"从静默公开
 * 变成**构建失败**，而"这份清单"也从人工维护变成**可核对**的。
 *
 * <p><b>登记不等于"免登录"。</b>本表回答的是"拦截器判不判权限码"，不是"要不要令牌"。
 * 其中一部分仍由服务层要求已认证 —— {@code /api/auth/me}、{@code /api/auth/password}、
 * {@code /api/auth/logout-all} 都通过 {@code AuthContextHolder.require()} 表达，
 * 它们刻意**不要权限码**（任何登录用户都应能看自己的信息、改自己的口令、踢掉自己的会话），
 * 理由见 {@code AuthController} 的类注释。要回答"哪些接口完全不需要登录"，
 * 还需叠上 {@code AuthTokenFilter} 那一层。
 *
 * <p><b>与 {@code bw_auth_resource.is_whitelist} 的分工：</b>那一列**不读也不可能是这份清单** ——
 * 种子资源由 {@link AuthPermissions#ALL} 建出，也就是**只有带权限码的接口才有行**，
 * 而本表里的端点在那张表里**一行都没有**。它是人工备注，不参与判定；
 * 本类才是判定用的登记（见 {@code docs/decisions/0052}）。
 */
public final class AuthPublicEndpoints {

    private AuthPublicEndpoints() {
    }

    /** 形状：{@code 方法 + 空格 + 路径}，路径与框架映射表里的写法完全一致（含路径变量占位）。 */
    public static final Pattern SHAPE = Pattern.compile("^(GET|POST|PUT|DELETE|PATCH) /[A-Za-z0-9/_{}.-]*$");

    /** 健康检查：探活必须在无令牌时可用。 */
    public static final String HEALTH = "GET /api/health";

    /** 登录 / 刷新 / 登出：拿到第一个令牌之前就要能调，否则是死锁。 */
    public static final String LOGIN = "POST /api/auth/login";
    public static final String REFRESH = "POST /api/auth/refresh";

    /**
     * 登出**一个**会话：撤的是请求体里指明的那个刷新令牌，因此不需要身份
     * （与 {@code /logout-all} 是两件事，见 {@code docs/decisions/0047}）。
     */
    public static final String LOGOUT = "POST /api/auth/logout";

    /** 撤掉**当前用户**名下的全部会话：作用域由上下文定死、不接受参数 ⇒ 要令牌、不要权限码。 */
    public static final String LOGOUT_ALL = "POST /api/auth/logout-all";

    /** 自助三件套：任何登录用户都应能对自己做，不需要权限码。 */
    public static final String ME = "GET /api/auth/me";
    public static final String CHANGE_PASSWORD = "PUT /api/auth/password";

    /**
     * 上报（写侧公开、查侧鉴权）：登录页出错时也必须能上报，
     * 否则"登录不上"这件事本身没有任何记录可查。查询全在 {@code GET} 上，另有权限码。
     */
    public static final String LOG_REPORT = "POST /api/logs/client";
    public static final String EVENT_REPORT = "POST /api/events/client";

    /** 当前全部"不参与权限判定"的端点。加端点只有两条路：写注解，或登记到这里。 */
    public static final List<String> ALL = List.of(
            HEALTH,
            LOGIN, REFRESH, LOGOUT, LOGOUT_ALL, ME, CHANGE_PASSWORD,
            LOG_REPORT, EVENT_REPORT);

}
