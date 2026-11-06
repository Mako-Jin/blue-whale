package com.bluewhale.map.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.map.dto.DistanceRequest;
import com.bluewhale.map.dto.GeocodeRequest;
import com.bluewhale.map.dto.RegeoRequest;
import com.bluewhale.map.dto.RouteRequest;
import com.bluewhale.map.service.MapGeoService;
import com.bluewhale.map.vo.DistanceVO;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.PoiPageVO;
import com.bluewhale.map.vo.ProviderHealthVO;
import com.bluewhale.map.vo.RegeoVO;
import com.bluewhale.map.vo.RouteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地图解析接口。
 *
 * <p>所有坐标在出入口都是同一个坐标系（默认 GCJ-02），响应里显式带 {@code coordType}。
 * 前端不需要知道当前用的是哪家供应商，切换供应商对前端是一次无感变更。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
public class MapGeoController {

    /** 地图解析服务 */
    private final MapGeoService geoService;

    /**
     * 地址解析（带缓存）
     *
     * @param request 请求
     * @return 解析结果
     */
    @PostMapping("/geocode")
    @RequirePermission("sys:map:geo:query")
    public Result<GeocodeVO> geocode(@Valid @RequestBody GeocodeRequest request) {
        return Result.ok(geoService.geocode(request));
    }

    /**
     * 逆地理
     *
     * @param request 请求
     * @return 逆地理结果
     */
    @PostMapping("/regeo")
    @RequirePermission("sys:map:geo:query")
    public Result<RegeoVO> regeo(@Valid @RequestBody RegeoRequest request) {
        return Result.ok(geoService.regeo(request));
    }

    /**
     * 地点检索
     *
     * @param keyword 关键词
     * @param city    限定城市
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/poi/search")
    @RequirePermission("sys:map:geo:query")
    public Result<PoiPageVO> searchPoi(@RequestParam String keyword,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return Result.ok(geoService.searchPoi(keyword, city, page, size));
    }

    /**
     * 路径规划
     *
     * @param request 请求
     * @return 路径结果
     */
    @PostMapping("/route")
    @RequirePermission("sys:map:geo:query")
    public Result<RouteVO> route(@Valid @RequestBody RouteRequest request) {
        return Result.ok(geoService.route(request));
    }

    /**
     * 批量直线距离（本地计算，不消耗供应商配额）
     *
     * @param request 请求
     * @return 按下标对齐的距离列表
     */
    @PostMapping("/distance")
    @RequirePermission("sys:map:geo:query")
    public Result<List<DistanceVO>> distance(@Valid @RequestBody DistanceRequest request) {
        return Result.ok(geoService.distance(request));
    }

    /**
     * 供应商健康与今日配额
     *
     * @return 供应商列表
     */
    @GetMapping("/provider/health")
    @RequirePermission("sys:map:provider:view")
    public Result<List<ProviderHealthVO>> health() {
        return Result.ok(geoService.health());
    }
}
