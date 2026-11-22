package com.bluewhale.pay.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.dto.PayNotifyRetryBatchRequest;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayNotifyRetryBatchItemVO;
import com.bluewhale.pay.vo.PayNotifyRetryResultVO;
import com.bluewhale.pay.vo.PayNotifyTaskVO;
import com.bluewhale.pay.vo.PayRefundVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * @param bizType 业务方类型过滤（可空）
     * @param current 页码
     * @param size    页大小
     * @return 分页结果
     */
    @GetMapping("/notify/page")
    @RequirePermission("sys:pay:list")
    public Result<PageResult<PayNotifyTaskVO>> notifyPage(@RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        PageResult<PayNotifyTaskPo> page =
                payNotifyService.pageTasks(current, size, status, bizType);
        return Result.ok(PageResult.of(
                page.getRecords().stream().map(PayOrderConverter.INSTANCE::toNotifyTaskVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 通知任务详情（含载荷，用于死信排查）。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/notify/{id}")
    @RequirePermission("sys:pay:list")
    public Result<PayNotifyTaskVO> notifyDetail(@PathVariable Long id) {
        return Result.ok(PayOrderConverter.INSTANCE.toNotifyTaskVo(payNotifyService.getTask(id)));
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

    /**
     * 批量手工重试通知任务（按入参顺序去重，逐项返回结果）。
     *
     * @param request 任务 ID 列表
     * @return 逐项结果
     */
    @PostMapping("/notify/retry-batch")
    @RequirePermission("sys:pay:notify:retry")
    public Result<List<PayNotifyRetryBatchItemVO>> retryBatch(
            @Valid @RequestBody PayNotifyRetryBatchRequest request) {
        return Result.ok(payNotifyService.retryBatch(request.getIds()));
    }
}
