package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.userconfig.UserConfigAppService;
import com.bluewhale.auth.application.userconfig.command.SaveUserConfigCommand;
import com.bluewhale.auth.interfaces.api.UserConfigApi;
import com.bluewhale.auth.interfaces.dto.SaveUserConfigRequest;
import com.bluewhale.auth.interfaces.dto.response.UserConfigResponse;
import com.bluewhale.auth.starter.assemble.ConfigAssembler;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户配置接口实现（Web 适配层）：登录用户自助管理自己的配置（无需权限码）。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserConfigController implements UserConfigApi {

    private final UserConfigAppService userConfigAppService;

    private final ConfigAssembler configAssembler;

    /**
     * 列出当前用户在本租户的配置（可按前缀过滤）
     *
     * @param request 当前请求（取用户 ID）
     * @param prefix 配置键前缀，空表示不过滤
     * @return 配置列表
     */
    @Override
    public Result<List<UserConfigResponse>> list(HttpServletRequest request,
                                                 @RequestParam(required = false) String prefix) {
        return Result.ok(configAssembler.toResponses(userConfigAppService.list(
                TenantContext.requireId(), UserContext.requireUserId(request), prefix)));
    }

    /**
     * upsert 一条当前用户的配置
     *
     * @param request 当前请求（取用户 ID）
     * @param body 保存请求
     * @return 成功
     */
    @Override
    public Result<Void> upsert(HttpServletRequest request,
                               @Valid @RequestBody SaveUserConfigRequest body) {
        Long userId = UserContext.requireUserId(request);
        userConfigAppService.upsert(TenantContext.requireId(), userId,
                new SaveUserConfigCommand(body.configKey(), body.configValue(), userId));
        return Result.ok();
    }

    /**
     * 删除当前用户的一条配置（删除后读取时回到租户默认/消费方默认）
     *
     * @param request 当前请求（取用户 ID）
     * @param configKey 配置键
     * @return 成功
     */
    @Override
    public Result<Void> delete(HttpServletRequest request, @PathVariable String configKey) {
        userConfigAppService.delete(TenantContext.requireId(),
                UserContext.requireUserId(request), configKey);
        return Result.ok();
    }
}
