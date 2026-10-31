package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.AppealQueryCommand;
import com.bluewhale.music.application.command.AppealReviewCommand;
import com.bluewhale.music.application.command.AppealSubmitCommand;
import com.bluewhale.music.application.service.SongAppealAppService;
import com.bluewhale.music.application.view.SongAppealView;
import com.bluewhale.music.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版权下架申诉接口（M5-3）。
 *
 * <p>上传者提交申诉要求 {@code sys:music:upload}（所有登录用户已授予）；
 * 运营复核要求 {@code sys:music:appeal:audit}。复核通过仅恢复版权为有效并保持下架，
 * 需运营重新上架后才会复播。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class SongAppealController {

    private final SongAppealAppService songAppealAppService;

    /**
     * 上传者提交版权下架申诉。
     *
     * @param id 歌曲 ID
     * @param command 申诉命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 申诉视图
     */
    @PostMapping("/song/{id}/appeal")
    @RequirePermission("sys:music:upload")
    @AuditLog(operation = "提交版权下架申诉", type = OperateType.CREATE, targetType = "mu_song_appeal",
            targetId = "#id")
    public Result<SongAppealView> submit(@PathVariable Long id,
                                        @RequestBody AppealSubmitCommand command,
                                        HttpServletRequest request) {
        return Result.ok(songAppealAppService.submit(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 申诉复核分页。
     *
     * @param songId 歌曲 ID，可选
     * @param status 状态过滤：PENDING / APPROVED / REJECTED，可选
     * @param current 页码
     * @param size 每页条数
     * @return 申诉分页
     */
    @GetMapping("/appeal/page")
    @RequirePermission("sys:music:appeal:audit")
    public Result<PageResult<SongAppealView>> page(
            @RequestParam(value = "songId", required = false) Long songId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        AppealQueryCommand command = new AppealQueryCommand();
        command.setSongId(songId);
        command.setStatus(status);
        command.setCurrent(current);
        command.setSize(size);
        return Result.ok(songAppealAppService.pageAudit(command));
    }

    /**
     * 申诉通过：版权恢复为有效并保持下架（需运营重新上架）。
     *
     * @param id 申诉 ID
     * @param command 复核命令（审核意见可选）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 申诉视图
     */
    @PostMapping("/appeal/{id}/approve")
    @RequirePermission("sys:music:appeal:audit")
    @AuditLog(operation = "版权申诉通过", type = OperateType.UPDATE, targetType = "mu_song_appeal", targetId = "#id")
    public Result<SongAppealView> approve(@PathVariable Long id,
                                          @RequestBody(required = false) AppealReviewCommand command,
                                          HttpServletRequest request) {
        return Result.ok(songAppealAppService.approve(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 申诉驳回：歌曲保持版权下架。
     *
     * @param id 申诉 ID
     * @param command 复核命令（审核意见可选）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 申诉视图
     */
    @PostMapping("/appeal/{id}/reject")
    @RequirePermission("sys:music:appeal:audit")
    @AuditLog(operation = "版权申诉驳回", type = OperateType.UPDATE, targetType = "mu_song_appeal", targetId = "#id")
    public Result<SongAppealView> reject(@PathVariable Long id,
                                         @RequestBody(required = false) AppealReviewCommand command,
                                         HttpServletRequest request) {
        return Result.ok(songAppealAppService.reject(id, command, UserContext.requireUserId(request)));
    }
}
