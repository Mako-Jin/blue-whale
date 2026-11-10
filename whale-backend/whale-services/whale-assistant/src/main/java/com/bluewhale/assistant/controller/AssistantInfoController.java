package com.bluewhale.assistant.controller;

import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务信息接口（Wave 6.01 M0 骨架自检）。
 *
 * <p>{@code GET /assistant/info}：返回服务名、Wave 与能力开关快照，
 * 用于网关路由打通后的连通性冒烟；后续 M1 的助手 CRUD 会陆续挂到
 * {@code /assistant/definitions}、{@code /assistant/sessions} 等资源路径。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/assistant")
public class AssistantInfoController {

    /**
     * 服务信息快照（无需权限码，网关 JWT 鉴权已兜底）。
     *
     * @return 服务名 / Wave / 版本阶段 / 就绪时间
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("service", "whale-assistant");
        meta.put("wave", "6.01");
        meta.put("phase", "M0-skeleton");
        meta.put("readyAt", LocalDateTime.now().toString());
        return Result.ok(meta);
    }
}
