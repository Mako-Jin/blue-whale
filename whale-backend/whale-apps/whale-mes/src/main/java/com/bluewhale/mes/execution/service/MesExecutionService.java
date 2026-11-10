package com.bluewhale.mes.execution.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import com.bluewhale.mes.execution.dto.MesExceptionActionRequest;
import com.bluewhale.mes.execution.dto.MesExceptionCreateRequest;
import com.bluewhale.mes.execution.dto.MesMaterialIssueRequest;
import com.bluewhale.mes.execution.dto.MesMaterialReturnRequest;
import com.bluewhale.mes.execution.dto.MesReportQueryRequest;
import com.bluewhale.mes.execution.dto.MesReportRequest;
import com.bluewhale.mes.execution.dto.MesTaskActionRequest;
import com.bluewhale.mes.execution.vo.MesExceptionVO;
import com.bluewhale.mes.execution.vo.MesProductionReportVO;
import com.bluewhale.mes.execution.vo.MesWipVO;

import java.util.List;

/**
 * MES 生产执行应用服务
 *
 * <p>负责任务开工/暂停/恢复/完工/报废、扫码报工、在制品快照、现场异常与领退料事实记录；
 * 不负责库存扣减与条码追溯（M2）。</p>
 *
 * @author BlueWhale
 */
public interface MesExecutionService {

    /**
     * 开工
     *
     * @param taskId  任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    MesWorkOrderTaskVO start(Long taskId, MesTaskActionRequest request);

    /**
     * 暂停
     *
     * @param taskId  任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    MesWorkOrderTaskVO pause(Long taskId, MesTaskActionRequest request);

    /**
     * 恢复
     *
     * @param taskId  任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    MesWorkOrderTaskVO resume(Long taskId, MesTaskActionRequest request);

    /**
     * 完工
     *
     * @param taskId  任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    MesWorkOrderTaskVO complete(Long taskId, MesTaskActionRequest request);

    /**
     * 报废
     *
     * @param taskId  任务 ID
     * @param request 动作请求（数量与原因必填）
     * @return 任务视图
     */
    MesWorkOrderTaskVO scrap(Long taskId, MesTaskActionRequest request);

    /**
     * 扫码报工
     *
     * @param request 报工请求
     * @return 报工记录
     */
    MesProductionReportVO report(MesReportRequest request);

    /**
     * 分页查询报工记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesProductionReportVO> pageReports(MesReportQueryRequest request);

    /**
     * 查询在制品快照
     *
     * @param workOrderId 工单 ID，可为空
     * @return 在制品列表
     */
    List<MesWipVO> wip(Long workOrderId);

    /**
     * 上报现场异常
     *
     * @param request 异常请求
     * @return 异常视图
     */
    MesExceptionVO raiseException(MesExceptionCreateRequest request);

    /**
     * 处理现场异常
     *
     * @param id      异常 ID
     * @param request 处理请求
     * @return 异常视图
     */
    MesExceptionVO handleException(Long id, MesExceptionActionRequest request);

    /**
     * 登记领料/补料
     *
     * @param request 领料请求
     * @return 领料单号
     */
    String issueMaterial(MesMaterialIssueRequest request);

    /**
     * 登记退料
     *
     * @param request 退料请求
     * @return 退料单号
     */
    String returnMaterial(MesMaterialReturnRequest request);
}
