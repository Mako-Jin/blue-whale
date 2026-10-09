package com.bluewhale.sts.common.basic.exception;

import com.bluewhale.sts.common.basic.enums.ResultCode;
import lombok.Getter;

/**
 * 业务异常 —— 表示"这条请求在业务规则上被拒绝"，而不是程序出了 bug。
 *
 * <p>它会被 {@code GlobalExceptionHandler} 统一翻译成响应体，因此：
 * <ul>
 *   <li><b>不要</b>用它包裹系统级故障（数据库连不上、NPE 等），那类应交给兜底处理并记 ERROR 日志；</li>
 *   <li><b>不要</b>靠它做流程控制（例如用异常跳出循环），异常构造要采集调用栈，代价远高于普通分支。</li>
 * </ul>
 *
 * <p>默认不采集调用栈（{@code writableStackTrace = false}）：业务异常在正常运营中会高频出现
 * （比如用户反复提交非法表单），采集栈没有价值却会让日志体积和 CPU 开销成倍上升。
 * 需要排查时再指定第三个构造参数打开。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，取值见 {@link ResultCode} */
    private final int code;

    public BusinessException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message, null);
    }

    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        this(resultCode.getCode(), message, cause);
    }

    public BusinessException(int code, String message) {
        this(code, message, null);
    }

    /** 所有构造最终汇聚到这里，确保"不采集调用栈"这一约定不会被某个分支漏掉 */
    private BusinessException(int code, String message, Throwable cause) {
        super(message, cause, false, false);
        this.code = code;
    }

}
