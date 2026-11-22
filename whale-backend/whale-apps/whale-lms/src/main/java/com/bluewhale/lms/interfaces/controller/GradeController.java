package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.exam.application.command.GradeCommand;
import com.bluewhale.lms.exam.application.service.GradeAppService;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.GradeView;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工判分接口（M3-2，教师侧）。
 *
 * <p>判分明细携带标准答案，因此与学员侧接口用不同权限码隔离；提交判分只接受主观题，
 * 每次提交生成新的成绩版本。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/grade", version = "1")
@RequiredArgsConstructor
public class GradeController {

    private final GradeAppService gradeAppService;

    /**
     * 待判队列分页。
     *
     * @param examId 考试 ID
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/pending")
    @RequirePermission(LmsPermissions.GRADE_LIST)
    public Result<PageResult<AttemptView>> pending(
            @RequestParam(value = "examId", required = false) Long examId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        AttemptQuery query = new AttemptQuery();
        query.setExamId(examId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(gradeAppService.pending(query, actor(request)));
    }

    /**
     * 判分明细（含标准答案）。
     *
     * @param attemptId 作答记录 ID
     * @param request 当前请求
     * @return 判分视图
     */
    @GetMapping("/{attemptId}")
    @RequirePermission(LmsPermissions.GRADE_LIST)
    public Result<GradeView> detail(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.ok(gradeAppService.detail(attemptId, actor(request)));
    }

    /**
     * 提交判分。
     *
     * @param attemptId 作答记录 ID
     * @param command 判分命令
     * @param request 当前请求
     * @return 新的成绩版本
     */
    @PostMapping("/{attemptId}")
    @RequirePermission(LmsPermissions.GRADE_SUBMIT)
    @AuditLog(operation = "考试人工判分", type = OperateType.UPDATE, targetType = "lr_score")
    public Result<ScoreView> grade(@PathVariable Long attemptId,
                                   @Valid @RequestBody GradeCommand command,
                                   HttpServletRequest request) {
        return Result.ok(gradeAppService.grade(attemptId, command, actor(request)));
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
