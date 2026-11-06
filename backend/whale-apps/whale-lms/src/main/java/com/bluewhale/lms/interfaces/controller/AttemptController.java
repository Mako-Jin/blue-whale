package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.exam.application.command.AttemptSaveCommand;
import com.bluewhale.lms.exam.application.service.AttemptAppService;
import com.bluewhale.lms.exam.application.view.AttemptPaperView;
import com.bluewhale.lms.exam.application.view.AttemptResultView;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作答接口（M3-2，学员侧）。
 *
 * <p>卷面接口不回传标准答案与选项正确标记，交卷后才按题给出对错；成绩按版本留存，
 * 人工判分后学员看到的分数自动更新到新版本。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/lms/attempt")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptAppService attemptAppService;

    /**
     * 开始作答（存在未交卷记录时复用）。
     *
     * @param examId 考试 ID
     * @param request 当前请求
     * @return 作答记录 ID
     */
    @PostMapping("/start/{examId}")
    @RequirePermission(LmsPermissions.ATTEMPT_START)
    @AuditLog(operation = "开始考试作答", type = OperateType.CREATE, targetType = "lr_exam_attempt")
    public Result<Long> start(@PathVariable Long examId, HttpServletRequest request) {
        return Result.ok(attemptAppService.start(examId, actor(request)));
    }

    /**
     * 获取作答卷面。
     *
     * @param attemptId 作答记录 ID
     * @param request 当前请求
     * @return 卷面
     */
    @GetMapping("/{attemptId}/paper")
    @RequirePermission(LmsPermissions.ATTEMPT_START)
    public Result<AttemptPaperView> paper(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.ok(attemptAppService.paper(attemptId, actor(request)));
    }

    /**
     * 保存答案（可整卷或增量提交）。
     *
     * @param attemptId 作答记录 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 本次落库题数
     */
    @PutMapping("/{attemptId}/answers")
    @RequirePermission(LmsPermissions.ATTEMPT_SAVE)
    @AuditLog(operation = "保存考试作答", type = OperateType.UPDATE, targetType = "lr_exam_answer")
    public Result<Integer> save(@PathVariable Long attemptId,
                                @Valid @RequestBody AttemptSaveCommand command,
                                HttpServletRequest request) {
        return Result.ok(attemptAppService.save(attemptId, command, actor(request)));
    }

    /**
     * 交卷。
     *
     * @param attemptId 作答记录 ID
     * @param request 当前请求
     * @return 交卷结果
     */
    @PostMapping("/{attemptId}/submit")
    @RequirePermission(LmsPermissions.ATTEMPT_SUBMIT)
    @AuditLog(operation = "交卷", type = OperateType.UPDATE, targetType = "lr_exam_attempt")
    public Result<AttemptResultView> submit(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.ok(attemptAppService.submit(attemptId, actor(request)));
    }

    /**
     * 我的作答记录分页。
     *
     * @param examId 考试 ID
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/mine")
    @RequirePermission(LmsPermissions.ATTEMPT_SUBMIT)
    public Result<PageResult<AttemptView>> mine(
            @RequestParam(value = "examId", required = false) Long examId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        AttemptQuery query = new AttemptQuery();
        query.setExamId(examId);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(attemptAppService.mine(query, actor(request)));
    }

    /**
     * 我的成绩分页。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/scores/mine")
    @RequirePermission(LmsPermissions.ATTEMPT_SUBMIT)
    public Result<PageResult<ScoreView>> myScores(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(attemptAppService.myScores(PageParams.current(current, pageNum),
                PageParams.size(size, pageSize), actor(request)));
    }

    /**
     * 查询某次作答结果（仅本人）。
     *
     * @param attemptId 作答记录 ID
     * @param request 当前请求
     * @return 结果
     */
    @GetMapping("/{attemptId}/result")
    @RequirePermission(LmsPermissions.ATTEMPT_SUBMIT)
    public Result<AttemptResultView> result(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.ok(attemptAppService.result(attemptId, actor(request)));
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
