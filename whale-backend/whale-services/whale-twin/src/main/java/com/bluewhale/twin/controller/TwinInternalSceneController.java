package com.bluewhale.twin.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.BindingVO;
import com.bluewhale.twin.dto.EntityVO;
import com.bluewhale.twin.dto.MarkerVO;
import com.bluewhale.twin.dto.SceneVO;
import com.bluewhale.twin.service.TwinBindingService;
import com.bluewhale.twin.service.TwinEntityService;
import com.bluewhale.twin.service.TwinMarkerService;
import com.bluewhale.twin.service.TwinSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孪生场景内网只读概要接口（AI 助手工具 twin_scene_overview 用，Wave 6.04）。
 *
 * <p>内网令牌守卫（{@code /**\/internal/**}，网关侧 internal-deny 403）；
 * 返回场景元数据 + 实体树 + 绑定 + 标注的只读概要，不含敏感配置。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/internal/scene")
@RequiredArgsConstructor
public class TwinInternalSceneController {

    private final TwinSceneService sceneService;

    private final TwinEntityService entityService;

    private final TwinBindingService bindingService;

    private final TwinMarkerService markerService;

    /**
     * 场景概要（sceneId 或 sceneCode 二选一）。
     *
     * @param sceneId   场景ID
     * @param sceneCode 场景编码
     * @return 场景 + 实体树 + 绑定 + 标注概要
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(value = "sceneId", required = false) Long sceneId,
            @RequestParam(value = "sceneCode", required = false) String sceneCode) {
        Long id = sceneService.resolveSceneId(sceneId, sceneCode);
        SceneVO scene = sceneService.get(id);
        List<EntityVO> entities = entityService.tree(id);
        List<BindingVO> bindings = bindingService.list(id);
        List<MarkerVO> markers = markerService.list(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene);
        result.put("entities", entities);
        result.put("bindings", bindings);
        result.put("markers", markers);
        return Result.ok(result);
    }
}
