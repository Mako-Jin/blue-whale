package com.bluewhale.pdm.document.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.document.dto.PdmDocAclSaveRequest;
import com.bluewhale.pdm.document.service.PdmDocAclService;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocAclVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PDM 图文档 ACL 接口
 *
 * <p>目录与文档共用同一组端点，通过 {@code resourceType} 区分资源。
 * 查询与维护均要求资源级 MANAGE 能力。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/acl", version = "1")
@RequiredArgsConstructor
public class PdmDocAclController {

    private final PdmDocAclService aclService;

    /**
     * 查询 ACL 规则
     *
     * @param resourceType   资源类型：FOLDER/DOCUMENT
     * @param resourceId     资源 ID
     * @param servletRequest 当前请求
     * @return 规则列表
     */
    @GetMapping
    @RequirePermission("sys:pdm:doc:acl:list")
    public Result<List<PdmDocAclVO>> list(@RequestParam String resourceType,
                                          @RequestParam Long resourceId,
                                          HttpServletRequest servletRequest) {
        return Result.ok(aclService.list(resourceType, resourceId, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 新增 ACL 规则
     *
     * @param request        保存请求
     * @param servletRequest 当前请求
     * @return 规则视图
     */
    @PutMapping
    @RequirePermission("sys:pdm:doc:acl:manage")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "维护图文档权限")
    public Result<PdmDocAclVO> save(@Valid @RequestBody PdmDocAclSaveRequest request,
                                    HttpServletRequest servletRequest) {
        return Result.ok(aclService.save(request, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 删除 ACL 规则
     *
     * @param id             规则 ID
     * @param servletRequest 当前请求
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:doc:acl:manage")
    @AuditLog(module = "PDM 图文档", type = OperateType.DELETE, operation = "删除图文档权限")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        aclService.delete(id, PdmDocViewer.of(servletRequest));
        return Result.ok();
    }
}
