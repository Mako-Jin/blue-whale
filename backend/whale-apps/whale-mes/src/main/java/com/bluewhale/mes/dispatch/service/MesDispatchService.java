package com.bluewhale.mes.dispatch.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dispatch.dto.MesDispatchRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskQueryRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskSplitRequest;
import com.bluewhale.mes.dispatch.dto.MesWorkOrderReceiveRequest;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import com.bluewhale.mes.vo.MesWorkOrderVO;

import java.util.List;

/**
 * MES 调度派工应用服务
 *
 * @author BlueWhale
 */
public interface MesDispatchService {

    /**
     * 接收 ERP 生产工单（按 ERP 单号幂等）
     *
     * @param request 下发请求
     * @return 工单视图
     */
    MesWorkOrderVO receive(MesWorkOrderReceiveRequest request);

    /**
     * 拆分工单为工序任务
     *
     * @param workOrderId 工单 ID
     * @param request     拆分请求，明细为空时按工艺路线自动拆分
     * @return 拆分后的任务列表
     */
    List<MesWorkOrderTaskVO> split(Long workOrderId, MesTaskSplitRequest request);

    /**
     * 批量派工
     *
     * @param request 派工请求
     * @return 派工单号
     */
    String dispatch(MesDispatchRequest request);

    /**
     * 撤回派工单（仅未开工任务可撤回）
     *
     * @param dispatchOrderId 派工单 ID
     */
    void withdraw(Long dispatchOrderId);

    /**
     * 分页查询任务
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesWorkOrderTaskVO> pageTasks(MesTaskQueryRequest request);

    /**
     * 生产看板汇总
     *
     * @param lineId 产线 ID，可为空
     * @param teamId 班组 ID，可为空
     * @return 看板数据
     */
    MesBoardVO board(Long lineId, Long teamId);
}
