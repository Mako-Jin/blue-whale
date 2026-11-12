package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.OAuthClientSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientResponse;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientSecretResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
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

/**
 * OAuth2 客户端 API 契约（权限中心扩展 M0）。
 *
 * <p>所有接口要求 {@code sys:oauth:client:*} 权限码；密钥明文仅在创建与轮换时返回一次。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/oauth-client/v{version}", version = "1")
public interface OAuthClientApi {

    /**
     * 分页查询客户端
     *
     * @param pageQuery 分页参数
     * @param keyword 客户端标识/名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 客户端分页结果
     */
    @GetMapping("/page")
    Result<PageResult<OAuthClientResponse>> page(PageQuery pageQuery,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status);

    /**
     * 注册客户端：密钥明文仅本次返回
     *
     * @param request 保存请求
     * @return 客户端标识与新密钥
     */
    @PostMapping
    Result<OAuthClientSecretResponse> create(@Valid @RequestBody OAuthClientSaveRequest request);

    /**
     * 编辑客户端配置
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的客户端
     */
    @PutMapping("/{id}")
    Result<OAuthClientResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody OAuthClientSaveRequest request);

    /**
     * 轮换客户端密钥：旧密钥立即失效，新密钥明文仅本次返回
     *
     * @param id 主键
     * @return 客户端标识与新密钥
     */
    @PostMapping("/{id}/secret")
    Result<OAuthClientSecretResponse> rotateSecret(@PathVariable Long id);

    /**
     * 停用客户端
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> disable(@PathVariable Long id);
}
