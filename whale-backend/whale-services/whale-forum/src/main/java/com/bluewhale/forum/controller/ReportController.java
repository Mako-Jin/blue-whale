package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.ReportCreateRequest;
import com.bluewhale.forum.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口（鸣鼓）：用户侧提交。
 *
 * <p>只需登录 + 浏览权限：举报是普通用户的权利，不应因为「没发过帖」就被拦住。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/report", version = "1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 提交举报。
     *
     * @param body 举报请求
     * @param request 当前请求
     * @return 工单ID（重复举报返回既有工单）
     */
    @PostMapping
    @RequirePermission(ForumPermissions.BROWSE)
    @AuditLog(operation = "提交论坛举报", type = OperateType.CREATE, targetType = "fm_report")
    public Result<Long> submit(@Valid @RequestBody ReportCreateRequest body, HttpServletRequest request) {
        return Result.ok(reportService.submit(body, ForumActor.from(request)));
    }
}
