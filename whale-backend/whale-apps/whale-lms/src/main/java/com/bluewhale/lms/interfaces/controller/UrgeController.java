package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.integration.application.service.UrgeAppService;
import com.bluewhale.lms.integration.application.view.ScheduleConflictView;
import com.bluewhale.lms.integration.application.view.UrgeItemView;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 未完成必修催办接口（M5-2）。
 *
 * <p>自动催办由 {@code LmsUrgeJob} 每日执行；这里提供手动补跑与单班预览，
 * 用于「昨天没跑成功」或「先看看会发给谁」。补跑按日期幂等，重复点不会重复发。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/urge", version = "1")
@RequiredArgsConstructor
public class UrgeController {

    private final UrgeAppService urgeAppService;

    /**
     * 手动执行一轮催办（可按天补跑）。
     *
     * @param day 催办归属日期，空为当天
     * @param request 当前请求
     * @return 结果汇总
     */
    @PostMapping("/run")
    @RequirePermission(LmsPermissions.URGE_MANAGE)
    @AuditLog(operation = "手动执行未完成必修催办", type = OperateType.OTHER, targetType = "lr_notify_log")
    public Result<UrgeRunView> run(
            @RequestParam(value = "day", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            HttpServletRequest request) {
        return Result.ok(urgeAppService.run(day, actor(request)));
    }

    /**
     * 预览单个培训班会催到谁（不投递）。
     *
     * @param classId 培训班 ID
     * @param request 当前请求
     * @return 单班结果
     */
    @GetMapping("/preview")
    @RequirePermission(LmsPermissions.URGE_MANAGE)
    public Result<UrgeItemView> preview(@RequestParam("classId") Long classId,
                                        HttpServletRequest request) {
        return Result.ok(urgeAppService.preview(classId, actor(request)));
    }

    /**
     * 查询培训班的场次与学员请假冲突（只读提示）。
     *
     * @param classId 培训班 ID
     * @param request 当前请求
     * @return 冲突明细
     */
    @GetMapping("/leave-conflicts")
    @RequirePermission(LmsPermissions.URGE_MANAGE)
    public Result<List<ScheduleConflictView>> leaveConflicts(@RequestParam("classId") Long classId,
                                                             HttpServletRequest request) {
        return Result.ok(urgeAppService.scheduleConflicts(classId, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
