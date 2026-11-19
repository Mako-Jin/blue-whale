package com.bluewhale.message.service;

import com.bluewhale.message.dto.MessageDashboardQueryRequest;
import com.bluewhale.message.vo.MessageDashboardVO;

/**
 * 投递看板服务：管理端按时间窗聚合投递质量指标。
 *
 * @author BlueWhale
 */
public interface MessageDashboardService {

    /**
     * 聚合投递看板数据。
     *
     * @param request 查询条件（时间窗）
     * @return 看板视图
     */
    MessageDashboardVO dashboard(MessageDashboardQueryRequest request);
}
