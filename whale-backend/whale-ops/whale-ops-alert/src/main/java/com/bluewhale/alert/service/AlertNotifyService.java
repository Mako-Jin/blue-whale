package com.bluewhale.alert.service;

import com.bluewhale.alert.po.AlertRecordPo;
import com.bluewhale.alert.po.AlertRulePo;

/**
 * 告警通知服务：按规则配置的通道发送通知。
 *
 * <p>通知失败只记录日志，不向上抛出，避免影响告警记录落库与入站接口返回。</p>
 *
 * @author BlueWhale
 */
public interface AlertNotifyService {

    /**
     * 发送告警通知
     *
     * @param rule   触发的告警规则
     * @param record 已落库的告警记录
     */
    void notify(AlertRulePo rule, AlertRecordPo record);
}
