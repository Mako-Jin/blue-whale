package com.bluewhale.iot.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.ShadowVO;
import com.bluewhale.iot.service.IotShadowService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备实时态内网只读接口（数字孪生 / AI 助手工具化调用，M4 正式接入）。
 *
 * <p>入参二选一：{@code deviceId}，或 {@code productCode + deviceName} 三元组定位。
 * 返回影子快照，不含 secret 等凭据字段。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/iot/internal/device")
@RequiredArgsConstructor
public class IotInternalDeviceController {

    private final IotShadowService shadowService;

    /**
     * 查询设备实时态（影子 + 在线状态）。
     *
     * @param deviceId    设备ID（与 productCode/deviceName 二选一）
     * @param productCode 产品编码
     * @param deviceName  设备名
     * @return 影子视图
     */
    @GetMapping("/state")
    public Result<ShadowVO> state(
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "deviceName", required = false) String deviceName) {
        if (deviceId != null) {
            return Result.ok(shadowService.get(deviceId));
        }
        if (StringUtils.hasText(productCode) && StringUtils.hasText(deviceName)) {
            return Result.ok(shadowService.getByCode(productCode, deviceName));
        }
        throw new BizException("需提供 deviceId 或 productCode+deviceName");
    }
}
