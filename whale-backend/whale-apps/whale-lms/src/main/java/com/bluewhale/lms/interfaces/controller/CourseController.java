package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.course.application.command.ChapterSaveCommand;
import com.bluewhale.lms.course.application.command.CourseSaveCommand;
import com.bluewhale.lms.course.application.command.CourseStatusCommand;
import com.bluewhale.lms.course.application.command.WareSaveCommand;
import com.bluewhale.lms.course.application.service.CourseAppService;
import com.bluewhale.lms.course.application.view.CourseDetailView;
import com.bluewhale.lms.course.application.view.CourseView;
import com.bluewhale.lms.course.domain.model.CourseQuery;
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
 * 课程管理接口（M1）。
 *
 * <p>发布状态机与内容冻结规则在应用层强制：发布态课程的章节 / 课件 / 头信息
 * 不可变更，需先归档下架；课件字节归 {@code whale-file}，本服务只保存引用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/course", version = "1")
@RequiredArgsConstructor
public class CourseController {

    private final CourseAppService courseAppService;

    /**
     * 课程分页。
     *
     * @param keyword 关键字
     * @param category 分类
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 课程分页
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.COURSE_LIST)
    public Result<PageResult<CourseView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CourseQuery query = new CourseQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(courseAppService.page(query, actor(request)));
    }

    /**
     * 课程详情（含章节与课件）。
     *
     * @param id 课程 ID
     * @param request 当前请求
     * @return 课程详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.COURSE_LIST)
    public Result<CourseDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(courseAppService.detail(id, actor(request)));
    }

    /**
     * 新建课程。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 课程 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.COURSE_CREATE)
    @AuditLog(operation = "新建课程", type = OperateType.CREATE,
            targetType = "lr_course")
    public Result<Long> create(@Valid @RequestBody CourseSaveCommand command, HttpServletRequest request) {
        return Result.ok(courseAppService.create(command, actor(request)));
    }

    /**
     * 编辑课程（发布态不可编辑）。
     *
     * @param id 课程 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.COURSE_UPDATE)
    @AuditLog(operation = "编辑课程", type = OperateType.UPDATE,
            targetType = "lr_course")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CourseSaveCommand command,
                               HttpServletRequest request) {
        courseAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 课程发布 / 归档。
     *
     * @param id 课程 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/status")
    @RequirePermission(LmsPermissions.COURSE_STATUS)
    @AuditLog(operation = "变更课程状态", type = OperateType.UPDATE,
            targetType = "lr_course")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody CourseStatusCommand command,
                                     HttpServletRequest request) {
        courseAppService.changeStatus(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 逻辑删除课程（发布态不可删）。
     *
     * @param id 课程 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.COURSE_DELETE)
    @AuditLog(operation = "删除课程", type = OperateType.DELETE,
            targetType = "lr_course")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        courseAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 新增章节。
     *
     * @param courseId 课程 ID
     * @param command 章节命令
     * @param request 当前请求
     * @return 章节 ID
     */
    @PostMapping("/{courseId}/chapter")
    @RequirePermission(LmsPermissions.CHAPTER_MANAGE)
    @AuditLog(operation = "新增章节", type = OperateType.CREATE,
            targetType = "lr_course_chapter")
    public Result<Long> addChapter(@PathVariable Long courseId,
                                   @Valid @RequestBody ChapterSaveCommand command,
                                   HttpServletRequest request) {
        return Result.ok(courseAppService.addChapter(courseId, command, actor(request)));
    }

    /**
     * 删除章节及其课件。
     *
     * @param chapterId 章节 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/chapter/{chapterId}")
    @RequirePermission(LmsPermissions.CHAPTER_MANAGE)
    @AuditLog(operation = "删除章节", type = OperateType.DELETE,
            targetType = "lr_course_chapter")
    public Result<Void> removeChapter(@PathVariable Long chapterId, HttpServletRequest request) {
        courseAppService.removeChapter(chapterId, actor(request));
        return Result.ok();
    }

    /**
     * 新增课件。
     *
     * @param chapterId 章节 ID
     * @param command 课件命令
     * @param request 当前请求
     * @return 课件 ID
     */
    @PostMapping("/chapter/{chapterId}/ware")
    @RequirePermission(LmsPermissions.WARE_MANAGE)
    @AuditLog(operation = "新增课件", type = OperateType.CREATE,
            targetType = "lr_courseware")
    public Result<Long> addWare(@PathVariable Long chapterId,
                                @Valid @RequestBody WareSaveCommand command,
                                HttpServletRequest request) {
        return Result.ok(courseAppService.addWare(chapterId, command, actor(request)));
    }

    /**
     * 删除课件。
     *
     * @param wareId 课件 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/ware/{wareId}")
    @RequirePermission(LmsPermissions.WARE_MANAGE)
    @AuditLog(operation = "删除课件", type = OperateType.DELETE,
            targetType = "lr_courseware")
    public Result<Void> removeWare(@PathVariable Long wareId, HttpServletRequest request) {
        courseAppService.removeWare(wareId, actor(request));
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
