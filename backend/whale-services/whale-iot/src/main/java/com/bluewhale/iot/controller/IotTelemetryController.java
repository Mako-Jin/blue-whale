package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.ShadowVO;
import com.bluewhale.iot.dto.TelemetryVO;
import com.bluewhale.iot.service.IotShadowService;
import com.bluewhale.iot.service.IotStreamService;
import com.bluewhale.iot.service.IotTelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 设备影子 / 遥测查询 / 实时事件推送接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/iot/devices")
@RequiredArgsConstructor
public class IotTelemetryController {

    private final IotTelemetryService telemetryService;

    private final IotShadowService shadowService;

    private final IotStreamService streamService;

    /**
     * 设备影子（最新上报快照 + 期望值 + 版本）。
     *
     * @param id 设备ID
     * @return 影子视图
     */
    @GetMapping("/{id}/shadow")
    @RequirePermission("sys:iot:device")
    public Result<ShadowVO> shadow(@PathVariable("id") Long id) {
        return Result.ok(shadowService.get(id));
    }

    /**
     * 设备遥测查询（按时间倒序）。
     *
     * @param id         设备ID
     * @param identifier 属性标识符过滤（可空）
     * @param from       起始时间（ISO-8601，可空）
     * @param to         结束时间（ISO-8601，可空）
     * @param limit      条数上限（可空）
     * @return 遥测列表
     */
    @GetMapping("/{id}/telemetry")
    @RequirePermission("sys:iot:device")
    public Result<List<TelemetryVO>> telemetry(
            @PathVariable("id") Long id,
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(telemetryService.query(id, identifier, from, to, limit));
    }

    /**
     * 订阅设备实时事件（SSE）。
     *
     * <p>事件名：{@code ready}（连接就绪）/ {@code telemetry}（遥测）/
     * {@code event}（设备事件）/ {@code command}（指令状态变化）。</p>
     *
     * @param id 设备ID
     * @return SSE 发射器（连接不设超时，服务端按心跳保活）
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("sys:iot:device")
    public SseEmitter stream(@PathVariable("id") Long id) {
        return streamService.subscribe(id);
    }
}
