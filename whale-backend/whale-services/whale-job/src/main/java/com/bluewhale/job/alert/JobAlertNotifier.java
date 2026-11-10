package com.bluewhale.job.alert;

import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;

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
    void notifyFailure(JobInfo job, JobLog jobLog);
}
