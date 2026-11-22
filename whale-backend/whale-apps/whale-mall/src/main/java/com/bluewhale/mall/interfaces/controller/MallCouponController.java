package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.coupon.application.command.CouponSaveCommand;
import com.bluewhale.mall.coupon.application.service.CouponAppService;
import com.bluewhale.mall.coupon.application.view.CouponUserView;
import com.bluewhale.mall.coupon.application.view.CouponView;
import com.bluewhale.mall.coupon.domain.model.CouponQuery;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 商城优惠券接口。
 *
 * <p>运营侧（模板增改查、启停）与买家侧（领券、我的券、下单可用券）分权限：
 * 买家能领券不等于能改券面额。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/coupon", version = "1")
@RequiredArgsConstructor
public class MallCouponController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final CouponAppService couponAppService;

    /**
     * 券模板分页。
     *
     * @param keyword    关键字
     * @param couponType 券类型
     * @param status     状态
     * @param current    后端风格页码
     * @param pageNum    前端风格页码
     * @param size       后端风格条数
     * @param pageSize   前端风格条数
     * @param request    当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.COUPON_LIST)
    public Result<PageResult<CouponView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "couponType", required = false) String couponType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CouponQuery query = new CouponQuery();
        query.setKeyword(keyword);
        query.setCouponType(couponType);
        query.setStatus(status);
        return Result.ok(couponAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 我的券分页。
     *
     * @param status   状态过滤
     * @param current  后端风格页码
     * @param pageNum  前端风格页码
     * @param size     后端风格条数
     * @param pageSize 前端风格条数
     * @param request  当前请求
     * @return 分页结果
     */
    @GetMapping("/mine")
    @RequirePermission(MallPermissions.COUPON_CLAIM)
    public Result<PageResult<CouponUserView>> mine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(couponAppService.mine(status,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 当前订单可用的券（含不可用及原因）。
     *
     * <p>该端点没有订单类目上下文，品类券只会显示「无适用类目/未达门槛」，
     * 精确的判定在下单试算里进行。</p>
     *
     * @param channelId   渠道 ID
     * @param goodsAmount 商品总额
     * @param request     当前请求
     * @return 券列表
     */
    @GetMapping("/available")
    @RequirePermission(MallPermissions.COUPON_CLAIM)
    public Result<List<CouponUserView>> available(
            @RequestParam("channelId") Long channelId,
            @RequestParam(value = "amount", required = false) BigDecimal goodsAmount,
            HttpServletRequest request) {
        return Result.ok(couponAppService.available(channelId, goodsAmount, null,
                MallActorContext.from(request)));
    }

    /**
     * 券模板详情。
     *
     * @param id      模板 ID
     * @param request 当前请求
     * @return 模板视图
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.COUPON_LIST)
    public Result<CouponView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(couponAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 新建券模板。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 模板视图
     */
    @PostMapping
    @RequirePermission(MallPermissions.COUPON_MANAGE)
    @AuditLog(operation = "新建优惠券", type = OperateType.CREATE, targetType = "mall_coupon")
    public Result<CouponView> create(@Valid @RequestBody CouponSaveCommand command, HttpServletRequest request) {
        return Result.ok(couponAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 编辑券模板（不影响已领出的券）。
     *
     * @param id      模板 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 模板视图
     */
    @PutMapping("/{id}")
    @RequirePermission(MallPermissions.COUPON_MANAGE)
    @AuditLog(operation = "编辑优惠券", type = OperateType.UPDATE, targetType = "mall_coupon")
    public Result<CouponView> update(@PathVariable("id") Long id,
                                     @Valid @RequestBody CouponSaveCommand command,
                                     HttpServletRequest request) {
        return Result.ok(couponAppService.update(id, command, MallActorContext.from(request)));
    }

    /**
     * 启停券模板。
     *
     * @param id      模板 ID
     * @param status  目标状态：ACTIVE / DISABLED
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/status")
    @RequirePermission(MallPermissions.COUPON_MANAGE)
    @AuditLog(operation = "启停优惠券", type = OperateType.UPDATE, targetType = "mall_coupon")
    public Result<Void> changeStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     HttpServletRequest request) {
        couponAppService.changeStatus(id, status, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 买家领券。
     *
     * @param id      模板 ID
     * @param request 当前请求
     * @return 领到的券
     */
    @PostMapping("/{id}/claim")
    @RequirePermission(MallPermissions.COUPON_CLAIM)
    public Result<CouponUserView> claim(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(couponAppService.claim(id, MallActorContext.from(request)));
    }
}
