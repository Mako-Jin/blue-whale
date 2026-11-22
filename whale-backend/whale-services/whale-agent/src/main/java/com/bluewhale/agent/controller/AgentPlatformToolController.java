package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.IotDeviceToolRequest;
import com.bluewhale.agent.dto.TwinSceneToolRequest;
import com.bluewhale.agent.support.platform.PlatformToolClient;
import com.bluewhale.agent.support.platform.PlatformToolClient.PlatformToolException;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台只读数据工具接口（{@code /agent/tools}，Wave 6.04 三平台联动，L1）。
 *
 * <p>工具契约注册在 whale-ai（iot_device_state / iot_device_history /
 * twin_scene_overview），执行端在本服务：经内网令牌读 whale-iot /
 * whale-twin internal 只读接口。权限与 {@code data_query} 同族
 * （{@code sys:ai:data:read}），失败统一转业务异常由编排层降级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping(path = "/agent/v{version}/tools", version = "1")
@RequirePermission("sys:ai:data:read")
@RequiredArgsConstructor
public class AgentPlatformToolController {

    private final PlatformToolClient platformToolClient;

    /**
     * IoT 设备实时态工具。
     *
     * @param request 设备定位入参
     * @return 影子 JSON（online/reported/desired/...）
     */
    @PostMapping("/iot-device-state")
    @AuditLog(operation = "智能体设备实时态查询", type = OperateType.QUERY, targetType = "iot_shadow")
    public Result<JsonNode> iotDeviceState(@RequestBody IotDeviceToolRequest request) {
        assertDeviceLocated(request);
        try {
            return Result.ok(platformToolClient.iotDeviceState(
                    request.getDeviceId(), request.getProductCode(), request.getDeviceName()));
        } catch (PlatformToolException e) {
            log.warn("设备实时态工具失败：{}", e.getMessage());
            throw new BizException(e.getMessage());
        }
    }

    /**
     * IoT 设备遥测历史工具。
     *
     * @param request 设备定位入参（含 identifier/from/to/limit 可选过滤）
     * @return 遥测数组 JSON
     */
    @PostMapping("/iot-device-history")
    @AuditLog(operation = "智能体遥测历史查询", type = OperateType.QUERY, targetType = "iot_telemetry")
    public Result<JsonNode> iotDeviceHistory(@RequestBody IotDeviceToolRequest request) {
        assertDeviceLocated(request);
        try {
            return Result.ok(platformToolClient.iotDeviceHistory(
                    request.getDeviceId(), request.getProductCode(), request.getDeviceName(),
                    request.getIdentifier(), request.getFrom(), request.getTo(), request.getLimit()));
        } catch (PlatformToolException e) {
            log.warn("遥测历史工具失败：{}", e.getMessage());
            throw new BizException(e.getMessage());
        }
    }

    /**
     * 孪生场景概要工具。
     *
     * @param request 场景定位入参
     * @return 概要 JSON（scene/entities/bindings/markers）
     */
    @PostMapping("/twin-scene-overview")
    @AuditLog(operation = "智能体孪生场景概要查询", type = OperateType.QUERY, targetType = "twin_scene")
    public Result<JsonNode> twinSceneOverview(@RequestBody TwinSceneToolRequest request) {
        if (request.getSceneId() == null && !StringUtils.hasText(request.getSceneCode())) {
            throw new BizException("biz.agent.controller.s-c-e-n-e-i", "需提供 sceneId 或 sceneCode");
        }
        try {
            return Result.ok(platformToolClient.twinSceneOverview(
                    request.getSceneId(), request.getSceneCode()));
        } catch (PlatformToolException e) {
            log.warn("孪生概要工具失败：{}", e.getMessage());
            throw new BizException(e.getMessage());
        }
    }

    /**
     * 校验设备定位入参（deviceId 或 productCode+deviceName）。
     *
     * @param request 入参
     */
    private void assertDeviceLocated(IotDeviceToolRequest request) {
        if (request.getDeviceId() == null
                && !(StringUtils.hasText(request.getProductCode())
                && StringUtils.hasText(request.getDeviceName()))) {
            throw new BizException("biz.agent.controller.d-e-v-i-c-e", "需提供 deviceId 或 productCode+deviceName");
        }
    }
}
