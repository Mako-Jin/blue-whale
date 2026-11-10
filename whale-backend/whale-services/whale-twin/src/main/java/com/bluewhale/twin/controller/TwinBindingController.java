package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.BindingSaveRequest;
import com.bluewhale.twin.dto.BindingStateVO;
import com.bluewhale.twin.dto.BindingVO;
import com.bluewhale.twin.service.TwinBindingRuntimeService;
import com.bluewhale.twin.service.TwinBindingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 孪生数据源绑定接口：实体与数据源的绑定 CRUD + 实时态快照。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes/{sceneId}/bindings")
@RequiredArgsConstructor
public class TwinBindingController {

    private final TwinBindingService bindingService;

    private final TwinBindingRuntimeService runtimeService;

    /**
     * 场景绑定列表。
     *
     * @param sceneId 场景ID
     * @return 绑定视图列表
     */
    @GetMapping
    @RequirePermission("sys:twin:scene")
    public Result<List<BindingVO>> list(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(bindingService.list(sceneId));
    }

    /**
     * 创建绑定。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:twin:scene")
    public Result<BindingVO> create(@PathVariable("sceneId") Long sceneId,
                                    @Valid @RequestBody BindingSaveRequest request) {
        return Result.ok(bindingService.create(sceneId, request));
    }

    /**
     * 更新绑定。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<BindingVO> update(@PathVariable("sceneId") Long sceneId,
                                    @PathVariable("id") Long id,
                                    @Valid @RequestBody BindingSaveRequest request) {
        return Result.ok(bindingService.update(sceneId, id, request));
    }

    /**
     * 删除绑定。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<Void> delete(@PathVariable("sceneId") Long sceneId,
                               @PathVariable("id") Long id) {
        bindingService.delete(sceneId, id);
        return Result.ok();
    }

    /**
     * 绑定详情。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<BindingVO> get(@PathVariable("sceneId") Long sceneId,
                                 @PathVariable("id") Long id) {
        return Result.ok(bindingService.get(sceneId, id));
    }

    /**
     * 当前实时态快照（订阅 SSE 前先取一次，避免白屏等待首个 tick）。
     *
     * @param sceneId 场景ID
     * @return 实时态列表
     */
    @GetMapping("/state")
    @RequirePermission("sys:twin:scene")
    public Result<List<BindingStateVO>> state(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(runtimeService.currentStates(sceneId));
    }
}
