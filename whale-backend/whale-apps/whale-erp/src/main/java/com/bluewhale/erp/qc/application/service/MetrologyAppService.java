package com.bluewhale.erp.qc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.qc.application.command.CalibrationPlanSaveCommand;
import com.bluewhale.erp.qc.application.command.CalibrationRecordCommand;
import com.bluewhale.erp.qc.application.command.MeasuringToolSaveCommand;
import com.bluewhale.erp.qc.application.view.CalibrationPlanView;
import com.bluewhale.erp.qc.application.view.CalibrationRecordView;
import com.bluewhale.erp.qc.application.view.MeasuringToolView;
import com.bluewhale.erp.qc.domain.model.QcQuery;

/**
 * 计量器具与校准应用服务。
 *
 * @author BlueWhale
 */
public interface MetrologyAppService {

    /**
     * 分页查询量具。
     *
     * @param query 查询条件
     * @return 量具分页
     */
    PageResult<MeasuringToolView> pageTools(QcQuery query);

    /**
     * 新增量具。
     *
     * @param command 保存命令
     * @return 量具
     */
    MeasuringToolView createTool(MeasuringToolSaveCommand command);

    /**
     * 编辑量具。
     *
     * @param toolNo 量具编号
     * @param command 保存命令
     * @return 量具
     */
    MeasuringToolView updateTool(String toolNo, MeasuringToolSaveCommand command);

    /**
     * 分页查询校准计划。
     *
     * @param query 查询条件
     * @return 校准计划分页
     */
    PageResult<CalibrationPlanView> pagePlans(QcQuery query);

    /**
     * 新增校准计划。
     *
     * @param command 保存命令
     * @return 校准计划
     */
    CalibrationPlanView createPlan(CalibrationPlanSaveCommand command);

    /**
     * 编辑校准计划。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 校准计划
     */
    CalibrationPlanView updatePlan(Long id, CalibrationPlanSaveCommand command);

    /**
     * 分页查询校准记录。
     *
     * @param query 查询条件
     * @return 校准记录分页
     */
    PageResult<CalibrationRecordView> pageRecords(QcQuery query);

    /**
     * 登记校准记录；合格时回写量具校准日期并顺延计划。
     *
     * @param command 校准命令
     * @return 校准记录
     */
    CalibrationRecordView calibrate(CalibrationRecordCommand command);
}
