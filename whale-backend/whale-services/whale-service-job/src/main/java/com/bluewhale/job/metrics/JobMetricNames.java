package com.bluewhale.job.metrics;

/**
 * 定时任务指标名与标签键常量。
 *
 * <p>集中定义避免埋点与看板/告警规则之间出现字符串漂移。</p>
 *
 * @author BlueWhale
 */
public final class JobMetricNames {

    /** 执行结果计数（按任务与状态） */
    public static final String EXECUTE_TOTAL = "job_execute_total";

    /** 执行耗时（含重试，单位秒） */
    public static final String EXECUTE_DURATION = "job_execute_duration_seconds";

    /** 调度延迟：实际触发时间与 cron 预期时间之差（单位秒） */
    public static final String SCHEDULE_DELAY = "job_schedule_delay_seconds";

    /** 调度线程池当前线程数 */
    public static final String POOL_SIZE = "job_schedule_pool_size";

    /** 调度线程池活跃线程数 */
    public static final String POOL_ACTIVE = "job_schedule_pool_active";

    /** 调度线程池排队任务数 */
    public static final String POOL_QUEUE_SIZE = "job_schedule_pool_queue_size";

    /** 本实例已注册调度数 */
    public static final String SCHEDULED_JOBS = "job_scheduled_jobs";

    /** 在线执行器总数 */
    public static final String EXECUTOR_ONLINE = "job_executor_online";

    /** 执行器注册/注销次数 */
    public static final String EXECUTOR_REGISTER_TOTAL = "job_executor_register_total";

    /** 注册表与 Nacos 不一致导致回退的次数 */
    public static final String DISPATCH_MISMATCH_TOTAL = "job_executor_dispatch_mismatch_total";

    /** 调度失效广播发布结果 */
    public static final String BROADCAST_TOTAL = "job_broadcast_total";

    /** 调度失效广播本地应用数 */
    public static final String BROADCAST_RECEIVE_TOTAL = "job_broadcast_receive_total";

    /** 调度失效广播降级次数 */
    public static final String BROADCAST_FALLBACK_TOTAL = "job_broadcast_fallback_total";

    /** 标签键：任务 */
    public static final String TAG_JOB = "job";

    /** 标签键：状态 */
    public static final String TAG_STATUS = "status";

    /** 标签键：动作 */
    public static final String TAG_ACTION = "action";

    /** 标签键：结果 */
    public static final String TAG_RESULT = "result";

    /** 标签键：原因 */
    public static final String TAG_REASON = "reason";

    /** 状态值：成功 */
    public static final String STATUS_SUCCESS = "success";

    /** 状态值：失败 */
    public static final String STATUS_FAILURE = "failure";

    /** 动作值：注册 */
    public static final String ACTION_REGISTER = "register";

    /** 动作值：注销 */
    public static final String ACTION_DEREGISTER = "deregister";

    /** 结果值：成功 */
    public static final String RESULT_SUCCESS = "success";

    /** 结果值：失败 */
    public static final String RESULT_FAILURE = "failure";

    /** 结果值：已应用 */
    public static final String RESULT_APPLIED = "applied";

    /** 原因值：Nacos 无实例 */
    public static final String REASON_NOT_IN_NACOS = "not-in-nacos";

    /** 原因值：注册表与 Nacos 无交集 */
    public static final String REASON_MISMATCH = "mismatch";

    /**
     * 工具类禁止实例化
     */
    private JobMetricNames() {
    }
}
