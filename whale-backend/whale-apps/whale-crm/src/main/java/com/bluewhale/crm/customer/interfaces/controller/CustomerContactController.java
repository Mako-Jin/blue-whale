package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.customer.application.command.AddressSaveCommand;
import com.bluewhale.crm.customer.application.command.ContactSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.CustomerAddressView;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户联系人 / 地址接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/customer", version = "1")
@RequiredArgsConstructor
public class CustomerContactController {

    private final CustomerAppService customerAppService;

    /**
     * 查询客户联系人。
     *
     * @param customerId 客户 ID
     * @param request 当前请求
     * @return 联系人列表
     */
    @GetMapping("/{customerId}/contact")
    @RequirePermission(CrmPermissions.CONTACT_LIST)
    public Result<List<ContactView>> listContacts(@PathVariable("customerId") Long customerId,
                                                  HttpServletRequest request) {
        return Result.ok(customerAppService.listContacts(customerId, CrmActorContext.from(request)));
    }

    /**
     * 新建联系人。
     *
     * @param customerId 客户 ID
     * @param command 联系人命令
     * @param request 当前请求
     * @return 联系人视图
     */
    @PostMapping("/{customerId}/contact")
    @RequirePermission(CrmPermissions.CONTACT_MANAGE)
    @AuditLog(operation = "新建联系人", type = OperateType.CREATE,
            targetType = "crm_contact", targetId = "#customerId", recordParams = false)
    public Result<ContactView> createContact(@PathVariable("customerId") Long customerId,
                                             @Valid @RequestBody ContactSaveCommand command,
                                             HttpServletRequest request) {
        return Result.ok(customerAppService.createContact(
                customerId, command, CrmActorContext.from(request)));
    }

    /**
     * 编辑联系人。
     *
     * @param contactId 联系人 ID
     * @param command 联系人命令
     * @param request 当前请求
     * @return 联系人视图
     */
    @PutMapping("/contact/{contactId}")
    @RequirePermission(CrmPermissions.CONTACT_MANAGE)
    @AuditLog(operation = "编辑联系人", type = OperateType.UPDATE,
            targetType = "crm_contact", targetId = "#contactId", recordParams = false)
    public Result<ContactView> updateContact(@PathVariable("contactId") Long contactId,
                                             @Valid @RequestBody ContactSaveCommand command,
                                             HttpServletRequest request) {
        return Result.ok(customerAppService.updateContact(
                contactId, command, CrmActorContext.from(request)));
    }

    /**
     * 删除联系人。
     *
     * @param contactId 联系人 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/contact/{contactId}")
    @RequirePermission(CrmPermissions.CONTACT_MANAGE)
    @AuditLog(operation = "删除联系人", type = OperateType.DELETE,
            targetType = "crm_contact", targetId = "#contactId")
    public Result<Void> deleteContact(@PathVariable("contactId") Long contactId,
                                      HttpServletRequest request) {
        customerAppService.deleteContact(contactId, CrmActorContext.from(request));
        return Result.ok();
    }

    /**
     * 查询客户地址。
     *
     * @param customerId 客户 ID
     * @param request 当前请求
     * @return 地址列表
     */
    @GetMapping("/{customerId}/address")
    @RequirePermission(CrmPermissions.ADDRESS_LIST)
    public Result<List<CustomerAddressView>> listAddresses(@PathVariable("customerId") Long customerId,
                                                           HttpServletRequest request) {
        return Result.ok(customerAppService.listAddresses(customerId, CrmActorContext.from(request)));
    }

    /**
     * 新建客户地址。
     *
     * @param customerId 客户 ID
     * @param command 地址命令
     * @param request 当前请求
     * @return 地址视图
     */
    @PostMapping("/{customerId}/address")
    @RequirePermission(CrmPermissions.ADDRESS_MANAGE)
    @AuditLog(operation = "新建客户地址", type = OperateType.CREATE,
            targetType = "crm_customer_address", targetId = "#customerId")
    public Result<CustomerAddressView> createAddress(@PathVariable("customerId") Long customerId,
                                                     @Valid @RequestBody AddressSaveCommand command,
                                                     HttpServletRequest request) {
        return Result.ok(customerAppService.createAddress(
                customerId, command, CrmActorContext.from(request)));
    }

    /**
     * 编辑客户地址。
     *
     * @param addressId 地址 ID
     * @param command 地址命令
     * @param request 当前请求
     * @return 地址视图
     */
    @PutMapping("/address/{addressId}")
    @RequirePermission(CrmPermissions.ADDRESS_MANAGE)
    @AuditLog(operation = "编辑客户地址", type = OperateType.UPDATE,
            targetType = "crm_customer_address", targetId = "#addressId")
    public Result<CustomerAddressView> updateAddress(@PathVariable("addressId") Long addressId,
                                                     @Valid @RequestBody AddressSaveCommand command,
                                                     HttpServletRequest request) {
        return Result.ok(customerAppService.updateAddress(
                addressId, command, CrmActorContext.from(request)));
    }

    /**
     * 删除客户地址。
     *
     * @param addressId 地址 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/address/{addressId}")
    @RequirePermission(CrmPermissions.ADDRESS_MANAGE)
    @AuditLog(operation = "删除客户地址", type = OperateType.DELETE,
            targetType = "crm_customer_address", targetId = "#addressId")
    public Result<Void> deleteAddress(@PathVariable("addressId") Long addressId,
                                      HttpServletRequest request) {
        customerAppService.deleteAddress(addressId, CrmActorContext.from(request));
        return Result.ok();
    }
}
