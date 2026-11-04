package com.bluewhale.erp.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.command.AttendanceSaveCommand;
import com.bluewhale.erp.hr.application.service.AttendanceAppService;
import com.bluewhale.erp.hr.application.view.AttendanceView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考勤汇总接口：分页查询与登记。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/hr/attendance")
@RequiredArgsConstructor
public class HrAttendanceController {

    private final AttendanceAppService attendanceAppService;

    /**
     * 考勤汇总分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 考勤分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:hr:attendance:list")
    public Result<PageResult<AttendanceView>> page(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(attendanceAppService.page(query));
    }

    /**
     * 保存考勤（按员工 + 期间 upsert）。
     *
     * @param command 保存命令
     * @return 考勤视图
     */
    @PostMapping
    @RequirePermission("sys:erp:hr:attendance:save")
    @AuditLog(operation = "登记考勤汇总", targetType = "erp_hr_attendance_summary", type = OperateType.UPDATE)
    public Result<AttendanceView> save(@Valid @RequestBody AttendanceSaveCommand command) {
        return Result.ok(attendanceAppService.save(command));
    }
}
