package com.bluewhale.common.jobschedule;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 受管定时任务声明：替代裸 {@code @Scheduled}，同时具备「进程内调度」与「被调度中心远程触发」两种能力。
 *
 * <p>行为由配置 {@code whale.schedule.local-enabled} 决定：</p>
 * <ul>
 *   <li>{@code true}（默认）：注册进程内 cron 调度，行为与 {@code @Scheduled(cron = ...)} 等价；</li>
 *   <li>{@code false}：不在本进程调度，仅登记到 {@link RemoteJobRegistry}，
 *       由调度中心（whale-job）经 {@code POST /internal/job/{code}/run} 触发。</li>
 * </ul>
 *
 * <p>因此迁移只需把注解换成本注解、删掉原 {@code @Scheduled}，回滚只需改配置，
 * 不需要改代码。标注的方法必须**无参**，返回值会作为执行摘要写入调度中心的执行流水。</p>
 *
 * @author BlueWhale
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScheduledJob {

    /**
     * 全局唯一任务编码，调度中心的 {@code job_info.job_code} 引用该值。
     *
     * @return 任务编码，建议形如 {@code crm.analytics.snapshot}
     */
    String code();

    /**
     * Cron 表达式，支持 {@code ${}} 占位符。
     *
     * @return cron 表达式
     */
    String cron();

    /**
     * 单次执行预期超时秒数，供调度侧配置参考（超过该时长的任务应调大调度方超时）。
     *
     * @return 超时秒数
     */
    int timeoutSeconds() default 300;
}
