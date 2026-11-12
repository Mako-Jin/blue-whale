package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.AssignPermissionsRequest;
import com.bluewhale.auth.interfaces.dto.RoleCreateRequest;
import com.bluewhale.auth.interfaces.dto.RoleUpdateRequest;
import com.bluewhale.auth.interfaces.dto.response.RoleResponse;
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

import java.util.List;

/**
 * 角色 API 契约：角色增删改查与权限分配。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/role/v{version}", version = "1")
public interface RoleApi {

    /**
     * 分页查询角色
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 角色编码或名称模糊匹配关键字（可空）
     * @return 角色分页结果
     */
    @GetMapping("/page")
    Result<PageResult<RoleResponse>> page(PageQuery pageQuery,
                                          @RequestParam(required = false) String keyword);

    /**
     * 角色详情
     *
     * @param id 角色 ID
     * @return 角色响应
     */
    @GetMapping("/{id}")
    Result<RoleResponse> detail(@PathVariable Long id);

    /**
     * 创建角色
     *
     * @param request 角色创建请求参数
     * @return 新建角色响应
     */
    @PostMapping
    Result<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request);

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param request 角色更新请求参数
     * @return 更新后的角色响应
     */
    @PutMapping("/{id}")
    Result<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request);

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 全量覆盖分配权限
     *
     * @param id 角色 ID
     * @param request 分配权限请求（含权限点 ID 列表）
     * @return 操作结果
     */
    @PostMapping("/{id}/permissions")
    Result<Void> assignPermissions(@PathVariable Long id,
                                   @Valid @RequestBody AssignPermissionsRequest request);

    /**
     * 查询角色已分配权限 ID
     *
     * @param id 角色 ID
     * @return 角色已分配的权限点 ID 列表
     */
    @GetMapping("/{id}/permissions")
    Result<List<Long>> permissionIds(@PathVariable Long id);
}
