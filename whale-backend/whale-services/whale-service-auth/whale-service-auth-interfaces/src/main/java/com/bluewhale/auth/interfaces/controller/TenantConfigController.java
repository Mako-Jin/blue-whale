package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.userconfig.TenantConfigAppService;
import com.bluewhale.auth.application.userconfig.TenantConfigEntry;
import com.bluewhale.auth.application.userconfig.command.SaveTenantConfigCommand;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.interfaces.dto.SaveTenantConfigRequest;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户配置接口：租户管理员维护本租户的默认值层。
 *
 * <p>读取优先级约定「用户配置 &gt; 租户配置 &gt; 消费方默认」；本控制器只维护中间层。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/tenant/config")
@RequiredArgsConstructor
public class TenantConfigController {

    /** 租户配置应用服务 */
    private final TenantConfigAppService tenantConfigAppService;

    /**
     * 列出本租户的配置（可按前缀过滤）
     *
     * @param prefix 配置键前缀，空表示不过滤
     * @return 配置条目列表
     */
    @GetMapping
    @RequirePermission("sys:tenant:config:list")
    public Result<List<TenantConfigEntry>> list(@RequestParam(required = false) String prefix) {
        return Result.ok(tenantConfigAppService.list(TenantContext.requireId(), prefix));
    }

    /**
     * upsert 一条租户配置
     *
     * @param request 当前请求（取操作人 ID）
     * @param body    保存请求
     * @return 成功
     */
    @PutMapping
    @RequirePermission("sys:tenant:config:edit")
    public Result<Void> upsert(HttpServletRequest request,
                               @Valid @RequestBody SaveTenantConfigRequest body) {
        Long userId = UserContext.findUserId(request);
        tenantConfigAppService.upsert(TenantContext.requireId(),
                new SaveTenantConfigCommand(body.configKey(), body.configValue(), userId));
        return Result.ok();
    }
}
