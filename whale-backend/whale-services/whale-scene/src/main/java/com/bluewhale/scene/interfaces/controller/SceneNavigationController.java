package com.bluewhale.scene.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.scene.application.service.SceneNavigationAppService;
import com.bluewhale.scene.application.view.NavigationSceneView;
import com.bluewhale.scene.interfaces.support.SceneUserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景导航只读接口。
 *
 * <p>登录即可访问（网关 JWT 校验），返回当前访问者可见的场景与入口；
 * 无权限的节点与入口不出现，跨租户数据由租户行插件隔离。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/scene")
@RequiredArgsConstructor
public class SceneNavigationController {

    private final SceneNavigationAppService sceneNavigationAppService;

    /**
     * 当前用户可见的场景导航（2D 优先）。
     *
     * @param request 当前请求（携带网关注入的身份头）
     * @return 导航场景列表
     */
    @GetMapping("/navigation")
    public Result<List<NavigationSceneView>> navigation(HttpServletRequest request) {
        return Result.ok(sceneNavigationAppService.navigation(SceneUserContext.navigationUser(request)));
    }
}
