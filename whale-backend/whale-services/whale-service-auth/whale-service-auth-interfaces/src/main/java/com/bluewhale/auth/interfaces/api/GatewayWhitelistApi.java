package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.GatewayAccessRuleRequest;
import com.bluewhale.auth.interfaces.dto.response.GatewayAccessRuleResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 网关白名单管理 API 契约：管理端维护免鉴权路径。
 *
 * <p>网关在 60s 内自动拉取最新生效项，管理端写操作无需通知网关。
 * 权限码 {@code sys:gateway:whitelist:list/add/edit/delete}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/gateway/whitelist", version = "1")
public interface GatewayWhitelistApi {

    /**
     * 查询白名单列表（含停用项）
     *
     * @param status 状态过滤；为空表示全部
     * @return 白名单条目列表
     */
    @GetMapping
    Result<List<GatewayAccessRuleResponse>> list(@RequestParam(required = false) Integer status);

    /**
     * 新增白名单条目
     *
     * @param request 保存请求
     * @return 操作结果
     */
    @PostMapping
    Result<Void> create(@Valid @RequestBody GatewayAccessRuleRequest request);

    /**
     * 更新白名单条目
     *
     * @param id 主键
     * @param request 保存请求
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> update(@PathVariable Long id, @Valid @RequestBody GatewayAccessRuleRequest request);

    /**
     * 删除白名单条目
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
