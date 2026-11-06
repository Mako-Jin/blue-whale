package com.bluewhale.map.service;

import com.bluewhale.map.dto.DistanceRequest;
import com.bluewhale.map.dto.GeocodeRequest;
import com.bluewhale.map.dto.RegeoRequest;
import com.bluewhale.map.dto.RouteRequest;
import com.bluewhale.map.vo.DistanceVO;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.PoiPageVO;
import com.bluewhale.map.vo.ProviderHealthVO;
import com.bluewhale.map.vo.RegeoVO;
import com.bluewhale.map.vo.RouteVO;

import java.util.List;

/**
 * 地图解析服务：能力路由、降级、缓存与坐标口径都在这一层收口。
 *
 * @author BlueWhale
 */
public interface MapGeoService {

    /**
     * 地址解析（带缓存）
     *
     * @param request 请求
     * @return 解析结果（展示坐标系）
     */
    GeocodeVO geocode(GeocodeRequest request);

    /**
     * 逆地理
     *
     * @param request 请求
     * @return 逆地理结果
     */
    RegeoVO regeo(RegeoRequest request);

    /**
     * 地点检索
     *
     * @param keyword 关键词
     * @param city    限定城市
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    PoiPageVO searchPoi(String keyword, String city, Integer page, Integer size);

    /**
     * 路径规划
     *
     * @param request 请求
     * @return 路径结果
     */
    RouteVO route(RouteRequest request);

    /**
     * 批量直线距离（本地计算，不消耗配额）
     *
     * @param request 请求
     * @return 按下标对齐的距离列表
     */
    List<DistanceVO> distance(DistanceRequest request);

    /**
     * 供应商健康与今日配额
     *
     * @return 供应商列表
     */
    List<ProviderHealthVO> health();
}
