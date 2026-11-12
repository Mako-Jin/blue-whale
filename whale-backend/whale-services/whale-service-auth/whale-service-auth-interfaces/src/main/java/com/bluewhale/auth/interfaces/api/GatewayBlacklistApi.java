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
 * 网关黑名单管理 API 契约：管理端维护封禁路径。
 *
 * <p><b>注意</b>：黑名单优先于白名单，误封 {@code /auth/v1/login} 会导致登录不可用。
 * 网关在 60s 内自动拉取最新生效项。权限码 {@code sys:gateway:blacklist:list/add/edit/delete}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/gateway/blacklist", version = "1")
public interface GatewayBlacklistApi {

    /**
     * 查询黑名单列表（含已解除项）
     *
     * @param status 状态过滤；为空表示全部
     * @return 黑名单条目列表
     */
    @GetMapping
    Result<List<GatewayAccessRuleResponse>> list(@RequestParam(required = false) Integer status);

    /**
     * 新增黑名单条目
     *
     * @param request 保存请求
     * @return 操作结果
     */
    @PostMapping
    Result<Void> create(@Valid @RequestBody GatewayAccessRuleRequest request);

    /**
     * 更新黑名单条目
     *
     * @param id 主键
     * @param request 保存请求
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> update(@PathVariable Long id, @Valid @RequestBody GatewayAccessRuleRequest request);

    /**
     * 删除黑名单条目
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
