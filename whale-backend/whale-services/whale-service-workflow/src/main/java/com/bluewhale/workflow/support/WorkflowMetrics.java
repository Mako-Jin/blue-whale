package com.bluewhale.workflow.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 工作流业务指标。
 *
 * <p>只登记 HTTP 指标看不出来的业务动作：实例生命周期、任务动作、定义发布、
 * 催办频控与编码冲突。指标名分别为 {@code workflow_instance_action_total}、
 * {@code workflow_task_action_total}、{@code workflow_definition_publish_total}、
 * {@code workflow_urge_total} 与 {@code workflow_definition_code_conflict_total}。</p>
 *
 * @author BlueWhale
 */
@Component
public class WorkflowMetrics {

    /** 实例动作：发起 */
    public static final String INSTANCE_START = "START";

    /** 实例动作：撤销 */
    public static final String INSTANCE_CANCEL = "CANCEL";

    /** 任务动作：同意 */
    public static final String TASK_APPROVE = "APPROVE";

    /** 任务动作：驳回 */
    public static final String TASK_REJECT = "REJECT";

    /** 任务动作：转办 */
    public static final String TASK_TRANSFER = "TRANSFER";

    /** 任务动作：委派 */
    public static final String TASK_DELEGATE = "DELEGATE";

    /** 任务动作：委派处理完成 */
    public static final String TASK_RESOLVE = "RESOLVE";

    /** 任务动作：回退 */
    public static final String TASK_ROLLBACK = "ROLLBACK";

    /** 任务动作：加签 */
    public static final String TASK_ADD_SIGN = "ADD_SIGN";

    /** 任务动作：内网完成 */
    public static final String TASK_COMPLETE = "COMPLETE";

    /** 催办结果：已提交 */
    public static final String URGE_SUBMITTED = "SUBMITTED";

    /** 催办结果：频控拒绝 */
    public static final String URGE_THROTTLED = "THROTTLED";

    /** 超时提醒结果：已投递 */
    public static final String REMINDER_SENT = "SENT";

    /** 超时提醒结果：跳过（账号解析不到或配置关闭） */
    public static final String REMINDER_SKIPPED = "SKIPPED";

    /** 超时提醒结果：投递失败 */
    public static final String REMINDER_FAILED = "FAILED";

    /** 指标注册中心 */
    private final MeterRegistry registry;

    /**
     * 注册业务指标。
     *
     * @param registry Micrometer 注册中心
     */
    public WorkflowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次实例生命周期动作。
     *
     * @param action 动作（{@link #INSTANCE_START} / {@link #INSTANCE_CANCEL}）
     */
    public void recordInstanceAction(String action) {
        Counter.builder("workflow.instance.action")
                .description("流程实例生命周期动作次数")
                .tag("action", action)
                .register(registry)
                .increment();
    }

    /**
     * 记录一次任务动作。
     *
     * @param action 动作（{@code APPROVE} / {@code REJECT} / {@code TRANSFER} 等）
     */
    public void recordTaskAction(String action) {
        Counter.builder("workflow.task.action")
                .description("待办任务动作次数")
                .tag("action", action)
                .register(registry)
                .increment();
    }

    /**
     * 记录一次流程定义发布。
     */
    public void recordDefinitionPublish() {
        Counter.builder("workflow.definition.publish")
                .description("流程定义发布次数")
                .register(registry)
                .increment();
    }

    /**
     * 记录一次催办结果。
     *
     * @param result 结果（{@link #URGE_SUBMITTED} / {@link #URGE_THROTTLED}）
     */
    public void recordUrge(String result) {
        Counter.builder("workflow.urge")
                .description("流程催办结果次数")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * 记录一次流程编码并发冲突。
     */
    public void recordCodeConflict() {
        Counter.builder("workflow.definition.code.conflict")
                .description("流程编码唯一键并发冲突次数")
                .register(registry)
                .increment();
    }

    /**
     * 记录一轮超时扫描命中的待办数。
     *
     * @param count 命中的待办数（0 不记录，避免空转产生噪声）
     */
    public void recordTaskOverdue(int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("workflow.task.overdue")
                .description("超时扫描命中的待办任务数")
                .register(registry)
                .increment(count);
    }

    /**
     * 记录一次超时提醒结果。
     *
     * @param result 结果（{@link #REMINDER_SENT} / {@link #REMINDER_SKIPPED} / {@link #REMINDER_FAILED}）
     */
    public void recordOverdueReminder(String result) {
        Counter.builder("workflow.task.overdue.reminder")
                .description("超时待办提醒结果次数")
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
