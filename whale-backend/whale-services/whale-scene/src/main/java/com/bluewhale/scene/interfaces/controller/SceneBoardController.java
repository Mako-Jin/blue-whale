package com.bluewhale.scene.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.scene.application.service.SceneBoardAppService;
import com.bluewhale.scene.application.view.MfgBoardView;
import com.bluewhale.scene.interfaces.support.SceneUserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 3D 看板数据接口（登录即可访问）。
 *
 * <p>数据由 ERP 内网聚合，数量与 ERP 生产订单 / 库存余额同源；
 * 数据源不可用时返回 {@code degraded=true}，由前端显式降级。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/scene/board")
@RequiredArgsConstructor
public class SceneBoardController {

    private final SceneBoardAppService sceneBoardAppService;

    /**
     * 制造看板实时数量。
     *
     * @param limit   单类记录上限（默认 50，最大 200）
     * @param request 当前请求（携带网关注入的租户头）
     * @return 看板视图
     */
    @GetMapping("/manufacturing")
    public Result<MfgBoardView> manufacturing(
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        return Result.ok(sceneBoardAppService.manufacturingBoard(
                SceneUserContext.requireTenantId(request), limit));
    }
}
