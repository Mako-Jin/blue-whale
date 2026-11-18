package com.bluewhale.job.scheduler;

/**
 * 任务执行超时异常：任务在配置的时限内未完成，调度侧已请求中断执行线程。
 *
 * <p>超时不重试——单次执行已经超时，重试只会放大对端压力并继续占用调度线程；
 * 调度引擎捕获本异常后直接落失败终态，并使用超时口径发送告警。</p>
 *
 * @author BlueWhale
 */
public class JobTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造超时异常
     *
     * @param message 超时描述（含时限）
     */
    public JobTimeoutException(String message) {
        super(message);
    }
}
