package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.WorkCalendarSaveCommand;
import com.bluewhale.oa.hr.application.service.WorkCalendarAppService;
import com.bluewhale.oa.hr.application.view.WorkCalendarView;
import com.bluewhale.oa.hr.domain.model.WorkCalendarQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * OA 工作日历接口。
 *
 * <p>维护节假日与调休，未配置日期按周一至周五兜底为工作日。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/hr/calendar")
@RequiredArgsConstructor
public class WorkCalendarController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_work_calendar";

    private final WorkCalendarAppService workCalendarAppService;

    /**
     * 分页查询工作日历（支持年月或日期区间）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:hr:calendar:list")
    public Result<PageResult<WorkCalendarView>> page(WorkCalendarQuery query) {
        return Result.ok(workCalendarAppService.page(query));
    }

    /**
     * 保存某天工作日历（按日期幂等 upsert）。
     *
     * @param date 日期（yyyy-MM-dd）
     * @param command 保存命令
     * @return 保存后的日历
     */
    @PutMapping("/{date}")
    @RequirePermission("sys:oa:hr:calendar:edit")
    @AuditLog(operation = "保存工作日历", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#date")
    public Result<WorkCalendarView> upsert(@PathVariable LocalDate date,
                                           @Valid @RequestBody WorkCalendarSaveCommand command) {
        return Result.ok(workCalendarAppService.upsert(date, command));
    }
}
