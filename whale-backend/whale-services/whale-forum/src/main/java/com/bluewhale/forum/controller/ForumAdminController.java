package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.AuditActionRequest;
import com.bluewhale.forum.dto.AuditItemVO;
import com.bluewhale.forum.dto.ModerationRequest;
import com.bluewhale.forum.dto.ReconcileReportVO;
import com.bluewhale.forum.dto.ReportHandleRequest;
import com.bluewhale.forum.dto.ReportVO;
import com.bluewhale.forum.dto.SensitiveWordSaveRequest;
import com.bluewhale.forum.dto.SensitiveWordVO;
import com.bluewhale.forum.service.AuditService;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.CountReconcileService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import com.bluewhale.forum.service.ReportService;
import com.bluewhale.forum.service.SensitiveWordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛管理端接口（审核台 / 举报工单 / 处置 / 词库 / 通知补偿）。
 *
 * <p>所有端点都在 {@code /forum/admin} 下，权限按 {@link ForumPermissions} 分开：
 * 审核用 {@code manage:audit}、处置用 {@code manage:dispose}、词库用 {@code manage:board}。
 * 只给一个 {@code manage:list} 会让「能看审核台」等于「能删任何人帖子」。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/admin", version = "1")
@RequiredArgsConstructor
public class ForumAdminController {

    private final AuditService auditService;

    private final ReportService reportService;

    private final ModerationService moderationService;

    private final BanService banService;

    private final SensitiveWordService sensitiveWordService;

    private final ForumNotifyService notifyService;

    private final CountReconcileService countReconcileService;

