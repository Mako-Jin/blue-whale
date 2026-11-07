package com.bluewhale.crm.marketing.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.marketing.application.command.MarketingSendCreateCommand;
import com.bluewhale.crm.marketing.application.service.MarketingSendAppService;
import com.bluewhale.crm.marketing.application.view.MarketingSendView;
import com.bluewhale.crm.marketing.domain.model.MarketingSendQuery;
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
 * 批量触达任务接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/marketing-send")
@RequiredArgsConstructor
public class CrmMarketingSendController {

    private final MarketingSendAppService marketingSendAppService;

    /**
     * 分页查询触达任务。
     *
     * @param keyword 关键字
     * @param campaignId 活动 ID
     * @param status 状态
     * @param channel 渠道
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<PageResult<MarketingSendView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        MarketingSendQuery query = new MarketingSendQuery();
        query.setKeyword(keyword);
        query.setCampaignId(campaignId);
        query.setStatus(status);
        query.setChannel(channel);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(marketingSendAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看触达任务详情。
     *
     * @param id 任务 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.CAMPAIGN_LIST)
    public Result<MarketingSendView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(marketingSendAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 创建触达任务。
     *
     * @param command 创建命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.MARKETING_SEND)
    @AuditLog(operation = "创建批量触达任务", type = OperateType.CREATE, targetType = "crm_marketing_send")
    public Result<MarketingSendView> create(@Valid @RequestBody MarketingSendCreateCommand command,
                                            HttpServletRequest request) {
        return Result.ok(marketingSendAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 执行触达任务。
     *
     * @param id 任务 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/execute")
    @RequirePermission(CrmPermissions.MARKETING_SEND)
    @AuditLog(operation = "执行批量触达任务", type = OperateType.UPDATE, targetType = "crm_marketing_send", targetId = "#id")
    public Result<MarketingSendView> execute(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(marketingSendAppService.execute(id, CrmActorContext.from(request)));
    }
}
