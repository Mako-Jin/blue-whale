package com.bluewhale.scene.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.scene.config.SceneProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景运行参数只读接口（前端判断 2D / 3D 能力与预算）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/scene")
@RequiredArgsConstructor
public class SceneMetaController {

    private final SceneProperties sceneProperties;

    /**
     * 查询场景服务能力与预算参数。
     *
     * @return 能力参数
     */
    @GetMapping("/meta")
    public Result<SceneMetaView> meta() {
        SceneMetaView view = new SceneMetaView();
        view.setThreeDEnabled(sceneProperties.isThreeDEnabled());
        view.setMaxNodesPerScene(sceneProperties.getMaxNodesPerScene());
        view.setMaxNodeDepth(sceneProperties.getMaxNodeDepth());
        view.setNavigationNodeLimit(sceneProperties.getNavigationNodeLimit());
        return Result.ok(view);
    }

    /**
     * 场景能力参数视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class SceneMetaView {

        /** 3D 增强是否启用（M1/M2 固定 false） */
        private boolean threeDEnabled;

        /** 场景节点数上限 */
        private int maxNodesPerScene;

        /** 节点树深度上限 */
        private int maxNodeDepth;

        /** 导航接口节点返回上限 */
        private int navigationNodeLimit;
    }
}
