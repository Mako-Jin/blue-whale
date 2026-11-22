package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.cert.application.command.CertTemplateSaveCommand;
import com.bluewhale.lms.cert.application.service.CertTemplateAppService;
import com.bluewhale.lms.cert.application.view.CertTemplateView;
import com.bluewhale.lms.cert.domain.model.CertTemplateQuery;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
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

/**
 * 证书模板接口（M4-1）。
 *
 * <p>模板定义发证口径（依据、有效期、编号前缀），因此维护权限与发证权限分开：
 * 能发证的人不一定要能改口径。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/cert/template", version = "1")
@RequiredArgsConstructor
public class CertTemplateController {

    private final CertTemplateAppService certTemplateAppService;

    /**
     * 模板分页。
     *
     * @param keyword 关键字
     * @param category 类别
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.CERT_LIST)
    public Result<PageResult<CertTemplateView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CertTemplateQuery query = new CertTemplateQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(certTemplateAppService.page(query, actor(request)));
    }

    /**
     * 模板详情。
     *
     * @param id 模板 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.CERT_LIST)
    public Result<CertTemplateView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(certTemplateAppService.detail(id, actor(request)));
    }

    /**
     * 新建模板。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 模板 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.CERT_TEMPLATE)
    @AuditLog(operation = "新建证书模板", type = OperateType.CREATE, targetType = "lr_cert_template")
    public Result<Long> create(@Valid @RequestBody CertTemplateSaveCommand command,
                               HttpServletRequest request) {
        return Result.ok(certTemplateAppService.create(command, actor(request)));
    }

    /**
     * 编辑模板。
     *
     * @param id 模板 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.CERT_TEMPLATE)
    @AuditLog(operation = "编辑证书模板", type = OperateType.UPDATE, targetType = "lr_cert_template")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CertTemplateSaveCommand command,
                               HttpServletRequest request) {
        certTemplateAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 删除模板（已被引用时拒绝）。
     *
     * @param id 模板 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.CERT_TEMPLATE)
    @AuditLog(operation = "删除证书模板", type = OperateType.DELETE, targetType = "lr_cert_template")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        certTemplateAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 启用 / 停用模板。
     *
     * @param id 模板 ID
     * @param enabled 是否启用
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/status")
    @RequirePermission(LmsPermissions.CERT_TEMPLATE)
    @AuditLog(operation = "变更证书模板状态", type = OperateType.UPDATE, targetType = "lr_cert_template")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestParam("enabled") boolean enabled,
                                     HttpServletRequest request) {
        certTemplateAppService.changeStatus(id, enabled, actor(request));
        return Result.ok();
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
