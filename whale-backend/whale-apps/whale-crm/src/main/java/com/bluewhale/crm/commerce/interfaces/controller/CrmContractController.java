package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.command.ContractRenewalCommand;
import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.commerce.application.view.ContractView;
import com.bluewhale.crm.commerce.domain.model.ContractQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 合同台账接口（M3 / 7f-1）。
 *
 * <p>合同正文与签署归 whale-contract，本接口只维护台账投影与续约标记。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/contract", version = "1")
@RequiredArgsConstructor
public class CrmContractController {

    private final ContractAppService contractAppService;

    /**
     * 合同台账分页。
     *
     * @param keyword 关键字（编号 / 标题）
     * @param customerId 客户 ID
     * @param status 合同状态
     * @param renewalStatus 续约状态
     * @param expireFrom 到期起始日
     * @param expireTo 到期截止日
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 合同台账分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.CONTRACT_LIST)
    public Result<PageResult<ContractView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "renewalStatus", required = false) String renewalStatus,
            @RequestParam(value = "expireFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expireFrom,
            @RequestParam(value = "expireTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expireTo,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ContractQuery query = new ContractQuery();
        query.setKeyword(keyword);
        query.setCustomerId(customerId);
        query.setStatus(status);
        query.setRenewalStatus(renewalStatus);
        query.setExpireFrom(expireFrom);
        query.setExpireTo(expireTo);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(contractAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 到期提醒。
     *
     * @param days 到期天数窗口
     * @param request 当前请求
     * @return 到期合同列表
     */
    @GetMapping("/expiring")
    @RequirePermission(CrmPermissions.CONTRACT_LIST)
    public Result<List<ContractView>> expiring(
            @RequestParam(value = "days", required = false) Integer days,
            HttpServletRequest request) {
        return Result.ok(contractAppService.expiring(days, CrmActorContext.from(request)));
    }

    /**
     * 合同台账详情。
     *
     * @param id 台账 ID
     * @param request 当前请求
     * @return 合同视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.CONTRACT_LIST)
    public Result<ContractView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(contractAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 按客户同步合同投影。
     *
     * @param customerId 客户 ID
     * @param request 当前请求
     * @return 同步数量
     */
    @PostMapping("/sync")
    @RequirePermission(CrmPermissions.CONTRACT_SYNC)
    @AuditLog(operation = "同步合同投影", type = OperateType.UPDATE, targetType = "crm_contract",
            targetId = "#customerId")
    public Result<Integer> sync(@RequestParam("customerId") Long customerId, HttpServletRequest request) {
        return Result.ok(contractAppService.syncByCustomer(customerId, CrmActorContext.from(request)));
    }

    /**
     * 标记合同续约。
     *
     * @param id 台账 ID
     * @param command 续约命令
     * @param request 当前请求
     * @return 合同视图
     */
    @PutMapping("/{id}/renewal")
    @RequirePermission(CrmPermissions.CONTRACT_RENEWAL)
    @AuditLog(operation = "标记合同续约", type = OperateType.UPDATE, targetType = "crm_contract", targetId = "#id")
    public Result<ContractView> renewal(@PathVariable("id") Long id,
                                        @Valid @RequestBody ContractRenewalCommand command,
                                        HttpServletRequest request) {
        return Result.ok(contractAppService.markRenewal(id, command, CrmActorContext.from(request)));
    }
}
