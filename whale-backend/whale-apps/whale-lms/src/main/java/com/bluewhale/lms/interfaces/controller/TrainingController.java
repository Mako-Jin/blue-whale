package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import com.bluewhale.lms.training.application.command.AssignmentBatchCommand;
import com.bluewhale.lms.training.application.command.SessionSaveCommand;
import com.bluewhale.lms.training.application.command.TrainingClassSaveCommand;
import com.bluewhale.lms.training.application.command.TrainingClassStatusCommand;
import com.bluewhale.lms.training.application.service.TrainingAppService;
import com.bluewhale.lms.training.application.view.AssignmentResultView;
import com.bluewhale.lms.training.application.view.LecturerHoursView;
import com.bluewhale.lms.training.application.view.TrainingClassDetailView;
import com.bluewhale.lms.training.application.view.TrainingClassView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 培训计划接口（M2）。
 *
 * <p>培训班发布前必须有场次；发布后场次与头信息冻结（已分配学员看到的日程不可再变）；
 * 结课为终态。必修分配批量幂等，重复调用只累加 {@code skipped}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/training", version = "1")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingAppService trainingAppService;

    /**
     * 培训班分页。
     *
     * @param keyword 关键字
     * @param mode 形式
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 培训班分页
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.TRAINING_LIST)
    public Result<PageResult<TrainingClassView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        TrainingClassQuery query = new TrainingClassQuery();
        query.setKeyword(keyword);
        query.setMode(mode);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(trainingAppService.page(query, actor(request)));
    }

    /**
     * 培训班详情（含场次与统计）。
     *
     * @param id 培训班 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.TRAINING_LIST)
    public Result<TrainingClassDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(trainingAppService.detail(id, actor(request)));
    }

    /**
     * 新建培训班。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 培训班 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.TRAINING_CREATE)
    @AuditLog(operation = "新建培训班", type = OperateType.CREATE,
            targetType = "lr_training_class")
    public Result<Long> create(@Valid @RequestBody TrainingClassSaveCommand command, HttpServletRequest request) {
        return Result.ok(trainingAppService.create(command, actor(request)));
    }

    /**
     * 编辑培训班（仅草稿）。
     *
     * @param id 培训班 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.TRAINING_UPDATE)
    @AuditLog(operation = "编辑培训班", type = OperateType.UPDATE,
            targetType = "lr_training_class")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody TrainingClassSaveCommand command,
                               HttpServletRequest request) {
        trainingAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 发布 / 结课。
     *
     * @param id 培训班 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/status")
    @RequirePermission(LmsPermissions.TRAINING_STATUS)
    @AuditLog(operation = "变更培训班状态", type = OperateType.UPDATE,
            targetType = "lr_training_class")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody TrainingClassStatusCommand command,
                                     HttpServletRequest request) {
        trainingAppService.changeStatus(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 逻辑删除培训班（仅草稿）。
     *
     * @param id 培训班 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.TRAINING_DELETE)
    @AuditLog(operation = "删除培训班", type = OperateType.DELETE,
            targetType = "lr_training_class")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        trainingAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 新增场次（仅草稿）。
     *
     * @param id 培训班 ID
     * @param command 场次命令
     * @param request 当前请求
     * @return 场次 ID
     */
    @PostMapping("/{id}/session")
    @RequirePermission(LmsPermissions.TRAINING_SESSION)
    @AuditLog(operation = "新增培训场次", type = OperateType.CREATE,
            targetType = "lr_class_session")
    public Result<Long> addSession(@PathVariable Long id,
                                   @Valid @RequestBody SessionSaveCommand command,
                                   HttpServletRequest request) {
        return Result.ok(trainingAppService.addSession(id, command, actor(request)));
    }

    /**
     * 删除场次（仅草稿）。
     *
     * @param sessionId 场次 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/session/{sessionId}")
    @RequirePermission(LmsPermissions.TRAINING_SESSION)
    @AuditLog(operation = "删除培训场次", type = OperateType.DELETE,
            targetType = "lr_class_session")
    public Result<Void> removeSession(@PathVariable Long sessionId, HttpServletRequest request) {
        trainingAppService.removeSession(sessionId, actor(request));
        return Result.ok();
    }

    /**
     * 批量必修分配（幂等）。
     *
     * @param id 培训班 ID
     * @param command 分配命令
     * @param request 当前请求
     * @return 执行结果
     */
    @PostMapping("/{id}/assign")
    @RequirePermission(LmsPermissions.TRAINING_ASSIGN)
    @AuditLog(operation = "培训班必修分配", type = OperateType.CREATE,
            targetType = "lr_assignment")
    public Result<AssignmentResultView> assign(@PathVariable Long id,
                                               @Valid @RequestBody AssignmentBatchCommand command,
                                               HttpServletRequest request) {
        return Result.ok(trainingAppService.assign(id, command, actor(request)));
    }

    /**
     * 讲师课时台账。
     *
     * @param id 培训班 ID
     * @param request 当前请求
     * @return 讲师课时列表
     */
    @GetMapping("/{id}/hours")
    @RequirePermission(LmsPermissions.TRAINING_HOURS)
    public Result<List<LecturerHoursView>> lecturerHours(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(trainingAppService.lecturerHours(id, actor(request)));
    }

    /**
     * 完成认定（按签到与课程进度实时计算，不落库快照）。
     *
     * @param id 培训班 ID
     * @param request 当前请求
     * @return 认定结果
     */
    @GetMapping("/{id}/completion")
    @RequirePermission(LmsPermissions.TRAINING_COMPLETION)
    public Result<TrainingCompletionView> completion(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(trainingAppService.completion(id, actor(request)));
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
