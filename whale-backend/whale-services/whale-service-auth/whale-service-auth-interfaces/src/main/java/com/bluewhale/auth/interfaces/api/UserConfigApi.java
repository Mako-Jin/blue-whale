package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.SaveUserConfigRequest;
import com.bluewhale.auth.interfaces.dto.response.UserConfigResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户配置 API 契约：登录用户自助管理自己的配置（无需权限码）。
 *
 * <p>配置按当前租户隔离；网关经 {@code /auth/v1/**} 转发到本服务，路径必须自带版本段。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/user-config", version = "1")
public interface UserConfigApi {

    /**
     * 列出当前用户在本租户的配置（可按前缀过滤）
     *
     * @param request 当前请求（取用户 ID）
     * @param prefix 配置键前缀，空表示不过滤
     * @return 配置列表
     */
    @GetMapping
    Result<List<UserConfigResponse>> list(HttpServletRequest request,
                                          @RequestParam(required = false) String prefix);

    /**
     * upsert 一条当前用户的配置
     *
     * @param request 当前请求（取用户 ID）
     * @param body 保存请求
     * @return 成功
     */
    @PutMapping
    Result<Void> upsert(HttpServletRequest request, @Valid @RequestBody SaveUserConfigRequest body);

    /**
     * 删除当前用户的一条配置（删除后读取时回到租户默认/消费方默认）
     *
     * @param request 当前请求（取用户 ID）
     * @param configKey 配置键
     * @return 成功
     */
    @DeleteMapping("/{configKey}")
    Result<Void> delete(HttpServletRequest request, @PathVariable String configKey);
}
