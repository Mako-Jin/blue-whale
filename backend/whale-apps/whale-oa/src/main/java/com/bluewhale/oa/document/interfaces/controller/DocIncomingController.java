package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocIncomingAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;
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
 * 收文接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/document/incoming")
@RequiredArgsConstructor
public class DocIncomingController {

    private static final String TARGET_TYPE = "oa_doc_incoming";

    private final DocIncomingAppService incomingAppService;

    /**
     * 分页查询收文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:document:list")
    public Result<PageResult<DocumentViews.Incoming>> page(DocQuery query) {
        return Result.ok(incomingAppService.page(query));
    }

    /**
     * 收文详情。
     *
     * @param id 收文ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:document:list")
    public Result<DocumentViews.Incoming> detail(@PathVariable Long id) {
        return Result.ok(incomingAppService.detail(id));
    }

    /**
     * 收文登记。
     *
     * @param command 收文命令
     * @return 收文
     */
    @PostMapping
    @RequirePermission("sys:oa:document:add")
    @AuditLog(operation = "收文登记", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> create(
            @Valid @RequestBody DocumentCommands.IncomingSave command) {
        return Result.ok(incomingAppService.create(command));
    }

    /**
     * 编辑登记。
     *
     * @param id      收文ID
     * @param command 收文命令
     * @return 收文
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:document:edit")
    @AuditLog(operation = "编辑收文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> update(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.IncomingSave command) {
        return Result.ok(incomingAppService.update(id, command));
    }

    /**
     * 删除登记。
     *
     * @param id 收文ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:document:delete")
    @AuditLog(operation = "删除收文", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        incomingAppService.delete(id);
        return Result.ok();
    }

    /**
     * 批阅。
     *
     * @param id      收文ID
     * @param command 批阅命令
     * @return 收文
     */
    @PostMapping("/{id}/review")
    @RequirePermission("sys:oa:document:incoming")
    @AuditLog(operation = "批阅收文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> review(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.IncomingReview command) {
        return Result.ok(incomingAppService.review(id, command));
    }

    /**
     * 转发。
     *
     * @param id      收文ID
     * @param command 转发命令
     * @return 收文
     */
    @PostMapping("/{id}/forward")
    @RequirePermission("sys:oa:document:incoming")
    @AuditLog(operation = "转发收文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> forward(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.IncomingForward command) {
        return Result.ok(incomingAppService.forward(id, command));
    }

    /**
     * 办结。
     *
     * @param id 收文ID
     * @return 收文
     */
    @PostMapping("/{id}/complete")
    @RequirePermission("sys:oa:document:incoming")
    @AuditLog(operation = "办结收文", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> complete(@PathVariable Long id) {
        return Result.ok(incomingAppService.complete(id));
    }

    /**
     * 归档。
     *
     * @param id 收文ID
     * @return 收文
     */
    @PostMapping("/{id}/archive")
    @RequirePermission("sys:oa:document:archive")
    @AuditLog(operation = "收文归档", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Incoming> archive(@PathVariable Long id) {
        return Result.ok(incomingAppService.archive(id));
    }
}
