package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.service.application.command.ReturnRequestCommand;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import com.bluewhale.crm.service.application.view.ReturnRequestView;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退换货申请 REST 控制器。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/return")
@RequiredArgsConstructor
public class CrmReturnController {

    private final ReturnRequestAppService returnRequestAppService;

    /**
     * 分页查询退换货申请。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param customerId 客户 ID
     * @param orderId 商务订单 ID
     * @param current 页码
     * @param pageNum 页码（前端风格）
     * @param size 每页条数
     * @param pageSize 每页条数（前端风格）
     * @param request HTTP 请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.RETURN_LIST)
    public Result<PageResult<ReturnRequestView>> page(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long customerId,
                                                      @RequestParam(required = false) Long orderId,
                                                      @RequestParam(required = false) Long current,
                                                      @RequestParam(required = false) Long pageNum,
                                                      @RequestParam(required = false) Long size,
                                                      @RequestParam(required = false) Long pageSize,
                                                      HttpServletRequest request) {
        ReturnRequestQuery query = new ReturnRequestQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCustomerId(customerId);
        query.setOrderId(orderId);
        query.setCurrent((int) PageParams.current(current, pageNum));
        query.setSize((int) PageParams.size(size, pageSize));
        CrmActor actor = CrmActorContext.from(request);
        return Result.ok(returnRequestAppService.page(query, actor));
    }

    /**
     * 查看申请详情。
     *
     * @param id 申请 ID
     * @param request HTTP 请求
     * @return 申请视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.RETURN_LIST)
    public Result<ReturnRequestView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(returnRequestAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建退换货申请。
     *
     * @param command 创建命令
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "新建退换货申请", type = OperateType.CREATE, targetType = "crm_return_request")
    public Result<ReturnRequestView> create(@Valid @RequestBody ReturnRequestCommand command,
                                            HttpServletRequest request) {
        return Result.ok(returnRequestAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑退换货申请。
     *
     * @param id 申请 ID
     * @param command 编辑命令
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "编辑退换货申请", type = OperateType.UPDATE, targetType = "crm_return_request",
            targetId = "#id")
    public Result<ReturnRequestView> update(@PathVariable Long id,
                                            @Valid @RequestBody ReturnRequestCommand command,
                                            HttpServletRequest request) {
        return Result.ok(returnRequestAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 提交审批。
     *
     * @param id 申请 ID
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "提交退换货审批", type = OperateType.UPDATE, targetType = "crm_return_request",
            targetId = "#id")
    public Result<ReturnRequestView> submit(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(returnRequestAppService.submit(id, CrmActorContext.from(request)));
    }

    /**
     * 手动重试下发 ERP。
     *
     * @param id 申请 ID
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PostMapping("/{id}/retry")
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "重试下发退换货", type = OperateType.UPDATE, targetType = "crm_return_request",
            targetId = "#id")
    public Result<ReturnRequestView> retry(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(returnRequestAppService.retry(id, CrmActorContext.from(request)));
    }

    /**
     * 取消申请。
     *
     * @param id 申请 ID
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "取消退换货申请", type = OperateType.UPDATE, targetType = "crm_return_request",
            targetId = "#id")
    public Result<ReturnRequestView> cancel(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(returnRequestAppService.cancel(id, CrmActorContext.from(request)));
    }

    /**
     * 对账单张申请 ERP 退货状态。
     *
     * @param id 申请 ID
     * @param request HTTP 请求
     * @return 申请视图
     */
    @PostMapping("/{id}/reconcile")
    @RequirePermission(CrmPermissions.RETURN_MANAGE)
    @AuditLog(operation = "对账退换货 ERP 状态", type = OperateType.OTHER, targetType = "crm_return_request",
            targetId = "#id")
    public Result<ReturnRequestView> reconcile(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(returnRequestAppService.reconcile(id, CrmActorContext.from(request)));
    }
}
