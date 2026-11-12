package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.SaveTenantConfigRequest;
import com.bluewhale.auth.interfaces.dto.response.TenantConfigEntryResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 租户配置 API 契约：租户管理员维护本租户的默认值层。
 *
 * <p>读取优先级约定「用户配置 &gt; 租户配置 &gt; 消费方默认」；本契约只维护中间层。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/tenant/v{version}/config", version = "1")
public interface TenantConfigApi {

    /**
     * 列出本租户的配置（可按前缀过滤）
     *
     * @param prefix 配置键前缀，空表示不过滤
     * @return 配置条目列表
     */
    @GetMapping
    Result<List<TenantConfigEntryResponse>> list(@RequestParam(required = false) String prefix);

    /**
     * upsert 一条租户配置
     *
     * @param request 当前请求（取操作人 ID）
     * @param body 保存请求
     * @return 成功
     */
    @PutMapping
    Result<Void> upsert(HttpServletRequest request, @Valid @RequestBody SaveTenantConfigRequest body);
}
