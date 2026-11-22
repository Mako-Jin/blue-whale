package com.bluewhale.pay.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayNotifyRetryResultVO;
import com.bluewhale.pay.vo.PayNotifyTaskVO;
import com.bluewhale.pay.vo.PayRefundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款单与业务通知任务管理接口。
 *
 * <p>路径 {@code /pay/v1/refund/**} 与 {@code /pay/v1/notify/**}，经网关按
 * {@code Path=/pay/**} 转发且不剥前缀，受 {@code sys:pay:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pay/v{version}", version = "1")
@RequiredArgsConstructor
public class PayRefundController {

    private final PayRefundService payRefundService;

    private final PayNotifyService payNotifyService;

    /**
     * 退款单分页。
     *
     * @param keyword 退款单号/支付单号模糊（可空）
     * @param current 页码
     * @param size    页大小
     * @return 分页结果
     */
    @GetMapping("/refund/page")
    @RequirePermission("sys:pay:list")
    public Result<PageResult<PayRefundVO>> page(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(payRefundService.page(keyword, current, size));
    }

    /**
     * 退款单详情。
     *
     * @param id 退款单 ID
     * @return 退款单
     */
    @GetMapping("/refund/{id}")
    @RequirePermission("sys:pay:list")
    public Result<PayRefundVO> detail(@PathVariable Long id) {
        return Result.ok(payRefundService.detail(id));
    }

    /**
     * 通知任务分页。
     *
     * @param status  状态过滤（可空）
     * @param current 页码
     * @param size    页大小
     * @return 分页结果
     */
    @GetMapping("/notify/page")
    @RequirePermission("sys:pay:list")
    public Result<PageResult<PayNotifyTaskVO>> notifyPage(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        PageResult<PayNotifyTaskPo> page =
                payNotifyService.pageTasks(current, size, status);
        return Result.ok(PageResult.of(
                page.getRecords().stream().map(PayOrderConverter.INSTANCE::toNotifyTaskVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 手工重试通知任务（DEAD 任务人工介入入口）。
     *
     * @param id 任务 ID
     * @return 三态结果：投递成功 / 正在投递 / 已安排下次重试
     */
    @PostMapping("/notify/{id}/retry")
    @RequirePermission("sys:pay:notify:retry")
    public Result<PayNotifyRetryResultVO> retry(@PathVariable Long id) {
        return Result.ok(payNotifyService.retryManually(id));
    }
}
