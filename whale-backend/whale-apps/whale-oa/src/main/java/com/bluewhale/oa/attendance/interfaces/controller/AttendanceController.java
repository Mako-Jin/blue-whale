package com.bluewhale.oa.attendance.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceConfigAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceCorrectionAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceExceptionAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceRecordAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrectionQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceDailyQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceExceptionQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecordQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 考勤接口。
 *
 * <p>服务内路径 {@code /oa/attendance/**}，经网关按 {@code Path=/oa/**} 转发；
 * 写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/attendance", version = "1")
@RequiredArgsConstructor
public class AttendanceController {

    private static final String TARGET_RECORD = "oa_att_record";

    private static final String TARGET_CONFIG = "oa_att_config";

    private static final String TARGET_CORRECTION = "oa_att_correction";

    private static final String TARGET_EXCEPTION = "oa_att_exception";

    private final AttendanceRecordAppService recordAppService;

    private final AttendanceDailyAppService dailyAppService;

    private final AttendanceConfigAppService configAppService;

    private final AttendanceCorrectionAppService correctionAppService;

    private final AttendanceExceptionAppService exceptionAppService;

    /**
     * 分页查询打卡记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/records/page")
    @RequirePermission("sys:oa:attendance:record:list")
    public Result<PageResult<AttendanceViews.RecordView>> records(AttendanceRecordQuery query) {
        return Result.ok(recordAppService.page(query));
    }

    /**
     * 员工打卡。
     *
     * @param command 打卡命令
     * @param request 当前请求
     * @return 打卡记录
     */
    @PostMapping("/records")
    @RequirePermission("sys:oa:attendance:record:add")
    @AuditLog(operation = "考勤打卡", type = OperateType.CREATE, targetType = TARGET_RECORD)
    public Result<AttendanceViews.RecordView> punch(@Valid @RequestBody AttendanceCommands.PunchCommand command,
                                                    HttpServletRequest request) {
        return Result.ok(recordAppService.punch(UserContext.requireUserId(request), command));
    }

    /**
     * 分页查询考勤日报。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/daily/page")
    @RequirePermission("sys:oa:attendance:daily:list")
    public Result<PageResult<AttendanceViews.DailyView>> daily(AttendanceDailyQuery query) {
        return Result.ok(dailyAppService.page(query));
    }

    /**
     * 按日期区间重算考勤日报。
     *
     * @param command 重算命令
     * @return 重算条数
     */
    @PostMapping("/daily/recalculate")
    @RequirePermission("sys:oa:attendance:daily:recalculate")
    @AuditLog(operation = "重算考勤日报", type = OperateType.UPDATE, targetType = TARGET_CONFIG)
    public Result<Long> recalculate(@Valid @RequestBody AttendanceCommands.RecalculateCommand command) {
        return Result.ok(dailyAppService.recalculate(command));
    }

    /**
     * 查询全部考勤组。
     *
     * @return 考勤组列表
     */
    @GetMapping("/groups")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<List<AttendanceViews.GroupView>> groups() {
        return Result.ok(configAppService.listGroups());
    }

    /**
     * 新增考勤组。
     *
     * @param command 保存命令
     * @return 考勤组
     */
    @PostMapping("/groups")
    @RequirePermission("sys:oa:attendance:config:add")
    @AuditLog(operation = "新增考勤组", type = OperateType.CREATE, targetType = TARGET_CONFIG)
    public Result<AttendanceViews.GroupView> createGroup(
            @Valid @RequestBody AttendanceCommands.GroupSaveCommand command) {
        return Result.ok(configAppService.saveGroup(null, command));
    }

    /**
     * 更新考勤组。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 考勤组
     */
    @PutMapping("/groups/{id}")
    @RequirePermission("sys:oa:attendance:config:edit")
    @AuditLog(operation = "更新考勤组", type = OperateType.UPDATE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<AttendanceViews.GroupView> updateGroup(
            @PathVariable Long id, @Valid @RequestBody AttendanceCommands.GroupSaveCommand command) {
        return Result.ok(configAppService.saveGroup(id, command));
    }

    /**
     * 删除考勤组。
     *
     * @param id 主键
     * @return 空响应体
     */
    @DeleteMapping("/groups/{id}")
    @RequirePermission("sys:oa:attendance:config:delete")
    @AuditLog(operation = "删除考勤组", type = OperateType.DELETE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        configAppService.deleteGroup(id);
        return Result.ok();
    }

    /**
     * 查询全部班次。
     *
     * @return 班次列表
     */
    @GetMapping("/shifts")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<List<AttendanceViews.ShiftView>> shifts() {
        return Result.ok(configAppService.listShifts());
    }

    /**
     * 新增班次。
     *
     * @param command 保存命令
     * @return 班次
     */
    @PostMapping("/shifts")
    @RequirePermission("sys:oa:attendance:config:add")
    @AuditLog(operation = "新增班次", type = OperateType.CREATE, targetType = TARGET_CONFIG)
    public Result<AttendanceViews.ShiftView> createShift(
            @Valid @RequestBody AttendanceCommands.ShiftSaveCommand command) {
        return Result.ok(configAppService.saveShift(null, command));
    }

    /**
     * 更新班次。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 班次
     */
    @PutMapping("/shifts/{id}")
    @RequirePermission("sys:oa:attendance:config:edit")
    @AuditLog(operation = "更新班次", type = OperateType.UPDATE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<AttendanceViews.ShiftView> updateShift(
            @PathVariable Long id, @Valid @RequestBody AttendanceCommands.ShiftSaveCommand command) {
        return Result.ok(configAppService.saveShift(id, command));
    }

    /**
     * 删除班次。
     *
     * @param id 主键
     * @return 空响应体
     */
    @DeleteMapping("/shifts/{id}")
    @RequirePermission("sys:oa:attendance:config:delete")
    @AuditLog(operation = "删除班次", type = OperateType.DELETE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<Void> deleteShift(@PathVariable Long id) {
        configAppService.deleteShift(id);
        return Result.ok();
    }

    /**
     * 查询全部考勤规则。
     *
     * @return 规则列表
     */
    @GetMapping("/rules")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<List<AttendanceViews.RuleView>> rules() {
        return Result.ok(configAppService.listRules());
    }

    /**
     * 新增考勤规则。
     *
     * @param command 保存命令
     * @return 规则
     */
    @PostMapping("/rules")
    @RequirePermission("sys:oa:attendance:config:add")
    @AuditLog(operation = "新增考勤规则", type = OperateType.CREATE, targetType = TARGET_CONFIG)
    public Result<AttendanceViews.RuleView> createRule(
            @Valid @RequestBody AttendanceCommands.RuleSaveCommand command) {
        return Result.ok(configAppService.saveRule(null, command));
    }

    /**
     * 更新考勤规则。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 规则
     */
    @PutMapping("/rules/{id}")
    @RequirePermission("sys:oa:attendance:config:edit")
    @AuditLog(operation = "更新考勤规则", type = OperateType.UPDATE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<AttendanceViews.RuleView> updateRule(
            @PathVariable Long id, @Valid @RequestBody AttendanceCommands.RuleSaveCommand command) {
        return Result.ok(configAppService.saveRule(id, command));
    }

    /**
     * 删除考勤规则。
     *
     * @param id 主键
     * @return 空响应体
     */
    @DeleteMapping("/rules/{id}")
    @RequirePermission("sys:oa:attendance:config:delete")
    @AuditLog(operation = "删除考勤规则", type = OperateType.DELETE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<Void> deleteRule(@PathVariable Long id) {
        configAppService.deleteRule(id);
        return Result.ok();
    }

    /**
     * 查询全部打卡地点。
     *
     * @return 地点列表
     */
    @GetMapping("/locations")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<List<AttendanceViews.LocationView>> locations() {
        return Result.ok(configAppService.listLocations());
    }

    /**
     * 新增打卡地点。
     *
     * @param command 保存命令
     * @return 地点
     */
    @PostMapping("/locations")
    @RequirePermission("sys:oa:attendance:config:add")
    @AuditLog(operation = "新增打卡地点", type = OperateType.CREATE, targetType = TARGET_CONFIG)
    public Result<AttendanceViews.LocationView> createLocation(
            @Valid @RequestBody AttendanceCommands.LocationSaveCommand command) {
        return Result.ok(configAppService.saveLocation(null, command));
    }

    /**
     * 更新打卡地点。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 地点
     */
    @PutMapping("/locations/{id}")
    @RequirePermission("sys:oa:attendance:config:edit")
    @AuditLog(operation = "更新打卡地点", type = OperateType.UPDATE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<AttendanceViews.LocationView> updateLocation(
            @PathVariable Long id, @Valid @RequestBody AttendanceCommands.LocationSaveCommand command) {
        return Result.ok(configAppService.saveLocation(id, command));
    }

    /**
     * 删除打卡地点。
     *
     * @param id 主键
     * @return 空响应体
     */
    @DeleteMapping("/locations/{id}")
    @RequirePermission("sys:oa:attendance:config:delete")
    @AuditLog(operation = "删除打卡地点", type = OperateType.DELETE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        configAppService.deleteLocation(id);
        return Result.ok();
    }

    /**
     * 查询全部打卡设备。
     *
     * @return 设备列表
     */
    @GetMapping("/devices")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<List<AttendanceViews.DeviceView>> devices() {
        return Result.ok(configAppService.listDevices());
    }

    /**
     * 新增打卡设备。
     *
     * @param command 保存命令
     * @return 设备
     */
    @PostMapping("/devices")
    @RequirePermission("sys:oa:attendance:config:add")
    @AuditLog(operation = "新增打卡设备", type = OperateType.CREATE, targetType = TARGET_CONFIG)
    public Result<AttendanceViews.DeviceView> createDevice(
            @Valid @RequestBody AttendanceCommands.DeviceSaveCommand command) {
        return Result.ok(configAppService.saveDevice(null, command));
    }

    /**
     * 更新打卡设备。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 设备
     */
    @PutMapping("/devices/{id}")
    @RequirePermission("sys:oa:attendance:config:edit")
    @AuditLog(operation = "更新打卡设备", type = OperateType.UPDATE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<AttendanceViews.DeviceView> updateDevice(
            @PathVariable Long id, @Valid @RequestBody AttendanceCommands.DeviceSaveCommand command) {
        return Result.ok(configAppService.saveDevice(id, command));
    }

    /**
     * 删除打卡设备。
     *
     * @param id 主键
     * @return 空响应体
     */
    @DeleteMapping("/devices/{id}")
    @RequirePermission("sys:oa:attendance:config:delete")
    @AuditLog(operation = "删除打卡设备", type = OperateType.DELETE, targetType = TARGET_CONFIG, targetId = "#id")
    public Result<Void> deleteDevice(@PathVariable Long id) {
        configAppService.deleteDevice(id);
        return Result.ok();
    }

    /**
     * 分页查询考勤异常。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/exceptions/page")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<PageResult<AttendanceViews.ExceptionView>> exceptions(AttendanceExceptionQuery query) {
        return Result.ok(exceptionAppService.page(query));
    }

    /**
     * 处理考勤异常（确认或豁免）。
     *
     * @param id 异常ID
     * @param command 处理命令
     * @param request 当前请求
     * @return 空响应体
     */
    @PutMapping("/exceptions/{id}/status")
    @RequirePermission("sys:oa:attendance:exception:handle")
    @AuditLog(operation = "处理考勤异常", type = OperateType.UPDATE, targetType = TARGET_EXCEPTION, targetId = "#id")
    public Result<Void> handleException(@PathVariable Long id,
                                        @Valid @RequestBody AttendanceCommands.ExceptionHandleCommand command,
                                        HttpServletRequest request) {
        exceptionAppService.handle(id, command, String.valueOf(UserContext.requireUserId(request)));
        return Result.ok();
    }

    /**
     * 分页查询补卡申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/corrections/page")
    @RequirePermission("sys:oa:attendance:config:list")
    public Result<PageResult<AttendanceViews.CorrectionView>> corrections(AttendanceCorrectionQuery query) {
        return Result.ok(correctionAppService.page(query));
    }

    /**
     * 提交补卡申请。
     *
     * @param command 补卡命令
     * @param request 当前请求
     * @return 补卡申请
     */
    @PostMapping("/corrections")
    @RequirePermission("sys:oa:attendance:correction:edit")
    @AuditLog(operation = "提交补卡申请", type = OperateType.CREATE, targetType = TARGET_CORRECTION)
    public Result<AttendanceViews.CorrectionView> createCorrection(
            @Valid @RequestBody AttendanceCommands.CorrectionSaveCommand command, HttpServletRequest request) {
        return Result.ok(correctionAppService.create(UserContext.requireUserId(request), command));
    }

    /**
     * 审批 / 撤销补卡申请（管理员）。
     *
     * @param id 补卡申请ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 空响应体
     */
    @PutMapping("/corrections/{id}/status")
    @RequirePermission("sys:oa:attendance:exception:handle")
    @AuditLog(operation = "处理补卡申请", type = OperateType.UPDATE, targetType = TARGET_CORRECTION, targetId = "#id")
    public Result<Void> updateCorrectionStatus(@PathVariable Long id,
                                               @Valid @RequestBody AttendanceCommands.CorrectionStatusCommand command,
                                               HttpServletRequest request) {
        correctionAppService.updateStatus(id, command, String.valueOf(UserContext.requireUserId(request)));
        return Result.ok();
    }
}
