package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.exam.application.command.QuestionSaveCommand;
import com.bluewhale.lms.exam.application.command.QuestionStatusCommand;
import com.bluewhale.lms.exam.application.service.QuestionAppService;
import com.bluewhale.lms.exam.application.view.QuestionView;
import com.bluewhale.lms.exam.domain.model.QuestionQuery;
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
 * 题库接口（M3-1）。
 *
 * <p>启用（ACTIVATE）会做完整性校验，草稿可保存不完整题目；删除前校验试卷引用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/question", version = "1")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionAppService questionAppService;

    /**
     * 题目分页。
     *
     * @param keyword 关键字
     * @param category 分类
     * @param type 题型
     * @param difficulty 难度
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.QUESTION_LIST)
    public Result<PageResult<QuestionView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        QuestionQuery query = new QuestionQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setType(type);
        query.setDifficulty(difficulty);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(questionAppService.page(query, actor(request)));
    }

    /**
     * 题目详情。
     *
     * @param id 题目 ID
     * @param request 当前请求
     * @return 题目
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.QUESTION_LIST)
    public Result<QuestionView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(questionAppService.detail(id, actor(request)));
    }

    /**
     * 新建题目。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 题目 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.QUESTION_CREATE)
    @AuditLog(operation = "新建题目", type = OperateType.CREATE, targetType = "lr_question")
    public Result<Long> create(@Valid @RequestBody QuestionSaveCommand command, HttpServletRequest request) {
        return Result.ok(questionAppService.create(command, actor(request)));
    }

    /**
     * 编辑题目。
     *
     * @param id 题目 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.QUESTION_UPDATE)
    @AuditLog(operation = "编辑题目", type = OperateType.UPDATE, targetType = "lr_question")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody QuestionSaveCommand command,
                               HttpServletRequest request) {
        questionAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 启用 / 停用题目。
     *
     * @param id 题目 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/status")
    @RequirePermission(LmsPermissions.QUESTION_UPDATE)
    @AuditLog(operation = "变更题目状态", type = OperateType.UPDATE, targetType = "lr_question")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody QuestionStatusCommand command,
                                     HttpServletRequest request) {
        questionAppService.changeStatus(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 删除题目。
     *
     * @param id 题目 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.QUESTION_DELETE)
    @AuditLog(operation = "删除题目", type = OperateType.DELETE, targetType = "lr_question")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        questionAppService.delete(id, actor(request));
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
