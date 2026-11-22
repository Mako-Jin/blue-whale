package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.twin.service.TwinStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 场景实时流接口（SSE）：订阅后由绑定运行时推送实时态批次。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/twin/v{version}/scenes/{sceneId}", version = "1")
@RequiredArgsConstructor
public class TwinStreamController {

    private final TwinStreamService streamService;

    /**
     * 订阅场景实时事件（ready / binding；心跳为注释帧）。
     *
     * @param sceneId 场景ID
     * @return SSE 发射器
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("sys:twin:scene")
    public SseEmitter stream(@PathVariable("sceneId") Long sceneId) {
        return streamService.subscribe(sceneId);
    }
}
