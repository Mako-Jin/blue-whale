package com.bluewhale.sts.audit.recorder;

import com.bluewhale.sts.audit.auditlog.AuditResult;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.web.advice.ResponseOutcome;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 审计拦截器 —— 把"这次请求被谁要求做了什么、结果如何"写成一条记录。
 *
 * <p><b>它必须注册在鉴权拦截器之前（order 更小）。</b>这不是风格问题，它决定
 * "越权尝试"能不能被记下来：Spring 只会对**已经成功通过 {@code preHandle} 的拦截器**
 * 调用 {@code afterCompletion}。鉴权拦截器在无权限时抛异常，此时若有拦截器排在它后面，
 * 那个拦截器的 {@code afterCompletion} 永远不会被调用 —— 于是"某个人试图删除用户但被拦住了"
 * 这条最值得审计的记录会整整齐齐地消失，而且没有任何报错。
 * 排在前面则相反：鉴权失败时，本拦截器的 {@code afterCompletion} 照常执行。
 *
 * <p><b>为什么在 {@code afterCompletion} 而不是 {@code preHandle} 落库：</b>
 * 审计要记的是"发生了什么"，而 {@code preHandle} 时只知道自己准备做什么。
 * 更要紧的是**时间点**：{@code afterCompletion} 运行时业务事务已经提交或回滚完毕，
 * 因此不会出现"事务后来回滚了、审计却记着成功"这种自相矛盾的历史。
 *
 * <p><b>匿名请求不产生审计。</b>审计要落到"人"身上，没有操作人的记录回答不了任何问题；
 * 而公网上的扫描探测会让审计表被"某 IP 试了 200 次登录接口"这类噪声灌满 ——
 * 那是安全监控的活（应由网关/风控承担），混进审计表只会淹没真正的管理操作。
 * 注意"已登录但无权限"与这一条**不同**：那种情况有明确的操作人，必须记。
 */
public class AuditInterceptor implements HandlerInterceptor {

    /**
     * 本次请求的审计状态。用请求属性携带，而不是 ThreadLocal ——
     * 请求属性随请求生命周期回收，没有"忘记清理导致串号"的失效方式。
     */
    private static final String STATE_ATTRIBUTE = AuditInterceptor.class.getName() + ".state";

    /** 与 {@code target_id VARCHAR(64)} 对齐 */
    private static final int MAX_TARGET_ID = 64;

    /** 与 {@code request_uri VARCHAR(512)} 对齐 */
    private static final int MAX_REQUEST_URI = 512;

    /** 与 {@code user_agent VARCHAR(512)} 对齐 */
    private static final int MAX_USER_AGENT = 512;

    /** 与 {@code ip VARCHAR(64)} 对齐 */
    private static final int MAX_IP = 64;

    /** 与 {@code trace_id VARCHAR(64)} 对齐 */
    private static final int MAX_TRACE_ID = 64;

    /** 与 {@code error_message VARCHAR(512)} 对齐 */
    private static final int MAX_ERROR_MESSAGE = 512;

    /**
     * 与前端日志管道串联用的请求头。
     *
     * <p>由前端 {@code common-tools/http} 的 {@code traceIdProvider} 注入
     * （见 ADR-0004 第 6 条）。本域**只读取、不生成**：后端自己造一个 id 没有意义 ——
     * 它串不起任何东西，只会让审计里多一列永远和别处对不上的值。
     */
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final AuditRecorder auditRecorder;

