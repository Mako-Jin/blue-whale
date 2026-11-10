package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocTemplateAppService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公文模板接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/document/templates")
@RequiredArgsConstructor
public class DocTemplateController {

    private static final String TARGET_TYPE = "oa_doc_template";

    private final DocTemplateAppService templateAppService;

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:document:template")
    public Result<PageResult<DocumentViews.Template>> page(DocQuery query) {
        return Result.ok(templateAppService.page(query));
    }

    /**
     * 查询全部模板。
     *
     * @param docType 适用范围，可为空
     * @return 模板列表
     */
    @GetMapping("/all")
    @RequirePermission("sys:oa:document:list")
    public Result<List<DocumentViews.Template>> all(
            @RequestParam(required = false) String docType) {
        return Result.ok(templateAppService.all(docType));
    }

    /**
     * 新增模板。
     *
     * @param command 模板命令
     * @return 模板
     */
    @PostMapping
    @RequirePermission("sys:oa:document:template")
    @AuditLog(operation = "新增公文模板", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Template> create(
            @Valid @RequestBody DocumentCommands.TemplateSave command) {
        return Result.ok(templateAppService.create(command));
    }

    /**
     * 更新模板。
     *
     * @param id      模板ID
     * @param command 模板命令
     * @return 模板
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:document:template")
    @AuditLog(operation = "编辑公文模板", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.Template> update(
            @PathVariable Long id, @Valid @RequestBody DocumentCommands.TemplateSave command) {
        return Result.ok(templateAppService.update(id, command));
    }

    /**
     * 删除模板。
     *
     * @param id 模板ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:document:template")
    @AuditLog(operation = "删除公文模板", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        templateAppService.delete(id);
        return Result.ok();
    }
}
