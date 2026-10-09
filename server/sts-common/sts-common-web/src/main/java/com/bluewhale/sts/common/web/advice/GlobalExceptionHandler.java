package com.bluewhale.sts.common.web.advice;

import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理 —— 把散落在各处的异常收敛成统一的响应体。
 *
 * <p>本类通过 {@code WebAutoConfiguration} 注册，业务模块无需手动 {@code @Import}：
 * 只要依赖 {@code sts-common-web}，异常处理即自动生效。
 *
 * <p><b>日志级别是有意区分的</b>：
 * <ul>
 *   <li>业务异常记 WARN —— 它是"预期的拒绝"，不是故障，不该触发告警；</li>
 *   <li>未预期异常记 ERROR 并带完整堆栈 —— 这才是需要人介入的信号。</li>
 * </ul>
 * 若两者都用 ERROR，告警会被正常业务拒绝淹没，最终没人再看告警。
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
     * 参数异常 —— 通常来自断言失败或非法的入参转换。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("参数异常 | message={}", ex.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 兜底处理。<b>必须最后匹配</b>——Spring 按异常类型的具体程度选择处理器，
     * 本方法作为最泛化的类型永远排在最后，不会抢走上面两个的活。
     *
     * <p>对外只回固定文案，<b>不把异常信息透给调用方</b>：堆栈里常含 SQL 片段、
     * 文件路径、内部主机名，直接回显等于免费送出一份内网结构图。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpectedException(Exception ex) {
        log.error("未预期异常 | message={}", ex.getMessage(), ex);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }

}
