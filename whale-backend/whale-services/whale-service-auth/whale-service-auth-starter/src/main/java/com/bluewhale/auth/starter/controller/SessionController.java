package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.query.SessionPageQuery;
import com.bluewhale.auth.application.service.SessionManageAppService;
import com.bluewhale.auth.interfaces.api.SessionApi;
import com.bluewhale.auth.interfaces.dto.response.SessionResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.SessionAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理接口实现（Web 适配层）：协议适配 + 查询参数装配，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class SessionController implements SessionApi {

    private final SessionManageAppService sessionManageAppService;
    private final SessionAssembler sessionAssembler;

    /**
     * 分页查询用户会话（refreshToken 记录账本）
     *
     * @param pageQuery 分页参数
     * @param userId 用户 ID
     * @param onlyActive 仅未撤销会话
     * @return 会话分页结果
     */
    @Override
    @RequirePermission("sys:session:list")
    public Result<PageResult<SessionResponse>> page(PageQuery pageQuery,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false, defaultValue = "false") boolean onlyActive) {
        return Result.ok(sessionAssembler.toPage(sessionManageAppService.pageSessions(new SessionPageQuery(
                pageQuery.getCurrent(), pageQuery.getSize(), userId, onlyActive))));
    }

    /**
     * 强制下线指定会话（按 jti）：账本标记 revoked 并写入用户撤销通道即时生效
     *
     * @param jti 会话令牌唯一标识
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:session:revoke")
    @AuditLog(operation = "强制下线会话", type = OperateType.UPDATE, targetType = "sys_refresh_token",
            targetId = "#jti")
    public Result<Void> revoke(@PathVariable String jti) {
        sessionManageAppService.revokeSession(jti);
        return Result.ok();
    }
}
