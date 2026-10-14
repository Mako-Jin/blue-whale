package com.bluewhale.sts.auth.authz;

import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器 —— 在进入 Controller 之前，按 {@link RequiresPermission} 判定准入。
 *
 * <p><b>为什么用 HandlerInterceptor 而不是 AOP：</b>
 * 拦截器天然运行在 Web 上下文里，能拿到处理器方法（{@link HandlerMethod}），
 * 且异常会交给 {@code HandlerExceptionResolver} 统一处理 ——
 * 也就是能复用本仓已有的 {@code GlobalExceptionHandler}，不必再造一套错误响应。
 * AOP 则要在各处对齐切点表达式，且脱离 Web 上下文后拿不到同样的信息。
 *
 * <p><b>类级与方法级注解都会生效</b>，方法级优先。用
 * {@link AnnotatedElementUtils#findMergedAnnotation} 而非 {@code getAnnotation}，
 * 是为了兼容组合注解（将来若要做 {@code @AdminOnly} 这类语义注解）。
 *
 * <p><b>未认证与无权限返回不同的码</b>：前者 401（请先登录），后者 403（登录了但不够格）。
 * 两者混成一个码，前端就无法决定是"跳登录页"还是"提示无权限"。
 *
 * <p><b>那条"没有注解就放行"的规则有一份登记表与一条守卫：</b>
 * {@link com.bluewhale.sts.auth.common.AuthPublicEndpoints} 逐条登记全部不参与权限判定的端点，
 * {@code PublicEndpointRegistryTest} 用框架的映射表核对"有注解"与"在登记表里"**恰好二选一**
 * —— 因此"新加端点忘了写注解"会在构建期就红，而不是静默公开（见 {@code docs/decisions/0052}）。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    public AuthInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresPermission annotation = AnnotatedElementUtils
                .findMergedAnnotation(handlerMethod.getMethod(), RequiresPermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils
                    .findMergedAnnotation(handlerMethod.getBeanType(), RequiresPermission.class);
        }
        // 未声明权限的接口 = 公开接口（登录、健康检查等），直接放行
        if (annotation == null) {
            return true;
        }

        AuthContext context = AuthContextHolder.get();
        if (context == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        for (String required : annotation.value()) {
            if (!permissionChecker.hasPermission(context, required)) {
                throw AuthResultCode.NO_PERMISSION.businessException(
                        "缺少权限：" + required);
            }
        }
        return true;
    }

}
