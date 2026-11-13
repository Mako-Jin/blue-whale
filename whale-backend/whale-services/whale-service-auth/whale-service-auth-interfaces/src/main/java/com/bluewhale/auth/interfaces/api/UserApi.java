package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.AssignRolesRequest;
import com.bluewhale.auth.interfaces.dto.request.BindUserTenantRequest;
import com.bluewhale.auth.interfaces.dto.request.UpdateAttrsRequest;
import com.bluewhale.auth.interfaces.dto.request.UserCreateRequest;
import com.bluewhale.auth.interfaces.dto.request.UserStatusRequest;
import com.bluewhale.auth.interfaces.dto.request.UserUpdateRequest;
import com.bluewhale.auth.interfaces.dto.response.ImportCommitResponse;
import com.bluewhale.auth.interfaces.dto.response.ImportPreviewResponse;
import com.bluewhale.auth.interfaces.dto.response.ResetPasswordResponse;
import com.bluewhale.auth.interfaces.dto.response.UserAttrResponse;
import com.bluewhale.auth.interfaces.dto.response.UserResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户 API 契约：用户 CRUD / 分配角色 / ABAC 属性读写 / 租户绑定。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/user/v{version}", version = "1")
public interface UserApi {

    /**
     * 分页查询用户
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    @GetMapping("/page")
    Result<PageResult<UserResponse>> page(PageQuery pageQuery,
                                          @RequestParam(required = false) String keyword);

    /**
     * 用户详情
     *
     * @param id 用户 ID
     * @return 用户响应（不含密码字段）
     */
    @GetMapping("/{id}")
    Result<UserResponse> detail(@PathVariable Long id);

    /**
     * 创建用户
     *
     * @param request 用户创建请求参数
     * @return 新建用户响应
     */
    @PostMapping
    Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request);

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param request 用户更新请求参数
     * @return 更新后的用户响应
     */
    @PutMapping("/{id}")
    Result<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request);

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 重置用户口令：系统生成一次性随机临时口令并返回（REQ-SEC-023）
     *
     * @param id 用户 ID
     * @return 一次性临时口令与目标用户信息
     */
    @PutMapping("/{id}/password")
    Result<ResetPasswordResponse> resetPassword(@PathVariable Long id);

    /**
     * 启停用户
     *
     * @param id 用户 ID
     * @param request 状态变更请求（含目标状态）
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request);

    /**
     * 全量覆盖分配角色
     *
     * @param id 用户 ID
     * @param request 分配角色请求（含角色 ID 列表）
     * @return 操作结果
     */
    @PostMapping("/{id}/roles")
    Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request);

    /**
     * 查询用户已分配角色 ID
     *
     * @param id 用户 ID
     * @return 用户已分配的角色 ID 列表
     */
    @GetMapping("/{id}/roles")
    Result<List<Long>> roleIds(@PathVariable Long id);

    /**
     * ABAC 属性读取
     *
     * @param id 用户 ID
     * @return 用户 ABAC 属性列表
     */
    @GetMapping("/{id}/attrs")
    Result<List<UserAttrResponse>> attrs(@PathVariable Long id);

    /**
     * ABAC 属性写入（全量覆盖）
     *
     * @param id 用户 ID
     * @param request 属性写入请求（全量覆盖）
     * @return 操作结果
     */
    @PutMapping("/{id}/attrs")
    Result<Void> updateAttrs(@PathVariable Long id, @Valid @RequestBody UpdateAttrsRequest request);

    /**
     * 为用户绑定可进入的租户（仅平台租户）
     *
     * @param id 用户 ID
     * @param request 绑定请求（含目标租户 ID）
     * @return 操作结果
     */
    @PostMapping("/{id}/tenants")
    Result<Void> bindTenant(@PathVariable Long id, @Valid @RequestBody BindUserTenantRequest request);

    /**
     * 解除用户与租户的关系（仅平台租户；首页关系禁止删除）
     *
     * @param id 用户 ID
     * @param tenantId 目标租户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/tenants/{tenantId}")
    Result<Void> unbindTenant(@PathVariable Long id, @PathVariable Long tenantId);

    /**
     * 下载用户导入模板（权限中心扩展 M4）
     *
     * @return 模板 xlsx 字节流
     */
    @GetMapping("/import/template")
    ResponseEntity<byte[]> importTemplate();

    /**
     * 导入预检：上传 xlsx 全量校验，返回批次 ID 与行级错误清单（权限中心扩展 M4）
     *
     * @param file 用户导入文件（xlsx）
     * @return 预检结果（存在错误时 batchId 为 {@code null}）
     */
    @PostMapping(value = "/import/preview", consumes = "multipart/form-data")
    Result<ImportPreviewResponse> importPreview(@RequestParam("file") MultipartFile file);

    /**
     * 导入提交：按预检批次分批写入，返回成功/跳过/失败行数与统一临时口令（权限中心扩展 M4）
     *
     * @param batchId 预检返回的批次 ID
     * @return 提交结果
     */
    @PostMapping("/import/commit")
    Result<ImportCommitResponse> importCommit(@RequestParam String batchId);

    /**
     * 导出用户：≤5 万行返回 xlsx 字节流，超过返回 whale-file 文件引用（权限中心扩展 M4）
     *
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 直传分支为 xlsx 字节流；上传分支为 {@code Result<Map>}（含 {@code objectKey}）
     */
    @PostMapping("/export")
    ResponseEntity<?> export(@RequestParam(required = false) String keyword);
}
