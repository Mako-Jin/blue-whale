package com.bluewhale.map.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.map.dto.LocationSaveRequest;
import com.bluewhale.map.service.MapLocationService;
import com.bluewhale.map.vo.MapLocationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务打点接口。
 *
 * <p>打点是「覆盖」语义：同一个 {@code bizType + bizId} 反复调用只会更新，不会累积。
 * 取消打点才需要显式调用 {@code /remove}，且删除后不留空壳记录。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/map/v{version}/location", version = "1")
@RequiredArgsConstructor
public class MapLocationController {

    /** 打点服务 */
    private final MapLocationService locationService;

    /**
     * 打点（幂等覆盖）
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @param request 打点请求
     * @return 打点结果
     */
    @PutMapping("/{bizType}/{bizId}")
    @RequirePermission("sys:map:location:manage")
    public Result<MapLocationVO> save(@PathVariable String bizType,
                                      @PathVariable String bizId,
                                      @Valid @RequestBody LocationSaveRequest request) {
        return Result.ok(locationService.save(bizType, bizId, request));
    }

    /**
     * 查询打点
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @return 打点结果（未打点返回 {@code null}）
     */
    @GetMapping("/{bizType}/{bizId}")
    @RequirePermission("sys:map:location:query")
    public Result<MapLocationVO> get(@PathVariable String bizType, @PathVariable String bizId) {
        return Result.ok(locationService.get(bizType, bizId));
    }

    /**
     * 取消打点
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @return 空结果
     */
    @PostMapping("/{bizType}/{bizId}/remove")
    @RequirePermission("sys:map:location:manage")
    public Result<Void> remove(@PathVariable String bizType, @PathVariable String bizId) {
        locationService.remove(bizType, bizId);
        return Result.ok();
    }

    /**
     * 附近查询
     *
     * @param bizType      业务类型（可空）
     * @param lng          中心经度
     * @param lat          中心纬度
     * @param coordType    坐标系（可空）
     * @param radiusMeters 半径（米，可空）
     * @param limit        返回条数（可空）
     * @return 按距离升序的点位
     */
    @GetMapping("/nearby")
    @RequirePermission("sys:map:location:query")
    public Result<List<MapLocationVO>> nearby(@RequestParam(required = false) String bizType,
                                              @RequestParam Double lng,
                                              @RequestParam Double lat,
                                              @RequestParam(required = false) String coordType,
                                              @RequestParam(required = false) Integer radiusMeters,
                                              @RequestParam(required = false) Integer limit) {
        return Result.ok(locationService.nearby(bizType, lng, lat, coordType, radiusMeters, limit));
    }
}
