package com.bluewhale.mes.dispatch.service;

/**
 * 工单进度回流服务
 *
 * <p>任务状态变化后按任务汇总刷新工单状态；工单全部任务完成时写入发件箱完成事件。</p>
 *
 * @author BlueWhale
 */
public interface MesTaskProgressService {

    /**
     * 刷新工单状态与完成回传
     *
     * @param workOrderId 工单 ID
     */
    void refreshWorkOrder(Long workOrderId);
}
