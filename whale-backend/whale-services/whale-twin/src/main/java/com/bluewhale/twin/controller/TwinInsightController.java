package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.dto.InsightRequest;
import com.bluewhale.twin.dto.InsightVO;
import com.bluewhale.twin.service.TwinInsightService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 孪生 AI 解读接口（Wave 6.04 M1）：上下文组装 + assistant 开放问答。
 *
 * <p>内网令牌留在服务端，前端只带普通用户态调用本接口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes")
@RequiredArgsConstructor
public class TwinInsightController {

    private final TwinInsightService insightService;

    /**
     * 生成 AI 解读（场景概要或指定实体）。
     *
     * @param sceneId     场景ID
     * @param request     解读请求（entityId/question 均可空）
     * @param httpRequest 当前请求
     * @return 解读结果
     */
    @PostMapping("/{sceneId}/ai/insight")
    @RequirePermission("sys:twin:scene")
    public Result<InsightVO> insight(@PathVariable("sceneId") Long sceneId,
                                     @Valid @RequestBody InsightRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(insightService.insight(sceneId, request,
                UserContext.requireUserId(httpRequest), WebHeaders.tenantId(httpRequest)));
    }
}
