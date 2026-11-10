package com.bluewhale.erp.qc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.qc.application.command.CalibrationPlanSaveCommand;
import com.bluewhale.erp.qc.application.command.CalibrationRecordCommand;
import com.bluewhale.erp.qc.application.command.MeasuringToolSaveCommand;
import com.bluewhale.erp.qc.application.service.MetrologyAppService;
import com.bluewhale.erp.qc.application.view.CalibrationPlanView;
import com.bluewhale.erp.qc.application.view.CalibrationRecordView;
import com.bluewhale.erp.qc.application.view.MeasuringToolView;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计量器具接口：量具台账、校准计划与校准记录。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/qc/metrology")
@RequiredArgsConstructor
public class QcMetrologyController {

    private final MetrologyAppService metrologyAppService;

    /**
     * 量具分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 量具分页
     */
    @GetMapping("/tool/page")
    @RequirePermission("sys:erp:qc:metrology:list")
    public Result<PageResult<MeasuringToolView>> pageTools(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(metrologyAppService.pageTools(query));
    }

    /**
     * 新增量具。
     *
     * @param command 保存命令
     * @return 量具
     */
    @PostMapping("/tool")
    @RequirePermission("sys:erp:qc:metrology:add")
    @AuditLog(operation = "新增计量器具", targetType = "erp_qc_measuring_tool", type = OperateType.CREATE)
    public Result<MeasuringToolView> createTool(@Valid @RequestBody MeasuringToolSaveCommand command) {
        return Result.ok(metrologyAppService.createTool(command));
    }

    /**
     * 编辑量具。
     *
     * @param toolNo 量具编号
     * @param command 保存命令
     * @return 量具
     */
    @PutMapping("/tool/{toolNo}")
    @RequirePermission("sys:erp:qc:metrology:update")
    @AuditLog(operation = "编辑计量器具", targetType = "erp_qc_measuring_tool", type = OperateType.UPDATE)
    public Result<MeasuringToolView> updateTool(@PathVariable String toolNo,
                                                @Valid @RequestBody MeasuringToolSaveCommand command) {
        return Result.ok(metrologyAppService.updateTool(toolNo, command));
    }

    /**
     * 校准计划分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 校准计划分页
     */
    @GetMapping("/plan/page")
    @RequirePermission("sys:erp:qc:metrology:list")
    public Result<PageResult<CalibrationPlanView>> pagePlans(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(metrologyAppService.pagePlans(query));
    }

    /**
     * 新增校准计划。
     *
     * @param command 保存命令
     * @return 校准计划
     */
    @PostMapping("/plan")
    @RequirePermission("sys:erp:qc:metrology:add")
    @AuditLog(operation = "新增校准计划", targetType = "erp_qc_calibration_plan", type = OperateType.CREATE)
    public Result<CalibrationPlanView> createPlan(@Valid @RequestBody CalibrationPlanSaveCommand command) {
        return Result.ok(metrologyAppService.createPlan(command));
    }

    /**
     * 编辑校准计划。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 校准计划
     */
    @PutMapping("/plan/{id}")
    @RequirePermission("sys:erp:qc:metrology:update")
    @AuditLog(operation = "编辑校准计划", targetType = "erp_qc_calibration_plan", type = OperateType.UPDATE)
    public Result<CalibrationPlanView> updatePlan(@PathVariable Long id,
                                                  @Valid @RequestBody CalibrationPlanSaveCommand command) {
        return Result.ok(metrologyAppService.updatePlan(id, command));
    }

    /**
     * 校准记录分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 校准记录分页
     */
    @GetMapping("/record/page")
    @RequirePermission("sys:erp:qc:metrology:list")
    public Result<PageResult<CalibrationRecordView>> pageRecords(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(metrologyAppService.pageRecords(query));
    }

    /**
     * 登记校准记录。
     *
     * @param command 校准命令
     * @return 校准记录
     */
    @PostMapping("/record")
    @RequirePermission("sys:erp:qc:metrology:calibrate")
    @AuditLog(operation = "登记量具校准", targetType = "erp_qc_calibration_record", type = OperateType.CREATE)
    public Result<CalibrationRecordView> calibrate(@Valid @RequestBody CalibrationRecordCommand command) {
        return Result.ok(metrologyAppService.calibrate(command));
    }
}
