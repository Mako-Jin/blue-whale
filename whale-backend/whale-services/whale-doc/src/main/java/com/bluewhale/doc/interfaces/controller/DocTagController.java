package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocTagService;
import com.bluewhale.doc.interfaces.dto.DocPageTagUpdateRequest;
import com.bluewhale.doc.interfaces.dto.DocTagRequest;
import com.bluewhale.doc.interfaces.vo.DocTagVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档标签接口。
 *
 * <p>空间标签挂在 {@code /doc/space/{id}/tag}，页面标签挂在 {@code /doc/page/{id}/tag}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocTagController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_tag";

    private final DocTagService tagService;

    /**
     * 查询空间标签及页面数量。
     *
     * @param spaceId     空间ID
     * @param httpRequest 当前请求
     * @return 标签列表
     */
    @GetMapping("/space/{spaceId}/tag")
    @RequirePermission("sys:doc:page:read")
    public Result<List<DocTagVO>> list(@PathVariable Long spaceId, HttpServletRequest httpRequest) {
        return Result.ok(tagService.list(spaceId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 创建空间标签。
     *
     * @param spaceId     空间ID
     * @param request     标签内容
     * @param httpRequest 当前请求
     * @return 新建标签
     */
    @PostMapping("/space/{spaceId}/tag")
    @RequirePermission("sys:doc:tag:manage")
    @AuditLog(operation = "新增文档标签", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocTagVO> create(@PathVariable Long spaceId,
                                   @Valid @RequestBody DocTagRequest request,
                                   HttpServletRequest httpRequest) {
        return Result.ok(tagService.create(spaceId, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 更新标签。
     *
     * @param id          标签ID
     * @param request     标签内容
     * @param httpRequest 当前请求
     * @return 更新后的标签
     */
    @PutMapping("/tag/{id}")
    @RequirePermission("sys:doc:tag:manage")
    @AuditLog(operation = "更新文档标签", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocTagVO> update(@PathVariable Long id,
                                   @Valid @RequestBody DocTagRequest request,
                                   HttpServletRequest httpRequest) {
        return Result.ok(tagService.update(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除标签。
     *
     * @param id          标签ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/tag/{id}")
    @RequirePermission("sys:doc:tag:manage")
    @AuditLog(operation = "删除文档标签", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        tagService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 查询页面标签。
     *
     * @param pageId      页面ID
     * @param httpRequest 当前请求
     * @return 标签列表
     */
    @GetMapping("/page/{pageId}/tag")
    @RequirePermission("sys:doc:page:read")
    public Result<List<DocTagVO>> pageTags(@PathVariable Long pageId, HttpServletRequest httpRequest) {
        return Result.ok(tagService.pageTags(pageId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 全量替换页面标签。
     *
     * @param pageId      页面ID
     * @param request     标签ID列表
     * @param httpRequest 当前请求
     * @return 更新后的页面标签
     */
    @PutMapping("/page/{pageId}/tag")
    @RequirePermission("sys:doc:tag:manage")
    @AuditLog(operation = "更新页面标签", type = OperateType.UPDATE, targetType = "doc_page", targetId = "#pageId")
    public Result<List<DocTagVO>> updatePageTags(@PathVariable Long pageId,
                                                 @Valid @RequestBody DocPageTagUpdateRequest request,
                                                 HttpServletRequest httpRequest) {
        return Result.ok(tagService.updatePageTags(pageId, request.getTagIds(),
                UserContext.requireUserId(httpRequest)));
    }
}
