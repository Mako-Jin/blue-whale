package com.bluewhale.sts.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 把"这次请求最终的失败码"留在请求上，供**事后**读取。
 *
 * <p><b>为什么需要这么一个通道：</b>本仓有一条重要约定 ——
 * <b>业务失败也返回 HTTP 200，业务结果只由响应体里的 {@code code} 表达</b>
 * （见 {@code docs/decisions/0005} 第 7 条）。这条约定对调用方是好事，
 * 却让"事后判断这次请求成功没有"变得困难：拦截器的 {@code afterCompletion}
 * 看到的是一个 200 的空响应，**从状态码上完全分不出"删除了一个用户"和"越权被拒"**。
 *
 * <p>审计恰恰最需要这个区分 —— "谁尝试越权"比"谁成功做了件事"更值得记录。
 * 所以由唯一知道结果的 {@link GlobalExceptionHandler} 在此留一个标记，
 * 由需要它的人（当前是审计拦截器）在请求结束前读走。
 *
 * <p><b>本类不认识审计域，这是有意的。</b>{@code sts-common-web} 属于技术底座，
 * 不能反向依赖任何业务域。这里只提供一个"请求属性 + 一个只读访问器"的中立约定：
 * 谁想读谁读，没人读也不影响任何事。若把它换成"common-web 直接调审计"，
 * 技术底座就被业务语义绑架了，而这个文件的存在意义正是避免那一天。
 *
 * <p>请求属性随请求生命周期自然回收，不需要清理 —— 与 ThreadLocal 相比，
 * 它没有"忘记清理导致串号"这种失效方式。
 */
public final class ResponseOutcome {

    /**
     * 失败详情。
     *
     * @param code    业务错误码，0 表示失败标记从未被写下（即成功）
     * @param message 对外返回的那句提示，**不是**内部异常信息 ——
     *                兜底处理器刻意不把异常信息透给调用方，这里也不该另开一个口子
     */
    public record Failure(int code, String message) {
    }

    /**
     * 请求属性名。用类全名做前缀：请求属性是全局命名空间，短名字撞车的表现是
     * "审计里的失败原因变成了别人的值"—— 不报错，只记错。
     */
    public static final String ATTRIBUTE = ResponseOutcome.class.getName() + ".failure";

    private ResponseOutcome() {
    }

    /**
     * 标记这次请求以失败告终。由全局异常处理器调用。
     *
     * <p>允许 {@code request} 为 null（异常处理器在极少数容器场景下可能拿不到请求），
     * 此时静默跳过 —— 少一条审计比让异常处理自身失败要好得多。
     */
    public static void markFailure(HttpServletRequest request, int code, String message) {
        if (request == null) {
            return;
        }
        request.setAttribute(ATTRIBUTE, new Failure(code, message));
    }

    /** 读取失败标记，未标记（即请求成功）时返回 {@code null} */
    public static Failure failureOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof Failure failure ? failure : null;
    }

}
