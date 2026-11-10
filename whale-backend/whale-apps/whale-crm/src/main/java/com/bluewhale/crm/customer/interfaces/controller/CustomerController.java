package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.customer.application.command.CustomerImportCommand;
import com.bluewhale.crm.customer.application.command.CustomerLevelCommand;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerStatusCommand;
import com.bluewhale.crm.customer.application.command.ProfileSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.service.CustomerImportExportAppService;
import com.bluewhale.crm.customer.application.service.CustomerMergeAppService;
import com.bluewhale.crm.customer.application.view.Customer360View;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerImportPreviewView;
import com.bluewhale.crm.customer.application.view.CustomerImportResultView;
import com.bluewhale.crm.customer.application.view.CustomerMergeResultView;
import com.bluewhale.crm.customer.application.view.CustomerProfileView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.DuplicateCandidateView;
import com.bluewhale.crm.customer.domain.model.CustomerMergeCommand;
import com.bluewhale.crm.customer.domain.model.CustomerMergePreview;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.CustomerPageParams;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户主数据接口（M1）。
 *
 * <p>数据范围在应用层强制：普通销售只看本人负责客户，越权按 404 处理；
 * 手机 / 邮箱 / 税号等敏感字段按 {@code sys:crm:sensitive:view} 权限脱敏。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerAppService customerAppService;

    private final CustomerMergeAppService customerMergeAppService;

    private final CustomerImportExportAppService customerImportExportAppService;

    /**
     * 客户分页。
     *
     * @param params 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 客户分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.CUSTOMER_LIST)
    public Result<PageResult<CustomerView>> page(
            @ModelAttribute CustomerPageParams params,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CustomerQuery query = params.toQuery();
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(customerAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 客户查重。
     *
     * @param name 客户名称
     * @param website 官网
     * @param taxNo 税号
     * @param phone 联系人电话
     * @param email 联系人邮箱
     * @param excludeId 排除的客户 ID
     * @param request 当前请求
     * @return 查重候选
     */
    @GetMapping("/duplicate-check")
    @RequirePermission(CrmPermissions.CUSTOMER_DUPLICATE)
    public Result<PageResult<DuplicateCandidateView>> duplicateCheck(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "website", required = false) String website,
            @RequestParam(value = "taxNo", required = false) String taxNo,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "excludeId", required = false) Long excludeId,
            HttpServletRequest request) {
        return Result.ok(customerAppService.duplicateCheck(
                name, website, taxNo, phone, email, excludeId, CrmActorContext.from(request)));
    }

    /**
     * 客户合并预览（M1-4）。
     *
     * @param command 合并命令
     * @param request 当前请求
     * @return 合并预览
     */
    @PostMapping("/merge/preview")
    @RequirePermission(CrmPermissions.CUSTOMER_MERGE)
    public Result<CustomerMergePreview> mergePreview(@RequestBody CustomerMergeCommand command,
                                                      HttpServletRequest request) {
        return Result.ok(customerMergeAppService.preview(command, CrmActorContext.from(request)));
    }

    /**
     * 执行客户合并（M1-4）。
     *
     * @param command 合并命令
     * @param request 当前请求
     * @return 合并结果
     */
    @PostMapping("/merge")
    @RequirePermission(CrmPermissions.CUSTOMER_MERGE)
    @AuditLog(operation = "合并客户", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#command.targetId")
    public Result<CustomerMergeResultView> merge(@RequestBody CustomerMergeCommand command,
                                                 HttpServletRequest request) {
        return Result.ok(customerMergeAppService.merge(command, CrmActorContext.from(request)));
    }

    /**
     * CSV 导入预检（M1-9）。
     *
     * @param command 导入内容
     * @param request 当前请求
     * @return 预检结果
     */
    @PostMapping("/import/preview")
    @RequirePermission(CrmPermissions.CUSTOMER_IMPORT)
    public Result<CustomerImportPreviewView> importPreview(@Valid @RequestBody CustomerImportCommand command,
                                                           HttpServletRequest request) {
        return Result.ok(customerImportExportAppService.preview(command.getContent(),
                CrmActorContext.from(request)));
    }

    /**
     * 执行 CSV 导入（M1-9）。
     *
     * @param command 导入内容
     * @param request 当前请求
     * @return 导入结果
     */
    @PostMapping("/import/execute")
    @RequirePermission(CrmPermissions.CUSTOMER_IMPORT)
    @AuditLog(operation = "导入客户", type = OperateType.IMPORT,
            targetType = "crm_customer", recordParams = false)
    public Result<CustomerImportResultView> importExecute(@Valid @RequestBody CustomerImportCommand command,
                                                         HttpServletRequest request) {
        return Result.ok(customerImportExportAppService.execute(command.getContent(),
                CrmActorContext.from(request)));
    }

    /**
     * 导出客户 CSV（M1-9）。
     *
     * @param params 查询条件
     * @param request 当前请求
     * @return CSV 内容
     */
    @GetMapping("/export")
    @RequirePermission(CrmPermissions.CUSTOMER_EXPORT)
    @AuditLog(operation = "导出客户", type = OperateType.EXPORT, targetType = "crm_customer")
    public Result<String> export(@ModelAttribute CustomerPageParams params, HttpServletRequest request) {
        return Result.ok(customerImportExportAppService.export(params.toQuery(),
                CrmActorContext.from(request)));
    }

    /**
     * 客户详情。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 客户详情
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.CUSTOMER_LIST)
    public Result<CustomerDetailView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(customerAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 客户 360。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 360 视图
     */
    @GetMapping("/{id}/360")
    @RequirePermission(CrmPermissions.CUSTOMER_LIST)
    public Result<Customer360View> customer360(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(customerAppService.customer360(id, CrmActorContext.from(request)));
    }

    /**
     * 新建客户。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 客户视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.CUSTOMER_CREATE)
    @AuditLog(operation = "新建客户", type = OperateType.CREATE, targetType = "crm_customer")
    public Result<CustomerView> create(@Valid @RequestBody CustomerSaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(customerAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑客户。
     *
     * @param id 客户 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 客户视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.CUSTOMER_UPDATE)
    @AuditLog(operation = "编辑客户", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody CustomerSaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(customerAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 变更客户状态。
     *
     * @param id 客户 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 客户视图
     */
    @PutMapping("/{id}/status")
    @RequirePermission(CrmPermissions.CUSTOMER_STATUS)
    @AuditLog(operation = "变更客户状态", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> changeStatus(@PathVariable("id") Long id,
                                             @Valid @RequestBody CustomerStatusCommand command,
                                             HttpServletRequest request) {
        return Result.ok(customerAppService.changeStatus(id, command, CrmActorContext.from(request)));
    }

    /**
     * 变更客户等级。
     *
     * @param id 客户 ID
     * @param command 等级命令
     * @param request 当前请求
     * @return 客户视图
     */
    @PutMapping("/{id}/level")
    @RequirePermission(CrmPermissions.CUSTOMER_LEVEL)
    @AuditLog(operation = "变更客户等级", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> changeLevel(@PathVariable("id") Long id,
                                            @Valid @RequestBody CustomerLevelCommand command,
                                            HttpServletRequest request) {
        return Result.ok(customerAppService.changeLevel(id, command, CrmActorContext.from(request)));
    }

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(CrmPermissions.CUSTOMER_DELETE)
    @AuditLog(operation = "删除客户", type = OperateType.DELETE,
            targetType = "crm_customer", targetId = "#id")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        customerAppService.delete(id, CrmActorContext.from(request));
        return Result.ok();
    }

    /**
     * 查询客户画像。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 画像视图
     */
    @GetMapping("/{id}/profile")
    @RequirePermission(CrmPermissions.PROFILE_VIEW)
    public Result<CustomerProfileView> getProfile(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(customerAppService.getProfile(id, CrmActorContext.from(request)));
    }

    /**
     * 保存客户画像。
     *
     * @param id 客户 ID
     * @param command 画像命令
     * @param request 当前请求
     * @return 画像视图
     */
    @PutMapping("/{id}/profile")
    @RequirePermission(CrmPermissions.PROFILE_SAVE)
    @AuditLog(operation = "保存客户画像", type = OperateType.UPDATE,
            targetType = "crm_customer_profile", targetId = "#id", recordParams = false)
    public Result<CustomerProfileView> saveProfile(@PathVariable("id") Long id,
                                                   @RequestBody ProfileSaveCommand command,
                                                   HttpServletRequest request) {
        return Result.ok(customerAppService.saveProfile(id, command, CrmActorContext.from(request)));
    }
}
