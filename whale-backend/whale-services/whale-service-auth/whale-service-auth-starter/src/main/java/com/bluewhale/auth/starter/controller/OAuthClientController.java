package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveOAuthClientCommand;
import com.bluewhale.auth.application.model.query.OAuthClientPageQuery;
import com.bluewhale.auth.application.service.OAuthClientAppService;
import com.bluewhale.auth.interfaces.api.OAuthClientApi;
import com.bluewhale.auth.interfaces.dto.request.OAuthClientSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientResponse;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientSecretResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.OAuthClientAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 客户端接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class OAuthClientController implements OAuthClientApi {

    private final OAuthClientAppService oauthClientAppService;
    private final OAuthClientAssembler oauthClientAssembler;

    /**
     * 分页查询客户端
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字
     * @param status 状态
     * @return 客户端分页结果
     */
    @Override
    @RequirePermission("sys:oauth:client:list")
    public Result<PageResult<OAuthClientResponse>> page(PageQuery pageQuery,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status) {
        return Result.ok(oauthClientAssembler.toPage(oauthClientAppService.page(new OAuthClientPageQuery(
                pageQuery.getCurrent(), pageQuery.getSize(), keyword, status))));
    }

    /**
     * 注册客户端：密钥明文仅本次返回
     *
     * @param request 保存请求
     * @return 客户端标识与新密钥
     */
    @Override
    @RequirePermission("sys:oauth:client:add")
    @AuditLog(operation = "注册 OAuth 客户端", type = OperateType.CREATE, targetType = "sys_oauth_client",
            recordParams = false)
    public Result<OAuthClientSecretResponse> create(@Valid @RequestBody OAuthClientSaveRequest request) {
        SaveOAuthClientCommand command = oauthClientAssembler.toCommand(request);
        return Result.ok(oauthClientAssembler.toSecretResponse(oauthClientAppService.create(command)));
    }

    /**
     * 编辑客户端配置
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的客户端
     */
    @Override
    @RequirePermission("sys:oauth:client:edit")
    @AuditLog(operation = "编辑 OAuth 客户端", type = OperateType.UPDATE, targetType = "sys_oauth_client",
            targetId = "#id", recordParams = false)
    public Result<OAuthClientResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody OAuthClientSaveRequest request) {
        SaveOAuthClientCommand command = oauthClientAssembler.toCommand(request);
        return Result.ok(oauthClientAssembler.toResponse(oauthClientAppService.update(id, command)));
    }

    /**
     * 轮换客户端密钥：旧密钥立即失效
     *
     * @param id 主键
     * @return 客户端标识与新密钥
     */
    @Override
    @RequirePermission("sys:oauth:client:secret")
    @AuditLog(operation = "轮换 OAuth 客户端密钥", type = OperateType.UPDATE,
            targetType = "sys_oauth_client", targetId = "#id", recordParams = false)
    public Result<OAuthClientSecretResponse> rotateSecret(@PathVariable Long id) {
        return Result.ok(oauthClientAssembler.toSecretResponse(oauthClientAppService.rotateSecret(id)));
    }

    /**
     * 停用客户端
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:oauth:client:delete")
    @AuditLog(operation = "停用 OAuth 客户端", type = OperateType.UPDATE, targetType = "sys_oauth_client",
            targetId = "#id")
    public Result<Void> disable(@PathVariable Long id) {
        oauthClientAppService.disable(id);
        return Result.ok();
    }
}
