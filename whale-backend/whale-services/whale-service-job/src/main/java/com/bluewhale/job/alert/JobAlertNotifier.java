package com.bluewhale.job.alert;

import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;

/**
 * 任务失败告警通知器：任务进入失败终态后由调度引擎调用。
 *
 * <p>实现必须保证异常不外抛，避免影响调度主流程。</p>
 *
 * @author BlueWhale
 */
public interface JobAlertNotifier {

    /**
     * 发送任务失败告警。
     *
     * @param job 任务定义
     * @param jobLog 已回写失败终态的执行日志
     */
    void notifyFailure(JobInfoPo job, JobLogPo jobLog);

    /**
     * 发送任务超时告警：默认与失败告警同口径，实现可按超时区分摘要与级别。
     *
     * @param job 任务定义
     * @param jobLog 已回写失败终态的执行日志
     */
    default void notifyTimeout(JobInfoPo job, JobLogPo jobLog) {
        notifyFailure(job, jobLog);
    }

    /**
     * 发送「连续失败自动暂停」告警：默认不发送，由支持外发的实现覆盖。
     *
     * @param job 被自动暂停的任务定义
     * @param failCount 触发暂停时的连续失败次数
     */
    default void notifyAutoPaused(JobInfoPo job, int failCount) {
    }

    /**
     * 发送「任务已恢复」通知：任务此前处于连续失败状态，本次执行成功后调用一次。
     *
     * <p>默认不发送，由支持外发的实现覆盖；实现同样不得外抛异常。</p>
     *
     * @param job 已恢复的任务定义
     */
    default void notifyRecovered(JobInfoPo job) {
    }
}
