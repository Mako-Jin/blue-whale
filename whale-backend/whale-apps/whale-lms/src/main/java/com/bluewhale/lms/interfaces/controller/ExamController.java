package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.exam.application.command.ExamSaveCommand;
import com.bluewhale.lms.exam.application.service.ExamAppService;
import com.bluewhale.lms.exam.application.view.ExamView;
import com.bluewhale.lms.exam.domain.model.ExamQuery;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
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

/**
 * 考试接口（M3-2）。
 *
 * <p>考试只引用已发布试卷，发布时校验试卷状态与及格分上限；结课为终态，
 * 不接受新的作答，历史成绩保留。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/lms/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamAppService examAppService;

    /**
     * 考试分页。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.EXAM_LIST)
    public Result<PageResult<ExamView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ExamQuery query = new ExamQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(examAppService.page(query, actor(request)));
    }

    /**
     * 考试详情。
     *
     * @param id 考试 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.EXAM_LIST)
    public Result<ExamView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(examAppService.detail(id, actor(request)));
    }

    /**
     * 新建考试。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 考试 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.EXAM_CREATE)
    @AuditLog(operation = "新建考试", type = OperateType.CREATE, targetType = "lr_exam")
    public Result<Long> create(@Valid @RequestBody ExamSaveCommand command, HttpServletRequest request) {
        return Result.ok(examAppService.create(command, actor(request)));
    }

    /**
     * 编辑考试（仅草稿）。
     *
     * @param id 考试 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.EXAM_UPDATE)
    @AuditLog(operation = "编辑考试", type = OperateType.UPDATE, targetType = "lr_exam")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ExamSaveCommand command,
                               HttpServletRequest request) {
        examAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 删除考试（仅草稿）。
     *
     * @param id 考试 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.EXAM_DELETE)
    @AuditLog(operation = "删除考试", type = OperateType.DELETE, targetType = "lr_exam")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        examAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 发布考试。
     *
     * @param id 考试 ID
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/publish")
    @RequirePermission(LmsPermissions.EXAM_PUBLISH)
    @AuditLog(operation = "发布考试", type = OperateType.UPDATE, targetType = "lr_exam")
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        examAppService.publish(id, actor(request));
        return Result.ok();
    }

    /**
     * 结课。
     *
     * @param id 考试 ID
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/close")
    @RequirePermission(LmsPermissions.EXAM_PUBLISH)
    @AuditLog(operation = "考试结课", type = OperateType.UPDATE, targetType = "lr_exam")
    public Result<Void> close(@PathVariable Long id, HttpServletRequest request) {
        examAppService.close(id, actor(request));
        return Result.ok();
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
