package com.bluewhale.crm.marketing.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.marketing.application.command.AudienceLockCommand;
import com.bluewhale.crm.marketing.application.command.AudiencePreviewCommand;
import com.bluewhale.crm.marketing.application.command.CampaignCreateCommand;
import com.bluewhale.crm.marketing.application.command.CampaignRoiCommand;
import com.bluewhale.crm.marketing.application.command.CampaignStatusCommand;
import com.bluewhale.crm.marketing.application.command.CampaignUpdateCommand;
import com.bluewhale.crm.marketing.application.service.CampaignAppService;
import com.bluewhale.crm.marketing.application.view.AudiencePreviewView;
import com.bluewhale.crm.marketing.application.view.CampaignAudienceView;
import com.bluewhale.crm.marketing.application.view.CampaignView;
import com.bluewhale.crm.marketing.domain.model.CampaignQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销活动接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/campaign")
@RequiredArgsConstructor
public class CrmCampaignController {

    private final CampaignAppService campaignAppService;

    /**
     * 分页查询营销活动。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param campaignType 活动类型
     * @param channel 渠道
     * @param ownerId 负责人
     * @param deptId 归属部门
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<PageResult<CampaignView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "campaignType", required = false) String campaignType,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CampaignQuery query = new CampaignQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCampaignType(campaignType);
        query.setChannel(channel);
        query.setOwnerId(ownerId);
        query.setDeptId(deptId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(campaignAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看营销活动详情。
     *
     * @param id 活动 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<CampaignView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(campaignAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建营销活动。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.CAMPAIGN_MANAGE)
    @AuditLog(operation = "新建营销活动", type = OperateType.CREATE, targetType = "crm_campaign")
    public Result<CampaignView> create(@Valid @RequestBody CampaignCreateCommand command,
                                       HttpServletRequest request) {
        return Result.ok(campaignAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑营销活动。
     *
     * @param id 活动 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.CAMPAIGN_MANAGE)
    @AuditLog(operation = "编辑营销活动", type = OperateType.UPDATE, targetType = "crm_campaign", targetId = "#id")
    public Result<CampaignView> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody CampaignUpdateCommand command,
                                       HttpServletRequest request) {
        return Result.ok(campaignAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 回填 ROI。
     *
     * @param id 活动 ID
     * @param command ROI 命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/roi")
    @RequirePermission(CrmPermissions.CAMPAIGN_MANAGE)
    @AuditLog(operation = "回填营销活动 ROI", type = OperateType.UPDATE, targetType = "crm_campaign", targetId = "#id")
    public Result<CampaignView> roi(@PathVariable("id") Long id,
                                    @Valid @RequestBody CampaignRoiCommand command,
                                    HttpServletRequest request) {
        return Result.ok(campaignAppService.updateRoi(id, command, CrmActorContext.from(request)));
    }

    /**
     * 预览活动人群。
     *
     * @param id 活动 ID
     * @param command 预览命令
     * @param request 当前请求
     * @return 预览视图
     */
    @PostMapping("/{id}/audience/preview")
    @RequirePermission(CrmPermissions.CAMPAIGN_AUDIENCE)
    public Result<AudiencePreviewView> previewAudience(@PathVariable("id") Long id,
                                                       @RequestBody(required = false) AudiencePreviewCommand command,
                                                       HttpServletRequest request) {
        return Result.ok(campaignAppService.previewAudience(id, command, CrmActorContext.from(request)));
    }

    /**
     * 锁定活动人群快照。
     *
     * @param id 活动 ID
     * @param command 锁定命令
     * @param request 当前请求
     * @return 人群快照视图
     */
    @PostMapping("/{id}/audience/lock")
    @RequirePermission(CrmPermissions.CAMPAIGN_AUDIENCE)
    @AuditLog(operation = "锁定活动人群", type = OperateType.UPDATE, targetType = "crm_campaign", targetId = "#id")
    public Result<CampaignAudienceView> lockAudience(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) AudienceLockCommand command,
                                                     HttpServletRequest request) {
        return Result.ok(campaignAppService.lockAudience(id, command, CrmActorContext.from(request)));
    }

    /**
     * 变更活动状态。
     *
     * @param id 活动 ID
     * @param command 状态命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/status")
    @RequirePermission(CrmPermissions.CAMPAIGN_STATUS)
    @AuditLog(operation = "变更营销活动状态", type = OperateType.UPDATE, targetType = "crm_campaign", targetId = "#id")
    public Result<CampaignView> changeStatus(@PathVariable("id") Long id,
                                             @Valid @RequestBody CampaignStatusCommand command,
                                             HttpServletRequest request) {
        return Result.ok(campaignAppService.changeStatus(id, command, CrmActorContext.from(request)));
    }
}
