package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocOutgoingAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;
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

/**
 * 发文接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/document/outgoing", version = "1")
@RequiredArgsConstructor
public class DocOutgoingController {

    private static final String TARGET_TYPE = "oa_doc_outgoing";

    private final DocOutgoingAppService outgoingAppService;

    /**
     * 分页查询发文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:document:list")
    public Result<PageResult<DocumentViews.Outgoing>> page(DocQuery query) {
        return Result.ok(outgoingAppService.page(query));
    }

    /**
     * 发文详情。
     *
     * @param id 发文ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:document:list")
    public Result<DocumentViews.Outgoing> detail(@PathVariable Long id) {
        return Result.ok(outgoingAppService.detail(id));
    }

    /**
     * 拟稿。
     *
     * @param command 发文命令
     * @param request 请求上下文
     * @return 发文
     */
    @PostMapping
    @RequirePermission("sys:oa:document:add")
    @AuditLog(operation = "公文拟稿", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> create(
            @Valid @RequestBody DocumentCommands.OutgoingSave command, HttpServletRequest request) {
        return Result.ok(outgoingAppService.create(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 编辑草稿/驳回单据。
     *
     * @param id      发文ID
     * @param command 发文命令
     * @return 发文
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:document:edit")
    @AuditLog(operation = "编辑公文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> update(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.OutgoingSave command) {
        return Result.ok(outgoingAppService.update(id, command));
    }

    /**
     * 删除草稿/驳回单据。
     *
     * @param id 发文ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:document:delete")
    @AuditLog(operation = "删除公文", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        outgoingAppService.delete(id);
        return Result.ok();
    }

    /**
     * 提交审核。
     *
     * @param id 发文ID
     * @return 发文
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:oa:document:submit")
    @AuditLog(operation = "提交公文审核", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> submit(@PathVariable Long id) {
        return Result.ok(outgoingAppService.submit(id));
    }

    /**
     * 审核。
     *
     * @param id      发文ID
     * @param command 审核命令
     * @return 发文
     */
    @PostMapping("/{id}/audit")
    @RequirePermission("sys:oa:document:audit")
    @AuditLog(operation = "审核公文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> audit(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.OutgoingAudit command) {
        return Result.ok(outgoingAppService.audit(id, command));
    }

    /**
     * 签发。
     *
     * @param id      发文ID
     * @param command 签发命令
     * @param request 请求上下文
     * @return 发文
     */
    @PostMapping("/{id}/sign")
    @RequirePermission("sys:oa:document:sign")
    @AuditLog(operation = "签发公文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> sign(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.OutgoingSign command,
            HttpServletRequest request) {
        if (command.getSigner() == null) {
            command.setSigner(WebHeaders.username(request));
        }
        return Result.ok(outgoingAppService.sign(id, command));
    }

    /**
     * 下发。
     *
     * @param id 发文ID
     * @return 发文
     */
    @PostMapping("/{id}/issue")
    @RequirePermission("sys:oa:document:sign")
    @AuditLog(operation = "下发公文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> issue(@PathVariable Long id) {
        return Result.ok(outgoingAppService.issue(id));
    }

    /**
     * 归档。
     *
     * @param id 发文ID
     * @return 发文
     */
    @PostMapping("/{id}/archive")
    @RequirePermission("sys:oa:document:archive")
    @AuditLog(operation = "公文归档", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Outgoing> archive(@PathVariable Long id) {
        return Result.ok(outgoingAppService.archive(id));
    }
}
