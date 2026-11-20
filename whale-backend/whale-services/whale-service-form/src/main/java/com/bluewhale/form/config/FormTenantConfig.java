package com.bluewhale.form.config;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.form.support.FormRoleContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 表单服务租户上下文强制校验。
 *
 * <p>{@code TenantContextFilter} 仅在 {@code X-Tenant-Id} 存在时可解析出租户，头缺失时租户上下文为空，
 * 此时 MyBatis-Plus 租户插件不加任何租户条件，定义/表定义等接口会退化为跨租户读写。
 * 故对 {@code /form/**} 统一强制要求租户上下文：缺失或非法直接拒绝，不再依赖各控制器自行调用
 * {@code TenantResolver}。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class FormTenantConfig implements WebMvcConfigurer {

    /** 需要租户上下文的路径模式 */
    private static final String TENANT_PATH_PATTERN = "/form/**";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantGuardInterceptor()).addPathPatterns(TENANT_PATH_PATTERN);
    }

    /**
     * 租户与角色守卫拦截器：租户上下文缺失即拒绝请求；角色头写入线程上下文供字段级权限使用
     */
    static final class TenantGuardInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (TenantContext.get() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "form.common.tenant-context-missing", "缺少租户上下文");
            }
            FormRoleContext.set(WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.USER_ROLES)));
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                    Exception ex) {
            FormRoleContext.clear();
        }
    }
}
