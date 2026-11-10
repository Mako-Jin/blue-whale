package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocNoAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公文文号规则接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/document/no-rules")
@RequiredArgsConstructor
public class DocNoRuleController {

    private static final String TARGET_TYPE = "oa_doc_no_rule";

    private final DocNoAppService docNoAppService;

    /**
     * 查询全部文号规则。
     *
     * @return 规则列表
     */
    @GetMapping
    @RequirePermission("sys:oa:document:list")
    public Result<List<DocumentViews.NoRule>> all() {
        return Result.ok(docNoAppService.all());
    }

    /**
     * 新增文号规则。
     *
     * @param command 规则命令
     * @return 规则
     */
    @PostMapping
    @RequirePermission("sys:oa:document:no-rule")
    @AuditLog(operation = "新增公文文号规则", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.NoRule> create(
            @Valid @RequestBody DocumentCommands.NoRuleSave command) {
        return Result.ok(docNoAppService.create(command));
    }

    /**
     * 更新文号规则。
     *
     * @param id      规则ID
     * @param command 规则命令
     * @return 规则
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:document:no-rule")
    @AuditLog(operation = "编辑公文文号规则", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.NoRule> update(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.NoRuleSave command) {
        return Result.ok(docNoAppService.update(id, command));
    }
}
