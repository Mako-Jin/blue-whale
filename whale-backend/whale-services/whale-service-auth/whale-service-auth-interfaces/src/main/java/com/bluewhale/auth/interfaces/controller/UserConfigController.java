package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.userconfig.UserConfigAppService;
import com.bluewhale.auth.application.userconfig.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.userconfig.vo.UserConfigVO;
import com.bluewhale.auth.interfaces.dto.SaveUserConfigRequest;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户配置接口：登录用户自助管理自己的配置（无需权限码）。
 *
 * <p>配置按当前租户隔离；网关侧经 {@code /auth/v1/user-config} 转发到本控制器。
 * 配置语义由 config_key 所属业务域定义，auth 只做存储口径校验（键格式、JSON、大小）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user-config")
@RequiredArgsConstructor
public class UserConfigController {

    /** 用户配置应用服务 */
    private final UserConfigAppService userConfigAppService;

    /**
     * 列出当前用户在本租户的配置（可按前缀过滤）
     *
     * @param request 当前请求（取用户 ID）
     * @param prefix  配置键前缀，空表示不过滤
     * @return 配置列表
     */
    @GetMapping
    public Result<List<UserConfigVO>> list(jakarta.servlet.http.HttpServletRequest request,
                                           @RequestParam(required = false) String prefix) {
        return Result.ok(userConfigAppService.list(
                TenantContext.requireId(), UserContext.requireUserId(request), prefix));
    }

    /**
     * upsert 一条当前用户的配置
     *
     * @param request 当前请求（取用户 ID）
     * @param body    保存请求
     * @return 成功
     */
    @PutMapping
    public Result<Void> upsert(jakarta.servlet.http.HttpServletRequest request,
                               @Valid @RequestBody SaveUserConfigRequest body) {
        Long userId = UserContext.requireUserId(request);
        userConfigAppService.upsert(TenantContext.requireId(), userId,
                new SaveUserConfigCommand(body.configKey(), body.configValue(), userId));
        return Result.ok();
    }

    /**
     * 删除当前用户的一条配置（删除后读取时回到租户默认/消费方默认）
     *
     * @param request   当前请求（取用户 ID）
     * @param configKey 配置键
     * @return 成功
     */
    @DeleteMapping("/{configKey}")
    public Result<Void> delete(jakarta.servlet.http.HttpServletRequest request,
                               @PathVariable String configKey) {
        userConfigAppService.delete(TenantContext.requireId(),
                UserContext.requireUserId(request), configKey);
        return Result.ok();
    }
}
