package com.bluewhale.sts.components.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计记录的补充入口 —— 业务代码用它回答"这次动的是哪一条"。
 *
 * <p><b>为什么需要它：</b>拦截器知道"谁"（认证上下文）、"做了什么"（{@link Audited} 注解）、
 * "什么时候"（服务端时间），唯独不知道"对象是谁" —— 那要等业务代码跑完才知道
 * （新建时主键是自增出来的，调用前根本不存在）。所以留一个只有业务代码能填的孔。
 *
 * <p><b>为什么用请求属性而不是 ThreadLocal：</b>本仓已经吃过 ThreadLocal 的亏
 * （见 {@code AuthContextHolder} 的注释：Tomcat 线程复用，忘了清理就会"偶发串号"）。
 * 请求属性随请求生命周期自然回收，**没有"忘记清理"这种失效方式** ——
 * 对一个只是"捎带一个字符串"的通道来说，这才是合适的代价。
 *
 * <p><b>没有请求上下文时静默无操作</b>：这个方法会在 Service 与 Controller 里被调用，
 * 而它们也可能被定时任务、测试或别的非 Web 入口调用。此时"记不了"是正常的，
 * 不该让业务代码为它加判断，更不该抛异常 —— 审计永远不能成为业务失败的原因。
 */
public final class AuditContext {

    /**
     * 请求属性名。
     *
     * <p>用类全名做前缀而不是 {@code "auditTarget"} 这类短语：请求属性是个**全局命名空间**，
     * 框架、其它过滤器、其它组件都可能往里放东西，短名字撞车的概率不低，
     * 而撞车的表现是"审计里的 target 变成了别人的值"—— 不会报错，只会记错。
     */
    public static final String TARGET_ATTRIBUTE = AuditContext.class.getName() + ".target";

    private AuditContext() {
    }

    /**
     * 标记本次操作的对象（主键或业务编码）。
     *
     * <p>传任意对象都可以，内部取 {@link Object#toString()}。允许传 {@code null}
     * 只是为了让调用点不必写判断（比如"更新成功但返回值里没有 id"这种边角情形）。
     */
    public static void target(Object target) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        request.setAttribute(TARGET_ATTRIBUTE, target == null ? null : String.valueOf(target));
    }

    /**
     * 读取当前请求上标记的对象，未标记时返回 {@code null}。由审计拦截器调用。
     *
     * <p>这里直接吃 {@link HttpServletRequest} 而不是走 {@link RequestContextHolder}：
     * 拦截器本来就握着请求对象，绕一圈 ThreadLocal 只会多一个可能为空的环节。
     */
    public static String targetOf(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(TARGET_ATTRIBUTE);
        return value == null ? null : String.valueOf(value);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
