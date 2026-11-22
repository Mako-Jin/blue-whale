package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.exam.application.command.PaperGenerateCommand;
import com.bluewhale.lms.exam.application.command.PaperQuestionAddCommand;
import com.bluewhale.lms.exam.application.command.PaperSaveCommand;
import com.bluewhale.lms.exam.application.service.PaperAppService;
import com.bluewhale.lms.exam.application.view.PaperDetailView;
import com.bluewhale.lms.exam.application.view.PaperGenerateResultView;
import com.bluewhale.lms.exam.application.view.PaperView;
import com.bluewhale.lms.exam.domain.model.PaperQuery;
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
 * 试卷接口（M3-1）。
 *
 * <p>规则抽题以 {@code seed} 保证可复现：不传 seed 时用试卷编码派生，重复生成结果一致；
 * 传新 seed 才会重新洗牌。候选不足直接报错，不用其它题补齐。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/paper", version = "1")
@RequiredArgsConstructor
public class PaperController {

    private final PaperAppService paperAppService;

    /**
     * 试卷分页。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param courseId 关联课程 ID
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.PAPER_LIST)
    public Result<PageResult<PaperView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PaperQuery query = new PaperQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCourseId(courseId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(paperAppService.page(query, actor(request)));
    }

    /**
     * 试卷详情（含题目清单）。
     *
     * @param id 试卷 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.PAPER_LIST)
    public Result<PaperDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(paperAppService.detail(id, actor(request)));
    }

    /**
     * 新建试卷。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 试卷 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.PAPER_CREATE)
    @AuditLog(operation = "新建试卷", type = OperateType.CREATE, targetType = "lr_paper")
    public Result<Long> create(@Valid @RequestBody PaperSaveCommand command, HttpServletRequest request) {
        return Result.ok(paperAppService.create(command, actor(request)));
    }

    /**
     * 编辑试卷（仅草稿）。
     *
     * @param id 试卷 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.PAPER_UPDATE)
    @AuditLog(operation = "编辑试卷", type = OperateType.UPDATE, targetType = "lr_paper")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody PaperSaveCommand command,
                               HttpServletRequest request) {
        paperAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 删除试卷（仅草稿）。
     *
     * @param id 试卷 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.PAPER_DELETE)
    @AuditLog(operation = "删除试卷", type = OperateType.DELETE, targetType = "lr_paper")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        paperAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 手工加题。
     *
     * @param id 试卷 ID
     * @param command 加题命令
     * @param request 当前请求
     * @return 空
     */
    @PostMapping("/{id}/question")
    @RequirePermission(LmsPermissions.PAPER_UPDATE)
    @AuditLog(operation = "试卷加题", type = OperateType.UPDATE, targetType = "lr_paper_question")
    public Result<Void> addQuestion(@PathVariable Long id,
                                    @Valid @RequestBody PaperQuestionAddCommand command,
                                    HttpServletRequest request) {
        paperAppService.addQuestion(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 移出题目。
     *
     * @param id 试卷 ID
     * @param questionId 题目 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}/question/{questionId}")
    @RequirePermission(LmsPermissions.PAPER_UPDATE)
    @AuditLog(operation = "试卷移出题目", type = OperateType.UPDATE, targetType = "lr_paper_question")
    public Result<Void> removeQuestion(@PathVariable Long id,
                                       @PathVariable Long questionId,
                                       HttpServletRequest request) {
        paperAppService.removeQuestion(id, questionId, actor(request));
        return Result.ok();
    }

    /**
     * 规则抽题（可复现）。
     *
     * @param id 试卷 ID
     * @param command 抽题命令
     * @param request 当前请求
     * @return 抽题结果
     */
    @PostMapping("/{id}/generate")
    @RequirePermission(LmsPermissions.PAPER_UPDATE)
    @AuditLog(operation = "试卷规则抽题", type = OperateType.UPDATE, targetType = "lr_paper_question")
    public Result<PaperGenerateResultView> generate(@PathVariable Long id,
                                                    @Valid @RequestBody PaperGenerateCommand command,
                                                    HttpServletRequest request) {
        return Result.ok(paperAppService.generate(id, command, actor(request)));
    }

    /**
     * 发布试卷。
     *
     * @param id 试卷 ID
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/publish")
    @RequirePermission(LmsPermissions.PAPER_PUBLISH)
    @AuditLog(operation = "发布试卷", type = OperateType.UPDATE, targetType = "lr_paper")
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        paperAppService.publish(id, actor(request));
        return Result.ok();
    }

    /**
     * 归档试卷。
     *
     * @param id 试卷 ID
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/archive")
    @RequirePermission(LmsPermissions.PAPER_PUBLISH)
    @AuditLog(operation = "归档试卷", type = OperateType.UPDATE, targetType = "lr_paper")
    public Result<Void> archive(@PathVariable Long id, HttpServletRequest request) {
        paperAppService.archive(id, actor(request));
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
