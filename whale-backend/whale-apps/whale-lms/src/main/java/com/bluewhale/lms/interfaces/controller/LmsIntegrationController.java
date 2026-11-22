package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.integration.application.service.MemberSnapshotAppService;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import com.bluewhale.lms.integration.application.view.MemberSnapshotView;
import com.bluewhale.lms.integration.application.view.MemberSyncResultView;
import com.bluewhale.lms.integration.application.view.NotifyLogView;
import com.bluewhale.lms.integration.domain.model.MemberSnapshotQuery;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成接口（M5）：员工只读快照与通知投递日志。
 *
 * <p>这一组接口都是运维视角：快照用于判断「谁还在职、能不能触达」，
 * 通知日志用于回答「到底发没发、为什么没发出去」。二者都不承载业务动作。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/integration", version = "1")
@RequiredArgsConstructor
public class LmsIntegrationController {

    private final MemberSnapshotAppService memberSnapshotAppService;

    private final NotifyAppService notifyAppService;

    /**
     * 触发员工目录全量同步。
     *
     * @param request 当前请求
     * @return 同步结果
     */
    @PostMapping("/member/sync")
    @RequirePermission(LmsPermissions.MEMBER_MANAGE)
    @AuditLog(operation = "同步员工目录快照", type = OperateType.OTHER, targetType = "lr_member_snapshot")
    public Result<MemberSyncResultView> sync(HttpServletRequest request) {
        return Result.ok(memberSnapshotAppService.sync(actor(request)));
    }

    /**
     * 员工快照分页。
     *
     * @param keyword 关键字（员工编号 / 姓名）
     * @param orgCode 组织编码
     * @param status 在职状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/member/page")
    @RequirePermission(LmsPermissions.MEMBER_MANAGE)
    public Result<PageResult<MemberSnapshotView>> memberPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        MemberSnapshotQuery query = new MemberSnapshotQuery();
        query.setKeyword(keyword);
        query.setOrgCode(orgCode);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(memberSnapshotAppService.page(query, actor(request)));
    }

    /**
     * 按平台账号查询快照。
     *
     * @param userId 用户 ID
     * @return 快照；未收录时返回 null
     */
    @GetMapping("/member/user/{userId}")
    @RequirePermission(LmsPermissions.MEMBER_MANAGE)
    public Result<MemberSnapshotView> memberByUser(@PathVariable Long userId) {
        return Result.ok(memberSnapshotAppService.byUserId(userId));
    }

    /**
     * 通知投递日志分页。
     *
     * @param scene 场景
     * @param userId 接收人
     * @param sendStatus 投递状态
     * @param idempotencyKey 幂等键
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/notify/page")
    @RequirePermission(LmsPermissions.NOTIFY_LIST)
    public Result<PageResult<NotifyLogView>> notifyPage(
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sendStatus", required = false) String sendStatus,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        NotifyLogQuery query = new NotifyLogQuery();
        query.setScene(scene);
        query.setUserId(userId);
        query.setSendStatus(sendStatus);
        query.setIdempotencyKey(idempotencyKey);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(notifyAppService.page(query, actor(request)));
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
