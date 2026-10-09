package com.bluewhale.sts.common.web.advice;

import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理 —— 把散落在各处的异常收敛成统一的响应体。
 *
 * <p>本类通过 {@code WebAutoConfiguration} 注册，业务模块无需手动 {@code @Import}：
 * 只要依赖 {@code sts-common-web}，异常处理即自动生效。
 *
 * <p><b>日志级别是有意区分的</b>：
 * <ul>
 *   <li>业务异常与参数错误记 WARN —— 它们是"预期的拒绝"，不是故障，不该触发告警；</li>
 *   <li>未预期异常记 ERROR 并带完整堆栈 —— 这才是需要人介入的信号。</li>
 * </ul>
 * 若两者都用 ERROR，告警会被正常业务拒绝淹没，最终没人再看告警。
 *
 * <p><b>参数类异常单独成套处理，而不是交给兜底：</b>兜底的文案是"系统内部错误"，
 * 但参数错误的责任在调用方，回一个 5 万号段的码会让前端把它当服务端故障去重试、
 * 让监控把它算进可用性。错误码分错类，比不给错误码更麻烦。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务规则拒绝。
     *
     * <p>注意这里<b>不打印堆栈</b>：{@link BusinessException} 构造时已关闭栈采集，
     * 且这类异常高频发生，打印堆栈只会让日志成本失控。
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常被拒绝 | code={} | message={}", ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 请求体 / 表单参数校验失败。
     *
     * <p>Spring 6 中 {@code MethodArgumentNotValidException} 继承自 {@link BindException}，
     * 因此这一个处理器同时覆盖 {@code @RequestBody} 与 {@code @ModelAttribute} 两种入参。
     *
     * <p>回传**字段级**信息（{@code 字段名: 原因}）：只回一句"参数错误"，
     * 调用方得靠猜是哪一项不合法，联调成本会成倍上升。
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | {}", detail);
        return R.fail(ResultCode.BAD_REQUEST, detail);
    }

    /**
     * 方法级参数校验失败（{@code @Validated} + {@code @RequestParam} 等）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | {}", detail);
        return R.fail(ResultCode.BAD_REQUEST, detail);
    }

    /**
     * 请求体无法解析（JSON 语法错误、类型不匹配）。
     *
     * <p><b>不回显解析器给出的原始信息</b>：它会带出内部类名与完整字段路径，
     * 相当于把实体的结构免费告诉调用方。只回一句可操作的提示。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败 | message={}", ex.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, "请求体格式不正确，请检查 JSON 语法与字段类型");
    }

    /**
     * 路径变量 / 查询参数类型不匹配，如 {@code ?page=abc}。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String detail = "参数 " + ex.getName() + " 类型不正确";
        log.warn("参数类型不匹配 | {}", detail);
        return R.fail(ResultCode.BAD_REQUEST, detail);
    }

    /**
     * 参数异常 —— 通常来自断言失败或非法的入参转换。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("参数异常 | message={}", ex.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 兜底处理。<b>必须最后匹配</b>——Spring 按异常类型的具体程度选择处理器，
     * 本方法作为最泛化的类型永远排在最后，不会抢走上面几个的活。
     *
     * <p>对外只回固定文案，<b>不把异常信息透给调用方</b>：堆栈里常含 SQL 片段、
     * 文件路径、内部主机名，直接回显等于免费送出一份内网结构图。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpectedException(Exception ex) {
        log.error("未预期异常 | message={}", ex.getMessage(), ex);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

}
