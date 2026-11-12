package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.PermissionRequest;
import com.bluewhale.auth.interfaces.dto.response.PermissionResponse;
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
 * 权限点 API 契约：权限点的增删改查与分页。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/permission/v{version}", version = "1")
public interface PermissionApi {

    /**
     * 分页查询权限点
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 权限编码或名称模糊匹配关键字（可空）
     * @param permType 权限类型精确过滤条件（可空）
     * @return 权限点分页结果
     */
    @GetMapping("/page")
    Result<PageResult<PermissionResponse>> page(PageQuery pageQuery,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String permType);

    /**
     * 权限点详情
     *
     * @param id 权限点 ID
     * @return 权限点响应
     */
    @GetMapping("/{id}")
    Result<PermissionResponse> detail(@PathVariable Long id);

    /**
     * 创建权限点
     *
     * @param request 权限点请求参数
     * @return 操作结果
     */
    @PostMapping
    Result<Void> create(@Valid @RequestBody PermissionRequest request);

    /**
     * 更新权限点
     *
     * @param id 权限点 ID
     * @param request 权限点请求参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request);

    /**
     * 删除权限点
     *
     * @param id 权限点 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
