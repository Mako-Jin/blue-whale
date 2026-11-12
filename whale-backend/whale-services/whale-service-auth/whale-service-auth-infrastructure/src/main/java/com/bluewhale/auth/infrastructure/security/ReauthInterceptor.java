package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.service.ReauthAppService;
import com.bluewhale.auth.application.security.ReauthRequiredException;
import com.bluewhale.auth.sdk.annotation.RequireReauth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * {@link RequireReauth} 二次认证拦截器（Wave 6.22）
 *
 * <p>在 {@link PermissionInterceptor} 之后执行：命中注解或路径规则 → 校验请求头
 * {@code X-Reauth-Token} 是否为当前用户的有效 step-up 凭证；无效时抛
 * {@link ReauthRequiredException}（HTTP 428 + 业务码 42801），由前端拦截器引导验密后重放。</p>
 *
 * <p>注解解析顺序：方法 → 声明类 → 实现的接口方法（兼容 API-first 写法，Spring 的
 * {@code getMethodAnnotation} 不跨接口）。另支持 {@code whale.auth.reauth.protected-rules}
 * 配置「METHOD 路径」清单兜底（<b>默认空</b>），用于不便改源码或需运维侧临时收紧的场景。</p>
 *
 * <p><b>自助改密不在默认清单内（Wave 6.32）</b>：{@code PUT /auth/v1/password} 的请求体
 * 已带原口令并由服务端逐字校验，本身即 step-up 证明。若再要求凭证会与「待改密」状态互锁：
 * 本拦截器返回 428 要求先验密，而 {@code POST /auth/v1/reauth} 对 {@code must_change_password}
 * 与口令过期的用户直接 403「请先修改口令」——这类用户既改不了口令也拿不到凭证。</p>
 *
 * @author BlueWhale
 */
@Component
public class ReauthInterceptor implements HandlerInterceptor {

    /** step-up 凭证请求头 */
    public static final String HEADER_NAME = "X-Reauth-Token";

    /** Ant 路径匹配器（{@code *} 单段、{@code **} 任意段） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 二次认证应用服务 */
    private final ReauthAppService reauthAppService;

    /** 路径规则清单，形如 {@code PUT /auth/v1/password} */
    private final List<String> protectedRules;

    /**
     * 构造拦截器
     *
     * @param reauthAppService 二次认证应用服务
     * @param rules 路径规则（默认空，自助改密刻意不入默认清单，见类注释）
     */
    public ReauthInterceptor(ReauthAppService reauthAppService,
                             @Value("${whale.auth.reauth.protected-rules:}") List<String> rules) {
        this.reauthAppService = reauthAppService;
        // `${…:}` 的空默认值会被 Spring 解析成含空串的列表，过滤掉空白项，避免规则表里混进 "" 匹配
        this.protectedRules = rules == null ? List.of()
                : rules.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!isProtected(request, handlerMethod)) {
            return true;
        }
        if (reauthAppService.verify(request.getHeader(HEADER_NAME))) {
            return true;
        }
        throw new ReauthRequiredException();
    }

    /**
     * 判断当前请求是否受二次认证约束（注解优先，其次路径规则）
     *
     * @param request 当前请求
     * @param handlerMethod 处理器方法
     * @return 受约束返回 {@code true}
     */
    private boolean isProtected(HttpServletRequest request, HandlerMethod handlerMethod) {
        if (resolveAnnotation(handlerMethod) != null) {
            return true;
        }
        String rawMethod = request.getMethod();
        String method = rawMethod == null ? "" : rawMethod.toUpperCase(Locale.ROOT);
        String uri = request.getRequestURI();
        for (String rule : protectedRules) {
            if (!StringUtils.hasText(rule)) {
                continue;
            }
            String trimmed = rule.trim();
            int space = trimmed.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String ruleMethod = trimmed.substring(0, space).toUpperCase(Locale.ROOT);
            String rulePath = trimmed.substring(space + 1).trim();
            if (ruleMethod.equals(method) && pathMatcher.match(rulePath, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析二次认证注解：方法 → 声明类 → 实现的接口方法
     *
     * @param handlerMethod 当前处理器方法
     * @return 命中的注解，未标注返回 {@code null}
     */
    private RequireReauth resolveAnnotation(HandlerMethod handlerMethod) {
        RequireReauth annotation = handlerMethod.getMethodAnnotation(RequireReauth.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = handlerMethod.getBeanType().getAnnotation(RequireReauth.class);
        if (annotation != null) {
            return annotation;
        }
        Method method = handlerMethod.getMethod();
        for (Class<?> iface : handlerMethod.getBeanType().getInterfaces()) {
            try {
                RequireReauth onInterface = iface
                        .getMethod(method.getName(), method.getParameterTypes())
                        .getAnnotation(RequireReauth.class);
                if (onInterface != null) {
                    return onInterface;
                }
            } catch (NoSuchMethodException ignored) {
                // 接口未声明同名方法：跳过
            }
        }
        return null;
    }
}