    /**
     * 待审队列。
     *
     * @param targetType THREAD / POST（缺省 THREAD）
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/audit/page")
    @RequirePermission(ForumPermissions.MANAGE_AUDIT)
    public Result<PageResult<AuditItemVO>> auditPage(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Long size,
            HttpServletRequest request) {
        return Result.ok(auditService.page(targetType, current, size, ForumActor.from(request)));
    }

    /**
     * 审核通过。
     *
     * @param body 审核请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/audit/pass")
    @RequirePermission(ForumPermissions.MANAGE_AUDIT)
    @AuditLog(operation = "论坛审核通过", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<Void> pass(@Valid @RequestBody AuditActionRequest body, HttpServletRequest request) {
        auditService.pass(body.getTargetType(), body.getTargetId(), body.getRemark(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 审核驳回（意见必填）。
     *
     * @param body 审核请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/audit/reject")
    @RequirePermission(ForumPermissions.MANAGE_AUDIT)
    @AuditLog(operation = "论坛审核驳回", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<Void> reject(@Valid @RequestBody AuditActionRequest body, HttpServletRequest request) {
        auditService.reject(body.getTargetType(), body.getTargetId(), body.getRemark(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 举报工单分页。
     *
     * @param status 状态（可空）
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/report/page")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    public Result<PageResult<ReportVO>> reportPage(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Long size,
            HttpServletRequest request) {
        return Result.ok(reportService.page(status, current, size, ForumActor.from(request)));
    }

    /**
     * 处理举报工单。
     *
     * @param id 工单ID
     * @param body 处理请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/report/{id}/handle")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "处理论坛举报", type = OperateType.UPDATE, targetType = "fm_report")
    public Result<Void> handleReport(@PathVariable("id") Long id,
                                     @Valid @RequestBody ReportHandleRequest body,
                                     HttpServletRequest request) {
        reportService.handle(id, body, ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 删除帖子（治理处置）。
     *
     * @param id 帖子ID
     * @param body 处置请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/thread/{id}/remove")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "删除论坛帖子（治理）", type = OperateType.DELETE, targetType = "fm_thread")
    public Result<Void> removeThread(@PathVariable("id") Long id,
                                     @Valid @RequestBody ModerationRequest body,
                                     HttpServletRequest request) {
        moderationService.removeThread(id, body.getReason(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 删除楼层（治理处置）。
     *
     * @param id 楼层ID
     * @param body 处置请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/post/{id}/remove")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "删除论坛楼层（治理）", type = OperateType.DELETE, targetType = "fm_post")
    public Result<Void> removePost(@PathVariable("id") Long id,
                                   @Valid @RequestBody ModerationRequest body,
                                   HttpServletRequest request) {
        moderationService.removePost(id, body.getReason(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 题金榜（加精，治理动作）。
     *
     * @param id 帖子ID
     * @param body 处置请求（理由必填）
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/thread/{id}/essence")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "论坛题金榜（加精）", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<Void> featureThread(@PathVariable("id") Long id,
                                      @Valid @RequestBody ModerationRequest body,
                                      HttpServletRequest request) {
        moderationService.featureThread(id, body.getReason(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 撤销精华（治理动作）。
     *
     * @param id 帖子ID
     * @param body 处置请求（理由必填）
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/thread/{id}/unessence")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "论坛撤销精华", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<Void> unfeatureThread(@PathVariable("id") Long id,
                                        @Valid @RequestBody ModerationRequest body,
                                        HttpServletRequest request) {
        moderationService.unfeatureThread(id, body.getReason(), ForumActor.from(request));
        return Result.ok();
    }

    /**
     * 禁言/封号。
     *
     * @param id 用户ID
     * @param body 处置请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/user/{id}/ban")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "论坛禁言/封号", type = OperateType.UPDATE, targetType = "fm_user_ban")
    public Result<Void> banUser(@PathVariable("id") Long id,
                                @Valid @RequestBody ModerationRequest body,
                                HttpServletRequest request) {
        moderationService.ban(id, body.getBanType() == null ? "MUTE" : body.getBanType(),
                body.getDays() == null ? 0 : body.getDays(), body.getReason(),
                ForumActor.from(request), null);
        return Result.ok();
    }

    /**
     * 解除禁言/封号。
     *
     * @param id 用户ID
     * @param request 当前请求
     * @return 解除条数
     */
    @PostMapping("/user/{id}/release")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "解除论坛禁言", type = OperateType.UPDATE, targetType = "fm_user_ban")
    public Result<Integer> releaseUser(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(moderationService.release(id, ForumActor.from(request)));
    }

    /**
     * 用户当前生效处置（供主页展示「禁言中，至 X 月 X 日」）。
     *
     * @param id 用户ID
     * @return 生效记录；无则数据为 null
     */
    @GetMapping("/user/{id}/ban")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    public Result<Object> activeBan(@PathVariable("id") Long id) {
        return Result.ok(banService.activeBan(id));
    }

    /**
     * 词库分页。
     *
     * @param keyword 关键词（可空）
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/sensitive/page")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    public Result<PageResult<SensitiveWordVO>> sensitivePage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Long size) {
        return Result.ok(sensitiveWordService.page(keyword, current, size));
    }

    /**
     * 新增/更新词条。
     *
     * @param body 词条请求
     * @param request 当前请求
     * @return 词条ID
     */
    @PostMapping("/sensitive")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    @AuditLog(operation = "维护论坛敏感词", type = OperateType.UPDATE, targetType = "fm_sensitive_word")
    public Result<Long> saveSensitive(@Valid @RequestBody SensitiveWordSaveRequest body,
                                      HttpServletRequest request) {
        return Result.ok(sensitiveWordService.save(body, ForumActor.from(request).userId()));
    }

    /**
     * 删除词条。
     *
     * @param id 词条ID
     * @return 空结果
     */
    @DeleteMapping("/sensitive/{id}")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    @AuditLog(operation = "删除论坛敏感词", type = OperateType.DELETE, targetType = "fm_sensitive_word")
    public Result<Void> deleteSensitive(@PathVariable("id") Long id) {
        sensitiveWordService.remove(id);
        return Result.ok();
    }

    /**
     * 重载词库缓存。
     *
     * @return 加载到的词条数
     */
    @PostMapping("/sensitive/reload")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    public Result<Integer> reloadSensitive() {
        return Result.ok(sensitiveWordService.reload());
    }

    /**
     * 补偿投递待发通知（由调度任务或运维手工触发）。
     *
     * @param limit 单批条数
     * @return 成功条数
     */
    @PostMapping("/notify/flush")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    public Result<Integer> flushNotify(@RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(notifyService.flushPending(limit == null ? 100 : limit));
    }

    /**
     * 计数对账（默认只报告不修，需显式 {@code fix=true} 才写回）。
     *
     * <p>默认 dry-run 是刻意的：对账接口一旦默认写库，任何一次误点都会把展示计数
     * 改成明细口径，而明细口径在「楼层被删但保留编号」等历史场景下未必就是
     * 运营想要的值。先看差异，再决定修。</p>
     *
     * @param fix 是否按真实值修正
     * @param limit 单批帖子数
     * @param request 当前请求
     * @return 对账结果
     */
    @PostMapping("/count/reconcile")
    @RequirePermission(ForumPermissions.MANAGE_OPS)
    @AuditLog(operation = "论坛计数对账", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<ReconcileReportVO> reconcileCounts(
            @RequestParam(value = "fix", required = false) Boolean fix,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        return Result.ok(countReconcileService.reconcile(Boolean.TRUE.equals(fix), limit,
                ForumActor.from(request).userId()));
    }
}
