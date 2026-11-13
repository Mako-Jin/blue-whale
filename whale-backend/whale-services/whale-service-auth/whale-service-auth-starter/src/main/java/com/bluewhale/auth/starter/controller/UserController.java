package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.UserAppService;
import com.bluewhale.auth.application.model.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.model.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.model.command.CreateUserCommand;
import com.bluewhale.auth.application.model.command.UpdateUserCommand;
import com.bluewhale.auth.application.model.query.UserPageQuery;
import com.bluewhale.auth.application.service.UserPermissionAppService;
import com.bluewhale.auth.application.model.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.application.model.importexport.ExportResult;
import com.bluewhale.auth.application.model.importexport.ImportPreviewResult;
import com.bluewhale.auth.application.model.importexport.ImportRowError;
import com.bluewhale.auth.application.model.importexport.ImportCommitResult;
import com.bluewhale.auth.application.service.UserImportExportAppService;
import com.bluewhale.auth.interfaces.api.UserApi;
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
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.sdk.annotation.RequireReauth;
import com.bluewhale.auth.starter.assemble.UserAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.idempotent.annotation.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 用户接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserAppService userAppService;
    private final UserPermissionAppService userPermissionAppService;
    private final TenantMembershipAppService tenantMembershipAppService;
    private final UserImportExportAppService userImportExportAppService;
    private final UserAssembler userAssembler;

    /**
     * 分页查询用户
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    @Override
    @RequirePermission("sys:user:list")
    public Result<PageResult<UserResponse>> page(PageQuery pageQuery,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(userAssembler.toPage(userAppService.page(
                new UserPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword))));
    }

    /**
     * 用户详情
     *
     * @param id 用户 ID
     * @return 用户响应（不含密码字段）
     */
    @Override
    @RequirePermission("sys:user:list")
    public Result<UserResponse> detail(@PathVariable Long id) {
        return Result.ok(userAssembler.toResponse(userAppService.getDetail(id)));
    }

    /**
     * 创建用户：参数含密码，落库前由脱敏器置为掩码
     *
     * @param request 用户创建请求参数
     * @return 新建用户响应
     */
    @Override
    @RequirePermission("sys:user:add")
    @AuditLog(operation = "创建用户", type = OperateType.CREATE, targetType = "sys_user")
    @Idempotent(biz = "auth:user:create", key = "#request.username")
    public Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userAssembler.toResponse(userAppService.create(new CreateUserCommand(
                request.getUsername(), request.getPassword(), request.getNickname(), request.getPhone(),
                request.getEmail(), request.getAvatar(), request.getDeptId(), request.getStatus()))));
    }

    /**
     * 更新用户：参数可能含密码，落库前由脱敏器置为掩码
     *
     * @param id 用户 ID
     * @param request 用户更新请求参数
     * @return 更新后的用户响应
     */
    @Override
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<UserResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userAssembler.toResponse(userAppService.update(id, new UpdateUserCommand(
                request.getPassword(), request.getNickname(), request.getPhone(), request.getEmail(),
                request.getAvatar(), request.getDeptId(), request.getStatus()))));
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:delete")
    @RequireReauth
    @AuditLog(operation = "删除用户", type = OperateType.DELETE, targetType = "sys_user", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        userAppService.delete(id);
        return Result.ok();
    }

    /**
     * 重置用户密码：系统生成一次性随机临时口令并返回（REQ-SEC-023）
     *
     * @param id 用户 ID
     * @return 一次性临时口令与目标用户信息
     */
    @Override
    @RequirePermission("sys:user:resetpwd")
    @RequireReauth
    @AuditLog(operation = "重置用户密码", type = OperateType.UPDATE, targetType = "sys_user",
            targetId = "#id", recordParams = false)
    public Result<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return Result.ok(userAssembler.toResetPasswordResponse(userAppService.resetPassword(id)));
    }

    /**
     * 启停用户：变更账号状态属敏感操作，必留痕
     *
     * @param id 用户 ID
     * @param request 状态变更请求（含目标状态）
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:status")
    @RequireReauth
    @AuditLog(operation = "启停用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusRequest request) {
        userAppService.changeStatus(id, new ChangeUserStatusCommand(request.getStatus()));
        return Result.ok();
    }

    /**
     * 全量覆盖分配角色：授权变更属敏感操作，必留痕
     *
     * @param id 用户 ID
     * @param request 分配角色请求（含角色 ID 列表）
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:assign")
    @AuditLog(operation = "分配用户角色", type = OperateType.GRANT, targetType = "sys_user", targetId = "#id")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        userAppService.assignRoles(id, new AssignUserRolesCommand(request.getRoleIds()));
        return Result.ok();
    }

    /**
     * 查询用户已分配角色 ID
     *
     * @param id 用户 ID
     * @return 用户已分配的角色 ID 列表
     */
    @Override
    @RequirePermission("sys:user:list")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(userAppService.listRoleIds(id));
    }

    /**
     * ABAC 属性读取
     *
     * @param id 用户 ID
     * @return 用户 ABAC 属性列表
     */
    @Override
    @RequirePermission("sys:user:list")
    public Result<List<UserAttrResponse>> attrs(@PathVariable Long id) {
        return Result.ok(userAssembler.toAttrResponses(userPermissionAppService.listByUserId(id)));
    }

    /**
     * ABAC 属性写入（全量覆盖）：影响数据范围判定，属敏感操作必留痕
     *
     * @param id 用户 ID
     * @param request 属性写入请求（全量覆盖）
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户属性", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> updateAttrs(@PathVariable Long id,
                                    @Valid @RequestBody UpdateAttrsRequest request) {
        List<ReplaceUserAttrsCommand.UserAttrCommand> attrs = request.getAttrs().stream()
                .map(item -> new ReplaceUserAttrsCommand.UserAttrCommand(
                        item.getPermissionId(), item.getAttrKey(), item.getAttrValue()))
                .toList();
        userPermissionAppService.replaceUserAttrs(id, new ReplaceUserAttrsCommand(attrs));
        return Result.ok();
    }

    /**
     * 为用户绑定可进入的租户（Wave 5.54 Phase 5.3，仅平台租户）
     *
     * @param id 用户 ID
     * @param request 绑定请求（含目标租户 ID）
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:tenant")
    @AuditLog(operation = "绑定用户租户", type = OperateType.GRANT, targetType = "sys_user", targetId = "#id")
    public Result<Void> bindTenant(@PathVariable Long id,
                                   @Valid @RequestBody BindUserTenantRequest request) {
        tenantMembershipAppService.bindTenant(id, request.getTenantId());
        return Result.ok();
    }

    /**
     * 解除用户与租户的关系（Wave 5.54 Phase 5.3，仅平台租户；首页关系禁止删除）
     *
     * @param id 用户 ID
     * @param tenantId 目标租户 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:tenant")
    @AuditLog(operation = "解绑用户租户", type = OperateType.DELETE, targetType = "sys_user", targetId = "#id")
    public Result<Void> unbindTenant(@PathVariable Long id, @PathVariable Long tenantId) {
        tenantMembershipAppService.unbindTenant(id, tenantId);
        return Result.ok();
    }

    /**
     * 下载用户导入模板（权限中心扩展 M4）
     *
     * @return 模板 xlsx 字节流
     */
    @Override
    @RequirePermission("sys:user:import")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-import-template.xlsx")
                .contentType(XLSX_MEDIA_TYPE)
                .body(userImportExportAppService.template());
    }

    /**
     * 导入预检：参数含上传文件，审计不记录请求参数（权限中心扩展 M4）
     *
     * @param file 用户导入文件（xlsx）
     * @return 预检结果（存在错误时 batchId 为 {@code null}）
     */
    @Override
    @RequirePermission("sys:user:import")
    @AuditLog(operation = "导入用户预检", type = OperateType.IMPORT, recordParams = false)
    public Result<ImportPreviewResponse> importPreview(@RequestParam("file") MultipartFile file) {
        ImportPreviewResult result = userImportExportAppService.preview(toInputStream(file));
        List<ImportPreviewResponse.RowError> errors = result.errors().stream()
                .map(this::toRowError).toList();
        return Result.ok(new ImportPreviewResponse(result.batchId(), result.totalRows(),
                result.validCount(), errors));
    }

    /**
     * 导入提交：统一临时口令为敏感信息，审计不记录请求参数（权限中心扩展 M4）
     *
     * @param batchId 预检返回的批次 ID
     * @return 提交结果
     */
    @Override
    @RequirePermission("sys:user:import")
    @AuditLog(operation = "导入用户提交", type = OperateType.IMPORT, recordParams = false)
    public Result<ImportCommitResponse> importCommit(@RequestParam String batchId) {
        ImportCommitResult result = userImportExportAppService.commit(batchId);
        return Result.ok(new ImportCommitResponse(result.totalRows(), result.successCount(),
                result.skippedCount(), result.failedCount(), result.tempPassword()));
    }

    /**
     * 导出用户：≤5 万行直传 xlsx，超限返回 whale-file 文件引用（权限中心扩展 M4）
     *
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 直传分支为 xlsx 字节流；上传分支为 {@code Result<Map>}（含 {@code objectKey}）
     */
    @Override
    @RequirePermission("sys:user:export")
    @AuditLog(operation = "导出用户", type = OperateType.EXPORT, recordParams = false)
    public ResponseEntity<?> export(@RequestParam(required = false) String keyword) {
        ExportResult result = userImportExportAppService.export(keyword);
        if (result.content() != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.xlsx")
                    .contentType(XLSX_MEDIA_TYPE)
                    .body(result.content());
        }
        return ResponseEntity.ok(Result.ok(result.remoteFile()));
    }

    /** xlsx 媒体类型（模板与导出共用） */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /**
     * 上传文件 → 输入流（读取失败转为受控业务异常）
     */
    private static java.io.InputStream toInputStream(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请上传用户导入文件");
        }
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "读取上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 行级错误 → 响应项
     */
    private ImportPreviewResponse.RowError toRowError(ImportRowError error) {
        return new ImportPreviewResponse.RowError(error.rowNo(), error.column(), error.message());
    }
}
