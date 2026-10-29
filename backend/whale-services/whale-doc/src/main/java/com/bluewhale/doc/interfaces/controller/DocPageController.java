package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocApprovalService;
import com.bluewhale.doc.application.service.DocPageCopyService;
import com.bluewhale.doc.application.service.DocPageService;
import com.bluewhale.doc.interfaces.dto.DocPageCreateRequest;
import com.bluewhale.doc.interfaces.dto.DocPageDuplicateRequest;
import com.bluewhale.doc.interfaces.dto.DocPageMoveRequest;
import com.bluewhale.doc.interfaces.dto.DocPageSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocApprovalVO;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageTreeVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.support.DocOperator;
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
 * 文档页面与页面树接口。
 *
 * <p>服务内路径前缀 {@code /doc}；页面树挂载在 {@code /doc/space/{id}/tree}，
 * 其余页面接口位于 {@code /doc/page/**}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocPageController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_page";

    private final DocPageService pageService;

    private final DocPageCopyService copyService;

    private final DocApprovalService approvalService;

    /**
     * 查询空间页面树。
     *
     * @param spaceId     空间ID
     * @param httpRequest 当前请求
     * @return 页面树
     */
    @GetMapping("/space/{spaceId}/tree")
    @RequirePermission("sys:doc:page:list")
    public Result<List<DocPageTreeVO>> tree(@PathVariable Long spaceId, HttpServletRequest httpRequest) {
        return Result.ok(pageService.tree(spaceId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 页面详情。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 页面详情
     */
    @GetMapping("/page/{id}")
    @RequirePermission("sys:doc:page:read")
    public Result<DocPageVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(pageService.detail(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 按空间与路径定位页面。
     *
     * @param spaceId     空间ID
     * @param slug        页面路径
     * @param httpRequest 当前请求
     * @return 页面详情
     */
    @GetMapping("/page/by-slug")
    @RequirePermission("sys:doc:page:read")
    public Result<DocPageVO> bySlug(@RequestParam Long spaceId, @RequestParam String slug,
                                    HttpServletRequest httpRequest) {
        return Result.ok(pageService.bySlug(spaceId, slug, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 新建页面。
     *
     * @param request     新建请求
     * @param httpRequest 当前请求
     * @return 新建页面详情
     */
    @PostMapping("/page")
    @RequirePermission("sys:doc:page:add")
    @AuditLog(operation = "新建文档页面", type = OperateType.CREATE, targetType = TARGET_TYPE,
            recordParams = false)
    public Result<DocPageVO> create(@Valid @RequestBody DocPageCreateRequest request,
                                    HttpServletRequest httpRequest) {
        return Result.ok(pageService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 保存页面标题与正文。
     *
     * @param id          页面ID
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PutMapping("/page/{id}")
    @RequirePermission("sys:doc:page:edit")
    @AuditLog(operation = "保存文档页面", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id",
            recordParams = false)
    public Result<DocPageSaveVO> save(@PathVariable Long id,
                                      @Valid @RequestBody DocPageSaveRequest request,
                                      HttpServletRequest httpRequest) {
        return Result.ok(pageService.save(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 仅更新页面标题。
     *
     * @param id          页面ID
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PutMapping("/page/{id}/title")
    @RequirePermission("sys:doc:page:edit")
    @AuditLog(operation = "更新文档页面标题", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<DocPageSaveVO> updateTitle(@PathVariable Long id,
                                             @Valid @RequestBody DocPageSaveRequest request,
                                             HttpServletRequest httpRequest) {
        return Result.ok(pageService.updateTitle(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除页面。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/page/{id}")
    @RequirePermission("sys:doc:page:delete")
    @AuditLog(operation = "删除文档页面", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        pageService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 移动页面。
     *
     * @param id          页面ID
     * @param request     移动请求
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @PostMapping("/page/{id}/move")
    @RequirePermission("sys:doc:page:move")
    @AuditLog(operation = "移动文档页面", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> move(@PathVariable Long id,
                             @Valid @RequestBody DocPageMoveRequest request,
                             HttpServletRequest httpRequest) {
        pageService.move(id, request, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 发布页面。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PostMapping("/page/{id}/publish")
    @RequirePermission("sys:doc:page:publish")
    @AuditLog(operation = "发布文档页面", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocPageSaveVO> publish(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(pageService.publish(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 复制页面（可含子树）。
     *
     * @param id          源页面ID
     * @param request     复制请求
     * @param httpRequest 当前请求
     * @return 新页面详情
     */
    @PostMapping("/page/{id}/duplicate")
    @RequirePermission("sys:doc:page:add")
    @AuditLog(operation = "复制文档页面", type = OperateType.CREATE, targetType = TARGET_TYPE, targetId = "#id",
            recordParams = false)
    public Result<DocPageVO> duplicate(@PathVariable Long id,
                                       @Valid @RequestBody DocPageDuplicateRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(copyService.duplicate(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 提交发布审批；审批关闭时直接发布。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PostMapping("/page/{id}/submit-approval")
    @RequirePermission("sys:doc:page:publish")
    @AuditLog(operation = "提交文档发布审批", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocPageSaveVO> submitApproval(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.requireUserId(httpRequest);
        return Result.ok(approvalService.submit(id, DocOperator.of(httpRequest, userId)));
    }

    /**
     * 查询页面发布审批状态。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 审批状态
     */
    @GetMapping("/page/{id}/approval")
    @RequirePermission("sys:doc:page:read")
    public Result<DocApprovalVO> approval(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(approvalService.status(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 归档页面。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PostMapping("/page/{id}/archive")
    @RequirePermission("sys:doc:page:archive")
    @AuditLog(operation = "归档文档页面", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocPageSaveVO> archive(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(pageService.archive(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 取消归档。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 保存结果
     */
    @PostMapping("/page/{id}/restore")
    @RequirePermission("sys:doc:page:archive")
    @AuditLog(operation = "恢复文档页面", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocPageSaveVO> restore(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(pageService.restore(id, UserContext.requireUserId(httpRequest)));
    }
}
