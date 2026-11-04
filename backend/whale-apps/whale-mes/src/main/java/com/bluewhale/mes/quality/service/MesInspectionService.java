package com.bluewhale.mes.quality.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.quality.dto.MesInspectionJudgeRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanQueryRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanSaveRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultSubmitRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskCreateRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskQueryRequest;
import com.bluewhale.mes.quality.vo.MesFirstArticleGateVO;
import com.bluewhale.mes.quality.vo.MesInspectionPlanVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskDetailVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskVO;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;

/**
 * MES 车间检验应用服务
 *
 * <p>覆盖检验计划维护、首件/IPQC 任务、检验结果录入、判定、首件门禁与质量看板。
 * 不变量：检验项未全部录入不可判定；判定 {@code FAILED} 必生成 NCR；首件最近一次未通过时门禁关闭。</p>
 *
 * @author BlueWhale
 */
public interface MesInspectionService {

    /**
     * 保存检验计划（含检验项，整体覆盖）
     *
     * @param request 计划保存请求
     * @return 计划视图
     */
    MesInspectionPlanVO savePlan(MesInspectionPlanSaveRequest request);

    /**
     * 分页查询检验计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesInspectionPlanVO> pagePlans(MesInspectionPlanQueryRequest request);

    /**
     * 查询检验计划详情
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    MesInspectionPlanVO getPlan(Long id);

    /**
     * 创建首件 / IPQC 检验任务
     *
     * @param request 任务创建请求
     * @return 检验任务视图
     */
    MesInspectionTaskVO createTask(MesInspectionTaskCreateRequest request);

    /**
     * 分页查询检验任务
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesInspectionTaskVO> pageTasks(MesInspectionTaskQueryRequest request);

    /**
     * 查询检验任务详情（任务 + 结果 + 不良）
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    MesInspectionTaskDetailVO getTask(Long id);

    /**
     * 录入检验结果与不良明细
     *
     * @param id      任务 ID
     * @param request 录入请求
     * @return 任务详情
     */
    MesInspectionTaskDetailVO submitResults(Long id, MesInspectionResultSubmitRequest request);

    /**
     * 判定检验任务
     *
     * @param id      任务 ID
     * @param request 判定请求
     * @return 检验任务视图
     */
    MesInspectionTaskVO judge(Long id, MesInspectionJudgeRequest request);

    /**
     * 首件门禁查询：最近一次首件检验是否通过
     *
     * @param workOrderId   工单 ID
     * @param operationCode 工序编码（空则不限制）
     * @return 门禁结果
     */
    MesFirstArticleGateVO firstArticleGate(Long workOrderId, String operationCode);

    /**
     * 质量看板聚合
     *
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @param lineId    产线 ID（空则不限制）
     * @return 看板视图
     */
    MesQualityBoardVO board(String boardDate, Long lineId);
}
