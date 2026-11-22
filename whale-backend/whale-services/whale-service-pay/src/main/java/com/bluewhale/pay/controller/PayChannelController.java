package com.bluewhale.pay.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.enums.PayChannelFeature;
import com.bluewhale.pay.vo.PayChannelCapabilityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 支付渠道能力查询接口。
 *
 * <p>前端据此渲染「哪些渠道支持部分退款/主动关单」，避免把不支持的能力
 * 做成可按按钮；服务端在退款与关单时同样按能力校验，前端展示不作为唯一防线。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pay/v{version}/channel", version = "1")
@RequiredArgsConstructor
public class PayChannelController {

    private final PayChannelGateway payChannelGateway;

    /**
     * 渠道能力矩阵。
     *
     * @return 每个可用渠道及其能力
     */
    @GetMapping("/capabilities")
    @RequirePermission("sys:pay:list")
    public Result<List<PayChannelCapabilityVO>> capabilities() {
        List<PayChannelCapabilityVO> result = payChannelGateway.capabilities().entrySet().stream()
                .map(entry -> PayChannelCapabilityVO.of(entry.getKey(), codesOf(entry.getValue())))
                .toList();
        return Result.ok(result);
    }

    /**
     * 能力枚举转编码列表（按编码排序，保证前端渲染稳定）。
     *
     * @param features 能力集合
     * @return 能力编码
     */
    private static List<String> codesOf(Set<PayChannelFeature> features) {
        return features == null ? List.of()
                : features.stream().map(PayChannelFeature::code).sorted().toList();
    }
}
