package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.userconfig.TenantConfigAppService;
import com.bluewhale.auth.application.userconfig.command.SaveTenantConfigCommand;
import com.bluewhale.auth.interfaces.api.TenantConfigApi;
import com.bluewhale.auth.interfaces.dto.SaveTenantConfigRequest;
import com.bluewhale.auth.interfaces.dto.response.TenantConfigEntryResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.ConfigAssembler;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户配置接口实现（Web 适配层）：租户管理员维护本租户的默认值层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class TenantConfigController implements TenantConfigApi {

    private final TenantConfigAppService tenantConfigAppService;

    private final ConfigAssembler configAssembler;

    /**
     * 列出本租户的配置（可按前缀过滤）
     *
     * @param prefix 配置键前缀，空表示不过滤
     * @return 配置条目列表
     */
    @Override
    @RequirePermission("sys:tenant:config:list")
    public Result<List<TenantConfigEntryResponse>> list(@RequestParam(required = false) String prefix) {
        return Result.ok(configAssembler.toTenantConfigResponses(
                tenantConfigAppService.list(TenantContext.requireId(), prefix)));
    }

    /**
     * upsert 一条租户配置
     *
     * @param request 当前请求（取操作人 ID）
     * @param body 保存请求
     * @return 成功
     */
    @Override
    @RequirePermission("sys:tenant:config:edit")
    public Result<Void> upsert(HttpServletRequest request,
                               @Valid @RequestBody SaveTenantConfigRequest body) {
        Long userId = UserContext.findUserId(request);
        tenantConfigAppService.upsert(TenantContext.requireId(),
                new SaveTenantConfigCommand(body.configKey(), body.configValue(), userId));
        return Result.ok();
    }
}
