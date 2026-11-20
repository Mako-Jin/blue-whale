package com.bluewhale.iot.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.ShadowVO;
import com.bluewhale.iot.dto.TelemetryVO;
import com.bluewhale.iot.service.IotShadowService;
import com.bluewhale.iot.service.IotTelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备实时态/遥测内网只读接口（数字孪生 / AI 助手工具化调用，Wave 6.04 接入）。
 *
 * <p>实时态入参二选一：{@code deviceId}，或 {@code productCode + deviceName} 三元组定位。
 * 返回影子快照，不含 secret 等凭据字段。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/iot/internal/device")
@RequiredArgsConstructor
public class IotInternalDeviceController {

    private final IotShadowService shadowService;

    private final IotTelemetryService telemetryService;

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
        return Result.ok(shadow(deviceId, productCode, deviceName));
    }

    /**
     * 查询设备遥测历史（时间倒序；AI 工具 iot_device_history 用）。
     *
     * @param deviceId    设备ID（与 productCode/deviceName 二选一）
     * @param productCode 产品编码
     * @param deviceName  设备名
     * @param identifier  属性标识符过滤（可空）
     * @param from        起始时间（ISO-8601，可空）
     * @param to          结束时间（ISO-8601，可空）
     * @param limit       条数上限（可空，钳制到配置上限）
     * @return 遥测列表
     */
    @GetMapping("/telemetry/history")
    public Result<List<TelemetryVO>> telemetryHistory(
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "deviceName", required = false) String deviceName,
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false) Integer limit) {
        ShadowVO shadow = shadow(deviceId, productCode, deviceName);
        Long resolved = Long.valueOf(shadow.getDeviceId());
        return Result.ok(telemetryService.query(resolved, identifier, from, to, limit));
    }

    /**
     * 定位设备并取影子（两种入参形态归一）。
     *
     * @param deviceId    设备ID
     * @param productCode 产品编码
     * @param deviceName  设备名
     * @return 影子视图
     */
    private ShadowVO shadow(Long deviceId, String productCode, String deviceName) {
        if (deviceId != null) {
            return shadowService.get(deviceId);
        }
        if (StringUtils.hasText(productCode) && StringUtils.hasText(deviceName)) {
            return shadowService.getByCode(productCode, deviceName);
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.internal-device.params-required",
                "需提供 deviceId 或 productCode+deviceName");
    }
}
