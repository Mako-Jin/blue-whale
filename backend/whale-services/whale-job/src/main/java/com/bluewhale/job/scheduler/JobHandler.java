package com.bluewhale.job.scheduler;

/**
 * 定时任务执行器 SPI。
 *
 * <p>实现类须为 Spring Bean，其 Bean 名称即 {@code job_info.handler_bean}；
 * 调度引擎按该名称从容器查找实现并调用 {@link #execute(String)}。
 * 返回值作为执行结果摘要写入 {@code job_log.result}，异常信息写入 {@code job_log.error_msg}。</p>
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
}
