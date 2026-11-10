package com.bluewhale.scene.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.service.ScenePreferenceAppService;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.interfaces.support.SceneUserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户场景偏好接口（2D / 3D 视图模式与默认场景）。
 *
 * <p>登录即可访问，偏好按当前用户维度保存；用户身份只信任网关注入的
 * {@code X-User-Id}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/scene")
@RequiredArgsConstructor
public class ScenePreferenceController {

    private final ScenePreferenceAppService scenePreferenceAppService;

    /**
     * 查询当前用户的场景偏好（无记录时返回默认 2D）。
     *
     * @param request 当前请求（携带网关注入的身份头）
     * @return 偏好视图
     */
    @GetMapping("/preference")
    public Result<ScenePreferenceView> preference(HttpServletRequest request) {
        return Result.ok(scenePreferenceAppService.get(SceneUserContext.requireUserId(request)));
    }

    /**
     * 保存当前用户的场景偏好。
     *
     * @param command 保存命令
     * @param request 当前请求（携带网关注入的身份头）
     * @return 保存后的偏好视图
     */
    @PutMapping("/preference")
    public Result<ScenePreferenceView> save(@RequestBody ScenePreferenceSaveCommand command,
                                            HttpServletRequest request) {
        return Result.ok(scenePreferenceAppService.save(SceneUserContext.requireUserId(request), command));
    }
}
