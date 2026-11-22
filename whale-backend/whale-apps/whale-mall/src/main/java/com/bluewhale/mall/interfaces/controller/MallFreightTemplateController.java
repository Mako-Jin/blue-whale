package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
import com.bluewhale.mall.pricing.application.command.FreightTemplateSaveCommand;
import com.bluewhale.mall.pricing.application.service.FreightTemplateAppService;
import com.bluewhale.mall.pricing.application.view.FreightTemplateView;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateQuery;
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
 * 商城运费模板接口。
 *
 * <p>运费模板挂在渠道上（{@code /mall/channel} 保存渠道时传 {@code freightTemplateId}），
 * 本控制器只管模板自身的增删改查。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/freight-template", version = "1")
@RequiredArgsConstructor
public class MallFreightTemplateController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final FreightTemplateAppService freightTemplateAppService;

    /**
     * 运费模板分页。
     *
     * @param keyword    关键字
     * @param chargeType 计费方式
     * @param status     状态
     * @param current    后端风格页码
     * @param pageNum    前端风格页码
     * @param size       后端风格条数
     * @param pageSize   前端风格条数
     * @param request    当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.FREIGHT_LIST)
    public Result<PageResult<FreightTemplateView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "chargeType", required = false) String chargeType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        FreightTemplateQuery query = new FreightTemplateQuery();
        query.setKeyword(keyword);
        query.setChargeType(chargeType);
        query.setStatus(status);
        return Result.ok(freightTemplateAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 运费模板详情。
     *
     * @param id      模板 ID
     * @param request 当前请求
     * @return 模板视图
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.FREIGHT_LIST)
    public Result<FreightTemplateView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(freightTemplateAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 新建运费模板。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 模板视图
     */
    @PostMapping
    @RequirePermission(MallPermissions.FREIGHT_MANAGE)
    @AuditLog(operation = "新建运费模板", type = OperateType.CREATE, targetType = "mall_freight_template")
    public Result<FreightTemplateView> create(@Valid @RequestBody FreightTemplateSaveCommand command,
                                              HttpServletRequest request) {
        return Result.ok(freightTemplateAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 编辑运费模板。
     *
     * @param id      模板 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 模板视图
     */
    @PutMapping("/{id}")
    @RequirePermission(MallPermissions.FREIGHT_MANAGE)
    @AuditLog(operation = "编辑运费模板", type = OperateType.UPDATE, targetType = "mall_freight_template")
    public Result<FreightTemplateView> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody FreightTemplateSaveCommand command,
                                              HttpServletRequest request) {
        return Result.ok(freightTemplateAppService.update(id, command, MallActorContext.from(request)));
    }

    /**
     * 启停运费模板。
     *
     * @param id      模板 ID
     * @param status  目标状态：ACTIVE / DISABLED
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/status")
    @RequirePermission(MallPermissions.FREIGHT_MANAGE)
    @AuditLog(operation = "启停运费模板", type = OperateType.UPDATE, targetType = "mall_freight_template")
    public Result<Void> changeStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     HttpServletRequest request) {
        freightTemplateAppService.changeStatus(id, status, MallActorContext.from(request));
        return Result.ok();
    }
}
