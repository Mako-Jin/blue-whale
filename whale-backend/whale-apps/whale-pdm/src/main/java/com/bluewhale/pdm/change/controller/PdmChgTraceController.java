package com.bluewhale.pdm.change.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.change.dto.PdmChgTraceQueryRequest;
import com.bluewhale.pdm.change.service.PdmChgTraceService;
import com.bluewhale.pdm.change.vo.PdmChgTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PDM 变更追溯接口
 *
 * <p>只提供查询；追溯由业务动作写入，不提供编辑/删除。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/change/trace", version = "1")
@RequiredArgsConstructor
public class PdmChgTraceController {

    private final PdmChgTraceService traceService;

    /**
     * 分页查询追溯。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:change:trace:list")
    public Result<PageResult<PdmChgTraceVO>> page(PdmChgTraceQueryRequest request) {
        return Result.ok(traceService.page(request));
    }

    /**
     * 按对象查询时间线。
     *
     * @param objectType 对象类型
     * @param objectId   对象ID
     * @return 时间线
     */
    @GetMapping("/timeline")
    @RequirePermission("sys:pdm:change:trace:list")
    public Result<List<PdmChgTraceVO>> timeline(@RequestParam String objectType,
                                                @RequestParam Long objectId) {
        return Result.ok(traceService.timeline(objectType, objectId));
    }
}
