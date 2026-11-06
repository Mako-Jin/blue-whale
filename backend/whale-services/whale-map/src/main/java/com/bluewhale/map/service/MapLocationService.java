package com.bluewhale.map.service;

import com.bluewhale.map.dto.LocationSaveRequest;
import com.bluewhale.map.vo.MapLocationVO;

import java.util.List;

/**
 * 业务打点服务。
 *
 * @author BlueWhale
 */
public interface MapLocationService {

    /**
     * 打点（幂等覆盖）
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @param request 打点请求
     * @return 打点结果（展示坐标系）
     */
    MapLocationVO save(String bizType, String bizId, LocationSaveRequest request);

    /**
     * 查询打点
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @return 打点结果（未打点返回 {@code null}）
     */
    MapLocationVO get(String bizType, String bizId);

    /**
     * 取消打点（物理删除）
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     */
    void remove(String bizType, String bizId);

    /**
     * 附近查询
     *
     * @param bizType      业务类型（可空，空表示不限类型）
     * @param lng          中心经度
     * @param lat          中心纬度
     * @param coordType    坐标系（可空）
     * @param radiusMeters 半径（米，可空）
     * @param limit        返回条数（可空）
     * @return 按距离升序的点位
     */
    List<MapLocationVO> nearby(String bizType, Double lng, Double lat, String coordType,
                               Integer radiusMeters, Integer limit);
}
