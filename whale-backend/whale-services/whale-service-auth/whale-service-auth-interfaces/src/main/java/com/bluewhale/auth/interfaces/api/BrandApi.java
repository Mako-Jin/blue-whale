package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.BrandConfigSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.BrandConfigResponse;
import com.bluewhale.auth.interfaces.dto.response.BrandResolveResponse;
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
 * 品牌配置 API 契约（权限中心扩展 M0）。
 *
 * <p>{@code /resolve} 供登录页免登录调用（网关直连、不在鉴权链路）；其余管理接口要求
 * {@code sys:brand:*} 权限码。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/brand/v{version}", version = "1")
public interface BrandApi {

    /**
     * 登录页品牌解析（免登录）
     *
     * @param targetType 作用域类型（可空，默认 PLATFORM）
     * @param targetId 作用域 ID（可空，默认 0）
     * @param domain 请求域名（可空）
     * @return 品牌渲染信息
     */
    @GetMapping("/resolve")
    Result<BrandResolveResponse> resolve(@RequestParam(required = false) String targetType,
                                         @RequestParam(required = false) Long targetId,
                                         @RequestParam(required = false) String domain);

    /**
     * 分页查询品牌配置
     *
     * @param pageQuery 分页参数
     * @param keyword 品牌名称/域名模糊匹配关键字（可空）
     * @param targetType 作用域类型精确过滤（可空）
     * @param status 状态精确过滤（可空）
     * @return 品牌分页结果
     */
    @GetMapping("/page")
    Result<PageResult<BrandConfigResponse>> page(PageQuery pageQuery,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String targetType,
                                                 @RequestParam(required = false) Integer status);

    /**
     * 新增品牌配置
     *
     * @param request 保存请求
     * @return 新建品牌
     */
    @PostMapping
    Result<BrandConfigResponse> create(@Valid @RequestBody BrandConfigSaveRequest request);

    /**
     * 编辑品牌配置
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的品牌
     */
    @PutMapping("/{id}")
    Result<BrandConfigResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody BrandConfigSaveRequest request);

    /**
     * 删除品牌配置（逻辑删除）
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
