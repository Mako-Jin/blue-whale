package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.application.service.WorkItemAppService;
import com.bluewhale.pm.application.service.WorkItemApprovalAppService;
import com.bluewhale.pm.application.service.WorkItemAttachmentAppService;
import com.bluewhale.pm.application.service.WorkItemCommentAppService;
import com.bluewhale.pm.application.service.WorkItemLinkAppService;
import com.bluewhale.pm.interfaces.dto.WorkItemAttachmentSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemCommentSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemLinkSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemQueryRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemRankRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemTransitionRequest;
import com.bluewhale.pm.interfaces.vo.ActivityVO;
import com.bluewhale.pm.interfaces.vo.WorkItemAttachmentVO;
import com.bluewhale.pm.interfaces.vo.WorkItemCommentVO;
import com.bluewhale.pm.interfaces.vo.WorkItemLinkVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作项接口：工作项主数据、状态流转、排序、动态以及评论 / 附件 / 关联协作能力。
 *
 * <p>用户身份取自网关注入的 {@code X-User-Id}，交由应用服务按项目角色收敛数据范围；
 * 全部接口受 {@code sys:pm:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pm/v{version}", version = "1")
@RequiredArgsConstructor
public class WorkItemController {

    /** 审计操作对象类型：工作项 */
    private static final String TARGET_ITEM = "pm_work_item";

    /** 审计操作对象类型：评论 */
    private static final String TARGET_COMMENT = "pm_comment";

    /** 审计操作对象类型：附件 */
    private static final String TARGET_ATTACHMENT = "pm_attachment";

    /** 审计操作对象类型：关联 */
    private static final String TARGET_LINK = "pm_work_item_link";

    private final WorkItemAppService workItemAppService;

    private final WorkItemCommentAppService commentAppService;

    private final WorkItemAttachmentAppService attachmentAppService;

    private final WorkItemLinkAppService linkAppService;

    private final WorkItemApprovalAppService approvalAppService;

    /**
     * 分页查询工作项
     *
     * @param request    查询条件
     * @param userHeader 用户ID请求头
     * @return 分页结果
     */
    @GetMapping("/work-item/page")
    @RequirePermission("sys:pm:item:list")
    public Result<PageResult<WorkItemVO>> page(@Valid WorkItemQueryRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.page(request, operatorId(userHeader)));
    }

    /**
     * 工作项详情
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 工作项详情
     */
    @GetMapping("/work-item/{id}")
    @RequirePermission("sys:pm:item:list")
    public Result<WorkItemVO> detail(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.getDetail(id, operatorId(userHeader)));
    }

    /**
     * 新增工作项
     *
     * @param request    新增请求
     * @param userHeader 用户ID请求头
     * @return 新增后的工作项
     */
    @PostMapping("/work-item")
    @RequirePermission("sys:pm:item:add")
    @AuditLog(operation = "新增工作项", type = OperateType.CREATE, targetType = TARGET_ITEM)
    public Result<WorkItemVO> create(@Valid @RequestBody WorkItemSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.create(request, operatorId(userHeader)));
    }

    /**
     * 更新工作项
     *
     * @param id         工作项ID
     * @param request    更新请求
     * @param userHeader 用户ID请求头
     * @return 更新后的工作项
     */
    @PutMapping("/work-item/{id}")
    @RequirePermission("sys:pm:item:edit")
    @AuditLog(operation = "更新工作项", type = OperateType.UPDATE, targetType = TARGET_ITEM)
    public Result<WorkItemVO> update(@PathVariable Long id, @Valid @RequestBody WorkItemSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.update(id, request, operatorId(userHeader)));
    }

    /**
     * 删除工作项
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/work-item/{id}")
    @RequirePermission("sys:pm:item:delete")
    @AuditLog(operation = "删除工作项", type = OperateType.DELETE, targetType = TARGET_ITEM)
    public Result<Void> delete(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        workItemAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 工作项状态流转
     *
     * @param id         工作项ID
     * @param request    流转请求
     * @param userHeader 用户ID请求头
     * @return 流转后的工作项
     */
    @PutMapping("/work-item/{id}/transition")
    @RequirePermission("sys:pm:item:transition")
    @AuditLog(operation = "工作项状态流转", type = OperateType.UPDATE, targetType = TARGET_ITEM)
    public Result<WorkItemVO> transition(@PathVariable Long id,
            @Valid @RequestBody WorkItemTransitionRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.transition(id, request, operatorId(userHeader)));
    }

    /**
     * 调整工作项排序
     *
     * @param id         工作项ID
     * @param request    排序请求
     * @param userHeader 用户ID请求头
     * @return 排序后的工作项
     */
    @PutMapping("/work-item/{id}/rank")
    @RequirePermission("sys:pm:item:rank")
    @AuditLog(operation = "调整工作项排序", type = OperateType.UPDATE, targetType = TARGET_ITEM)
    public Result<WorkItemVO> rank(@PathVariable Long id, @Valid @RequestBody WorkItemRankRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.rank(id, request, operatorId(userHeader)));
    }

    /**
     * 查询工作项动态
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 动态列表
     */
    @GetMapping("/work-item/{id}/activity")
    @RequirePermission("sys:pm:item:list")
    public Result<List<ActivityVO>> listActivities(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.listActivities(id, operatorId(userHeader)));
    }

    /**
     * 查询项目级动态
     *
     * @param projectId  项目ID
     * @param userHeader 用户ID请求头
     * @return 动态列表
     */
    @GetMapping("/project/{projectId}/activity")
    @RequirePermission("sys:pm:item:list")
    public Result<List<ActivityVO>> listProjectActivities(@PathVariable Long projectId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(workItemAppService.listProjectActivities(projectId, operatorId(userHeader)));
    }

    /**
     * 查询工作项评论
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 评论列表
     */
    @GetMapping("/work-item/{id}/comment")
    @RequirePermission("sys:pm:item:list")
    public Result<List<WorkItemCommentVO>> listComments(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(commentAppService.list(id, operatorId(userHeader)));
    }

    /**
     * 新增工作项评论
     *
     * @param id         工作项ID
     * @param request    新增请求
     * @param userHeader 用户ID请求头
     * @return 新增后的评论
     */
    @PostMapping("/work-item/{id}/comment")
    @RequirePermission("sys:pm:comment:add")
    @AuditLog(operation = "新增工作项评论", type = OperateType.CREATE, targetType = TARGET_COMMENT)
    public Result<WorkItemCommentVO> addComment(@PathVariable Long id,
            @Valid @RequestBody WorkItemCommentSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(commentAppService.add(id, request, operatorId(userHeader)));
    }

    /**
     * 删除工作项评论
     *
     * @param id         评论ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/comment/{id}")
    @RequirePermission("sys:pm:comment:delete")
    @AuditLog(operation = "删除工作项评论", type = OperateType.DELETE, targetType = TARGET_COMMENT)
    public Result<Void> deleteComment(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        commentAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 查询工作项附件
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 附件列表
     */
    @GetMapping("/work-item/{id}/attachment")
    @RequirePermission("sys:pm:item:list")
    public Result<List<WorkItemAttachmentVO>> listAttachments(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(attachmentAppService.list(id, operatorId(userHeader)));
    }

    /**
     * 登记工作项附件引用
     *
     * @param id         工作项ID
     * @param request    登记请求
     * @param userHeader 用户ID请求头
     * @return 登记后的附件
     */
    @PostMapping("/work-item/{id}/attachment")
    @RequirePermission("sys:pm:attachment:edit")
    @AuditLog(operation = "登记工作项附件", type = OperateType.CREATE, targetType = TARGET_ATTACHMENT)
    public Result<WorkItemAttachmentVO> addAttachment(@PathVariable Long id,
            @Valid @RequestBody WorkItemAttachmentSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(attachmentAppService.add(id, request, operatorId(userHeader)));
    }

    /**
     * 解除工作项附件引用
     *
     * @param id         附件记录ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/attachment/{id}")
    @RequirePermission("sys:pm:attachment:edit")
    @AuditLog(operation = "解除工作项附件", type = OperateType.DELETE, targetType = TARGET_ATTACHMENT)
    public Result<Void> deleteAttachment(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        attachmentAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 查询工作项关联
     *
     * @param id         工作项ID
     * @param userHeader 用户ID请求头
     * @return 关联列表
     */
    @GetMapping("/work-item/{id}/link")
    @RequirePermission("sys:pm:item:list")
    public Result<List<WorkItemLinkVO>> listLinks(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(linkAppService.list(id, operatorId(userHeader)));
    }

    /**
     * 新增工作项关联
     *
     * @param id         源工作项ID
     * @param request    新增请求
     * @param userHeader 用户ID请求头
     * @return 新增后的关联
     */
    @PostMapping("/work-item/{id}/link")
    @RequirePermission("sys:pm:link:edit")
    @AuditLog(operation = "新增工作项关联", type = OperateType.CREATE, targetType = TARGET_LINK)
    public Result<WorkItemLinkVO> addLink(@PathVariable Long id,
            @Valid @RequestBody WorkItemLinkSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(linkAppService.add(id, request, operatorId(userHeader)));
    }

    /**
     * 删除工作项关联
     *
     * @param id         关联记录ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/work-item/link/{id}")
    @RequirePermission("sys:pm:link:edit")
    @AuditLog(operation = "删除工作项关联", type = OperateType.DELETE, targetType = TARGET_LINK)
    public Result<Void> deleteLink(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        linkAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 发起工作项审批（可选能力，{@code whale.pm.approval.enabled=false} 时返回业务提示）
     *
     * @param id               工作项ID
     * @param userHeader       用户ID请求头
     * @param usernameHeader   用户账号请求头
     * @param permissionHeader 权限码请求头（透传给 workflow 判定审批权限）
     * @return 流程实例ID
     */
    @PostMapping("/work-item/{id}/approval")
    @RequirePermission("sys:pm:item:approval")
    @AuditLog(operation = "发起工作项审批", type = OperateType.UPDATE, targetType = TARGET_ITEM)
    public Result<String> submitApproval(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader,
            @RequestHeader(value = WebHeaders.USERNAME, required = false) String usernameHeader,
            @RequestHeader(value = WebHeaders.PERMISSION_CODES, required = false) String permissionHeader) {
        return Result.ok(approvalAppService.submit(id, operatorId(userHeader),
                usernameHeader, permissionHeader));
    }

    /**
     * 解析网关注入的用户ID请求头（不可解析时返回 {@code null}，由应用服务按未认证处理）
     *
     * @param userHeader 用户ID请求头
     * @return 用户ID；缺失或非法时返回 {@code null}
     */
    private static Long operatorId(String userHeader) {
        if (userHeader == null || userHeader.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userHeader.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
