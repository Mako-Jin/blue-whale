package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.course.application.command.ProgressReportCommand;
import com.bluewhale.lms.course.application.service.LearningAppService;
import com.bluewhale.lms.course.application.view.EnrollmentView;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习接口（报名 / 进度，M1）。
 *
 * <p>报名幂等：同一学员对同一课程重复报名返回既有记录；进度只增不减。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/learning", version = "1")
@RequiredArgsConstructor
public class LearningController {

    private final LearningAppService learningAppService;

    /**
     * 报名课程（幂等）。
     *
     * @param courseId 课程 ID
     * @param request 当前请求
     * @return 学习记录视图
     */
    @PostMapping("/enroll/{courseId}")
    @RequirePermission(LmsPermissions.LEARNING_ENROLL)
    @AuditLog(operation = "报名课程", type = OperateType.CREATE,
            targetType = "lr_enrollment")
    public Result<EnrollmentView> enroll(@PathVariable Long courseId, HttpServletRequest request) {
        return Result.ok(learningAppService.enroll(courseId, actor(request)));
    }

    /**
     * 我的学习分页。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 学习记录分页
     */
    @GetMapping("/my")
    @RequirePermission(LmsPermissions.LEARNING_LIST)
    public Result<PageResult<EnrollmentView>> my(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(learningAppService.myPage(PageParams.current(current, pageNum),
                PageParams.size(size, pageSize), actor(request)));
    }

    /**
     * 上报章节学习进度（只增不减）。
     *
     * @param courseId 课程 ID
     * @param command 进度命令
     * @param request 当前请求
     * @return 学习记录视图（含整体进度）
     */
    @PostMapping("/{courseId}/progress")
    @RequirePermission(LmsPermissions.LEARNING_PROGRESS)
    @AuditLog(operation = "上报学习进度", type = OperateType.UPDATE,
            targetType = "lr_progress")
    public Result<EnrollmentView> reportProgress(@PathVariable Long courseId,
                                                 @Valid @RequestBody ProgressReportCommand command,
                                                 HttpServletRequest request) {
        return Result.ok(learningAppService.reportProgress(courseId, command, actor(request)));
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
