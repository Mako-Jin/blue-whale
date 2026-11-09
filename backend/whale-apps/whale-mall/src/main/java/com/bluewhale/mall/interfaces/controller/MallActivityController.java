package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.mall.catalog.application.command.ActivitySaveCommand;
import com.bluewhale.mall.catalog.application.service.ActivityAppService;
import com.bluewhale.mall.catalog.application.view.ActivityView;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
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
 * 商城限时特价活动接口（运营侧）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/activity")
@RequiredArgsConstructor
public class MallActivityController {

    private final ActivityAppService activityAppService;

    /**
     * 活动分页。
     *
     * @param code      编码关键字
     * @param name      名称关键字
     * @param skuId     SKU ID
     * @param channelId 渠道 ID
     * @param status    状态
     * @param current   页码
     * @param size      每页条数
     * @param actor     操作人
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.ACTIVITY_LIST)
    public Result<PageResult<ActivityView>> page(@RequestParam(required = false) String code,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) Long skuId,
                                                 @RequestParam(required = false) Long channelId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") Long current,
                                                 @RequestParam(defaultValue = "20") Long size,
                                                 HttpServletRequest request) {
        return Result.ok(activityAppService.page(code, name, skuId, channelId, status, current, size,
                MallActorContext.from(request)));
    }

    /**
     * 活动详情。
     *
     * @param id    主键
     * @param actor 操作人
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.ACTIVITY_LIST)
    public Result<ActivityView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(activityAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 新建/更新活动。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 主键
     */
    @PostMapping
    @RequirePermission(MallPermissions.ACTIVITY_MANAGE)
    public Result<Long> save(@Valid @RequestBody ActivitySaveCommand command, HttpServletRequest request) {
        return Result.ok(activityAppService.save(command, MallActorContext.from(request)));
    }

    /**
     * 启停活动。
     *
     * @param id     主键
     * @param status 目标状态
     * @param actor  操作人
     * @return 空结果
     */
    @PostMapping("/{id}/status")
    @RequirePermission(MallPermissions.ACTIVITY_MANAGE)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status, HttpServletRequest request) {
        activityAppService.changeStatus(id, status, MallActorContext.from(request));
        return Result.ok();
    }
}
