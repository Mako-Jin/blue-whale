package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocPageAclService;
import com.bluewhale.doc.interfaces.dto.DocPageAclUpdateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageAclVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面级权限接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocPageAclController {

    private final DocPageAclService aclService;

    /**
     * 查询页面授权。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 页面授权
     */
    @GetMapping("/page/{id}/acl")
    @RequirePermission("sys:doc:page:acl")
    public Result<DocPageAclVO> get(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(aclService.get(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 更新页面授权。
     *
     * @param id          页面ID
     * @param request     授权内容
     * @param httpRequest 当前请求
     * @return 更新后的授权
     */
    @PutMapping("/page/{id}/acl")
    @RequirePermission("sys:doc:page:acl")
    @AuditLog(operation = "更新页面级权限", type = OperateType.UPDATE, targetType = "doc_page", targetId = "#id")
    public Result<DocPageAclVO> update(@PathVariable Long id,
                                       @Valid @RequestBody DocPageAclUpdateRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(aclService.update(id, request, UserContext.requireUserId(httpRequest)));
    }
}
