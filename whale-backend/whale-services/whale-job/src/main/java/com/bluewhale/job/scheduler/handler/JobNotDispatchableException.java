package com.bluewhale.job.scheduler.handler;

/**
 * 任务无法投递：属于配置或部署问题，重试在短时间内不可能自愈。
 *
 * <p>触发场景：执行器未部署/未注册、执行器在线但不支持该任务编码、任务未配置寻址参数。
 * 调度侧捕获该异常后直接记失败终态，**不做重试等待**——否则每轮触发都会占住调度线程
 * 睡满重试间隔（线程池只有 4 个，几个未部署的服务就能把调度饿死）。</p>
 *
 * @author BlueWhale
 */
public class JobNotDispatchableException extends IllegalStateException {

    /**
     * 构造异常。
     *
     * @param message 失败原因（需可直接给运维定位）
     */
    public JobNotDispatchableException(String message) {
        super(message);
    }
}