    public AuditInterceptor(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Audited audited = resolveAudited(handlerMethod);
        if (audited == null) {
            return true;
        }
        // 记下起点。afterCompletion 靠这个属性的**存在**判断"这次请求需要记审计"，
        // 因此不标 @Audited 的接口在这里什么也不会留下
        request.setAttribute(STATE_ATTRIBUTE, new State(audited, System.currentTimeMillis()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object raw = request.getAttribute(STATE_ATTRIBUTE);
        if (!(raw instanceof State state)) {
            return;
        }

        AuthContext context = AuthContextHolder.get();
        if (context == null || context.getUserId() == null) {
            return;
        }

        long finishedAt = System.currentTimeMillis();
        Outcome outcome = outcomeOf(request, response, ex);
        AuditRecord record = new AuditRecord(
                state.audited.action(),
                state.audited.resource(),
                truncate(AuditContext.targetOf(request), MAX_TARGET_ID),
                context.getUserId(),
                truncate(context.getUsername(), MAX_TARGET_ID),
                context.getTenantId(),
                outcome.result(),
                outcome.code(),
                outcome.message(),
                request.getMethod(),
                truncate(request.getRequestURI(), MAX_REQUEST_URI),
                truncate(resolveIp(request), MAX_IP),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT),
                truncate(request.getHeader(TRACE_HEADER), MAX_TRACE_ID),
                finishedAt - state.startedAt());

        // 审计写失败绝不允许影响已经准备好的业务响应。
        // 三个理由，按重要性排：① 用户在界面上看到的结果不该因为"旁路的日志"而变；
        // ② 审计失败的原因往往正是"库有问题"，此时若把异常抛出去，会演变成
        //    "库出问题 → 所有写接口都 500"的放大故障；③ 拦截器里抛出的异常在
        //    afterCompletion 阶段已经无法被 GlobalExceptionHandler 翻译，
        //    只会变成一个容器级 500，把真正的原因盖掉。
        try {
            auditRecorder.record(record);
        } catch (Exception writeFailure) {
            auditRecorder.reportFailure(record, writeFailure);
        }
    }

    /**
     * 判定这次请求的结果。
     *
     * <p><b>为什么不只看异常：</b>本仓约定"业务失败也返回 HTTP 200，业务结果只由 code 表达"，
     * 于是"越权被拒""编码重复"这些**没有异常**的失败在 {@code afterCompletion} 里
     * 看起来与成功一模一样。若不处理，审计表会把"有人试图越权"记成"某人成功执行了操作" ——
     * 而这恰好是审计最不能出的错。
     *
     * <p>三条判据按可信度依次使用：
     * <ol>
     *   <li>处理器抛出的异常（异常没被 {@code GlobalExceptionHandler} 接住的情形）；</li>
     *   <li>{@link ResponseOutcome} 留下的失败标记（被接住的情形 —— 绝大多数走这条）；</li>
     *   <li>HTTP 状态码 ≥ 400（容器级错误，如请求在进入 Spring MVC 之前就被拒）。</li>
     * </ol>
     */
    private static Outcome outcomeOf(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        if (ex != null) {
            int code = ex instanceof BusinessException businessException
                    ? businessException.getCode()
                    : ResultCode.SYSTEM_ERROR.getCode();
            return new Outcome(false, code, truncate(ex.getMessage(), MAX_ERROR_MESSAGE));
        }

        ResponseOutcome.Failure failure = ResponseOutcome.failureOf(request);
        if (failure != null) {
            return new Outcome(false, failure.code(), truncate(failure.message(), MAX_ERROR_MESSAGE));
        }

        int status = response.getStatus();
        if (status >= 400) {
            // 状态码本身只有三位，乘 100 让它落进本仓"5 位 = HTTP 3 位 + 业务 2 位"的编码规则里，
            // 免得审计表里混进两种长度的码。细分 00 表示"没有更细的信息"
            return new Outcome(false, status * 100, "HTTP " + status);
        }

        return new Outcome(true, null, null);
    }

    /**
     * 解析 {@link Audited}：方法级优先，类级其次。
     *
     * <p>与 {@code AuthInterceptor} 用同一套查找方式（{@link AnnotatedElementUtils#findMergedAnnotation}
     * 而非 {@code getAnnotation}），这样将来做组合注解（如 {@code @AdminAudited}）时两边一起生效，
     * 不会出现"权限注解能继承、审计注解不能"的半吊子状态。
     */
    private static Audited resolveAudited(HandlerMethod handlerMethod) {
        Audited audited = AnnotatedElementUtils
                .findMergedAnnotation(handlerMethod.getMethod(), Audited.class);
        if (audited == null) {
            audited = AnnotatedElementUtils
                    .findMergedAnnotation(handlerMethod.getBeanType(), Audited.class);
        }
        return audited;
    }

    /**
     * 解析来源 IP。优先取 {@code X-Forwarded-For} 的**第一段**。
     *
     * <p>与日志域、事件域、权限域三处保持完全一致的取法（最靠近客户端的那个才是真实来源）。
     * 需注意该头可被伪造，因此它只用于排查参考，**不作为鉴权或风控依据** ——
     * 审计里出现一个伪造的 IP 不会影响"谁做的"这个判断，因为操作人是服务端从令牌里取出来的。
     */
    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 截断超长文本，截断处留标记。
     *
     * <p>截断而不是丢弃：审计记录的主体（谁、做了什么、结果）绝不因为一个超长的
     * User-Agent 而整条不写。
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…(已截断)";
    }

    /** 每次请求的审计状态：注解 + 起点时间 */
    private record State(Audited audited, long startedAt) {
    }

    /** 结果三元组：是否成功、错误码、错误描述 */
    private record Outcome(boolean success, Integer code, String message) {

        AuditResult result() {
            return success ? AuditResult.SUCCESS : AuditResult.FAILURE;
        }
    }

}
