package com.bluewhale.alert.service;

import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 告警记录服务
 *
 * @author BlueWhale
 */
public interface AlertRecordService {

    /**
     * 分页查询告警记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<AlertRecordVO> page(AlertRecordQueryRequest request);

    /**
     * 接收 Prometheus Alertmanager 入站告警并落库。
     *
     * <p>每条告警生成一条记录；{@code firing} 且命中启用规则时按规则通道发送通知，
     * {@code resolved} 只落库不通知。通知失败不影响落库。</p>
     *
     * @param payload Alertmanager Webhook 请求体
     * @return 落库条数
     */
    int ingest(PrometheusWebhookPayload payload);
}
