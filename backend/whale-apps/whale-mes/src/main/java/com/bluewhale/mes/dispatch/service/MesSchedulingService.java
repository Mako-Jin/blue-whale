package com.bluewhale.mes.dispatch.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dispatch.dto.MesScheduleActionRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleGenerateRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleQueryRequest;
import com.bluewhale.mes.dispatch.vo.MesScheduleGenerateVO;
import com.bluewhale.mes.dispatch.vo.MesSchedulePlanVO;

/**
 * MES 有限产能排程服务。
 *
 * <p>按工作中心每班产能把待排工序任务分配到排程窗口内的班次；同工作中心同班次不超产能，
 * 窗口内容量不足的剩余数量留在任务上。排程结果先落草稿，锁定后作为执行依据，取消释放时段。</p>
 *
 * @author BlueWhale
 */
public interface MesSchedulingService {

    /**
     * 生成有限产能排程草稿
     *
     * @param request 生成请求
     * @return 生成结果统计
     */
    MesScheduleGenerateVO generate(MesScheduleGenerateRequest request);

    /**
     * 分页查询排程计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSchedulePlanVO> page(MesScheduleQueryRequest request);

    /**
     * 查询排程计划详情（含时段明细）
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    MesSchedulePlanVO detail(Long id);

    /**
     * 锁定排程计划（DRAFT → LOCKED，重复锁定幂等）
     *
     * @param id      计划 ID
     * @param request 动作请求
     * @return 计划视图
     */
    MesSchedulePlanVO lock(Long id, MesScheduleActionRequest request);

    /**
     * 取消排程计划（DRAFT → CANCELLED，释放全部时段）
     *
     * @param id      计划 ID
     * @param request 动作请求
     * @return 计划视图
     */
    MesSchedulePlanVO cancel(Long id, MesScheduleActionRequest request);
}
