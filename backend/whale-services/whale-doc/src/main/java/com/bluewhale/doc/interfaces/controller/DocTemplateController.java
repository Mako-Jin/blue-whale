package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocTemplateService;
import com.bluewhale.doc.interfaces.dto.DocTemplateApplyRequest;
import com.bluewhale.doc.interfaces.dto.DocTemplateSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.interfaces.vo.DocTemplateVO;
import com.bluewhale.doc.support.web.UserContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档模板接口（M3）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/template")
@RequiredArgsConstructor
public class DocTemplateController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_template";

    private final DocTemplateService templateService;

    /**
     * 查询可用模板。
     *
     * @param spaceId     空间ID，可为空
     * @param httpRequest 当前请求
     * @return 模板列表
     */
    @GetMapping
    @RequirePermission("sys:doc:page:list")
    public Result<List<DocTemplateVO>> list(@RequestParam(required = false) Long spaceId,
                                            HttpServletRequest httpRequest) {
        return Result.ok(templateService.list(spaceId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 查询模板详情。
     *
     * @param id          模板ID
     * @param httpRequest 当前请求
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:doc:page:read")
    public Result<DocTemplateVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(templateService.detail(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 新建模板。
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 模板详情
     */
    @PostMapping
    @RequirePermission("sys:doc:template:manage")
    @AuditLog(operation = "新建文档模板", type = OperateType.CREATE, targetType = TARGET_TYPE, recordParams = false)
    public Result<DocTemplateVO> create(@Valid @RequestBody DocTemplateSaveRequest request,
                                        HttpServletRequest httpRequest) {
        return Result.ok(templateService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 更新模板。
     *
     * @param id          模板ID
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 模板详情
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:doc:template:manage")
    @AuditLog(operation = "更新文档模板", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id",
            recordParams = false)
    public Result<DocTemplateVO> update(@PathVariable Long id,
                                        @Valid @RequestBody DocTemplateSaveRequest request,
                                        HttpServletRequest httpRequest) {
        return Result.ok(templateService.update(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除模板。
     *
     * @param id          模板ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:doc:template:manage")
    @AuditLog(operation = "删除文档模板", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        templateService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 从模板创建页面。
     *
     * @param id          模板ID
     * @param request     创建请求
     * @param httpRequest 当前请求
     * @return 新建页面详情
     */
    @PostMapping("/{id}/apply")
    @RequirePermission("sys:doc:page:add")
    @AuditLog(operation = "从模板创建文档页面", type = OperateType.CREATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<DocPageVO> apply(@PathVariable Long id,
                                   @Valid @RequestBody DocTemplateApplyRequest request,
                                   HttpServletRequest httpRequest) {
        return Result.ok(templateService.apply(id, request, UserContext.requireUserId(httpRequest)));
    }
}
