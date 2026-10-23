package com.bluewhale.job.scheduler;

/**
 * 定时任务执行器 SPI。
 *
 * <p>实现类须为 Spring Bean，其 Bean 名称即 {@code job_info.handler_bean}；
 * 调度引擎按该名称从容器查找实现并调用 {@link #executeWithContext(JobExecutionContext)}。
 * 返回值作为执行结果摘要写入 {@code job_log.result}，异常信息写入 {@code job_log.error_msg}。</p>
 *
 * <p>兼容性：只需实现 {@link #execute(String)}；带上下文的方法提供默认实现，
 * 默认退化为不感知分片/重试次数，既有执行器无需改动。</p>
 *
 * @author BlueWhale
 */
public interface JobHandler {

    /**
     * 执行任务
     *
     * @param param 执行器参数（来自 {@code job_info.handler_param}，可为空）
     * @return 执行结果摘要
     * @throws Exception 执行失败时抛出，由调度引擎捕获并记入执行日志
     */
    String execute(String param) throws Exception;

    /**
     * 带执行上下文执行任务：默认退化为 {@link #execute(String)}，忽略分片与重试信息。
     *
     * @param context 执行上下文（含任务标识、参数、分片序号/总数与已重试次数）
     * @return 执行结果摘要
     * @throws Exception 执行失败时抛出，由调度引擎捕获并记入执行日志
     */
    default String executeWithContext(JobExecutionContext context) throws Exception {
        return execute(context.getParam());
    }
}
