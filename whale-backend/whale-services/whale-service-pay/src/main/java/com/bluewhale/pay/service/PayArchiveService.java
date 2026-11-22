package com.bluewhale.pay.service;

/**
 * 支付数据归档服务。
 *
 * @author BlueWhale
 */
public interface PayArchiveService {

    /**
     * 执行一轮归档（定时任务入口）。
     *
     * <p>支付流水与成功通知分别按保留天数搬迁到历史表；任一阶段失败只记录日志，
     * 不阻断另一阶段，数据留待下次重跑。</p>
     *
     * @return 本轮搬迁总行数
     */
    int archive();
}
