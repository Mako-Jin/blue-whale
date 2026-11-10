package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocVersionService;
import com.bluewhale.doc.interfaces.dto.DocVersionManualRequest;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageVersionVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面版本历史接口。
 *
 * <p>服务内路径 {@code /doc/page/{pageId}/version/**}；版本列表与详情仅空间成员可读，
 * 手动快照需编辑权限，恢复历史版本需 {@code sys:doc:version:restore}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/page/{pageId}/version")
@RequiredArgsConstructor
public class DocVersionController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_page";

    private final DocVersionService versionService;

    /**
     * 分页查询版本历史。
     *
     * @param pageId      页面ID
     * @param current     当前页码
     * @param size        每页条数
     * @param httpRequest 当前请求
     * @return 版本分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:doc:version:list")
    public Result<PageResult<DocPageVersionVO>> page(@PathVariable Long pageId,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     HttpServletRequest httpRequest) {
        return Result.ok(versionService.page(pageId, current, size,
                UserContext.requireUserId(httpRequest)));
    }

    /**
     * 版本详情。
     *
     * @param pageId      页面ID
     * @param revision    快照 revision
     * @param httpRequest 当前请求
     * @return 版本详情
     */
    @GetMapping("/{revision}")
    @RequirePermission("sys:doc:version:list")
    public Result<DocPageVersionVO> detail(@PathVariable Long pageId, @PathVariable Long revision,
                                           HttpServletRequest httpRequest) {
        return Result.ok(versionService.detail(pageId, revision, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 手动保存当前版本快照。
     *
     * @param pageId      页面ID
     * @param request     变更说明
     * @param httpRequest 当前请求
     * @return 版本详情
     */
    @PostMapping
    @RequirePermission("sys:doc:page:edit")
    @AuditLog(operation = "手动保存页面版本", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocPageVersionVO> manual(@PathVariable Long pageId,
                                           @Valid @RequestBody(required = false) DocVersionManualRequest request,
                                           HttpServletRequest httpRequest) {
        return Result.ok(versionService.manual(pageId, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 恢复历史版本。
     *
     * @param pageId      页面ID
     * @param revision    历史 revision
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PostMapping("/{revision}/restore")
    @RequirePermission("sys:doc:version:restore")
    @AuditLog(operation = "恢复文档历史版本", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#pageId")
    public Result<DocPageSaveVO> restore(@PathVariable Long pageId, @PathVariable Long revision,
                                         HttpServletRequest httpRequest) {
        return Result.ok(versionService.restore(pageId, revision, UserContext.requireUserId(httpRequest)));
    }
}
