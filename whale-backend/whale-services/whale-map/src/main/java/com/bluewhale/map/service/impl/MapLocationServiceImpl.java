package com.bluewhale.map.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.common.geo.GeoDistance;
import com.bluewhale.map.common.geo.GeoHash;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.dto.GeocodeRequest;
import com.bluewhale.map.dto.LocationSaveRequest;
import com.bluewhale.map.entity.MapLocation;
import com.bluewhale.map.mapper.MapLocationMapper;
import com.bluewhale.map.service.MapGeoService;
import com.bluewhale.map.service.MapLocationService;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.MapLocationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 业务打点服务实现。
 *
 * <p><b>覆盖写而不是追加</b>：一个客户、一个供应商在业务上只有一个所在地，追加历史会让
 * 「现在在哪」变成一个需要排序才能回答的问题，而且每次搬家都多一条要维护的脏数据。</p>
 *
 * <p><b>附近查询先粗后精</b>：数据库只负责按 geohash 九宫格圈候选（走索引），
 * 球面距离与半径过滤在内存里做。geohash 只保证「在格子里」，直接拿它当结果会漏掉
 * 半径边缘的点，且漏得很稳定。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapLocationServiceImpl implements MapLocationService {

    /** geohash 存储精度（9 位约 4.8 米，足够表达门址且索引选择性合适） */
    private static final int GEOHASH_PRECISION = 9;

    /** 坐标小数位（与表定义一致） */
    private static final int COORD_SCALE = 7;

    /** 附近查询候选行上限：超过就先取最近的 1000 条，避免「市中心一个点」拉回半张表 */
    private static final int CANDIDATE_LIMIT = 1000;

    /** 默认附近半径（米） */
    private static final int DEFAULT_RADIUS = 1000;

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 50;

    /** 业务类型长度上限 */
    private static final int MAX_BIZ_TYPE_LENGTH = 32;

    /** 业务对象ID长度上限 */
    private static final int MAX_BIZ_ID_LENGTH = 64;

    /** 打点 Mapper */
    private final MapLocationMapper locationMapper;

    /** 地图解析服务（只给地址不給坐标时用） */
    private final MapGeoService geoService;

    /** 坐标系口径 */
    private final CoordContext coords;

    /** 服务配置 */
    private final MapProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public MapLocationVO save(String bizType, String bizId, LocationSaveRequest request) {
        String type = requireBizKey(bizType, "业务类型", MAX_BIZ_TYPE_LENGTH);
        String id = requireBizKey(bizId, "业务对象ID", MAX_BIZ_ID_LENGTH);
        LocationSource source = resolveSource(request);
        MapLocation row = findRow(type, id);
        if (row == null) {
            row = new MapLocation();
            row.setBizType(type);
            row.setBizId(id);
        }
        applySource(row, source, request);
        row.setGeohash(GeoHash.encode(source.point.lng(), source.point.lat(), GEOHASH_PRECISION));
        if (row.getId() == null) {
            locationMapper.insert(row);
        } else {
            locationMapper.updateById(row);
        }
        return toVo(row);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapLocationVO get(String bizType, String bizId) {
        String type = requireBizKey(bizType, "业务类型", MAX_BIZ_TYPE_LENGTH);
        String id = requireBizKey(bizId, "业务对象ID", MAX_BIZ_ID_LENGTH);
        MapLocation row = findRow(type, id);
        return row == null ? null : toVo(row);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(String bizType, String bizId) {
        String type = requireBizKey(bizType, "业务类型", MAX_BIZ_TYPE_LENGTH);
        String id = requireBizKey(bizId, "业务对象ID", MAX_BIZ_ID_LENGTH);
        MapLocation row = findRow(type, id);
        if (row == null) {
            return;
        }
        locationMapper.deletePhysical(row.getId(), TenantContext.requireId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MapLocationVO> nearby(String bizType, Double lng, Double lat, String coordType,
                                      Integer radiusMeters, Integer limit) {
        GeoPoint center = coords.toStorage(lng, lat, coordType);
        int radius = radiusMeters == null ? DEFAULT_RADIUS : radiusMeters;
        int maxRadius = properties.getLocation().getNearbyMaxRadiusMeters();
        if (radius <= 0 || radius > maxRadius) {
            throw new BizException(MapErrorCode.NEARBY_RADIUS_TOO_LARGE,
                    "半径需在 1–" + maxRadius + " 米之间");
        }
        List<MapLocation> rows = selectCandidates(bizType, center, radius);
        List<MapLocationVO> hits = new ArrayList<>();
        for (MapLocation row : rows) {
            GeoPoint point = new GeoPoint(row.getLng().doubleValue(), row.getLat().doubleValue(), coords.storage());
            double distance = GeoDistance.meters(center, point);
            if (distance <= radius) {
                MapLocationVO vo = toVo(row);
                vo.setDistanceMeters(coords.round(distance));
                hits.add(vo);
            }
        }
        hits.sort(Comparator.comparingDouble(MapLocationVO::getDistanceMeters));
        int maxRows = Math.min(limit == null ? DEFAULT_LIMIT : limit,
                properties.getLocation().getNearbyMaxRows());
        return hits.size() > maxRows ? new ArrayList<>(hits.subList(0, maxRows)) : hits;
    }

    /**
     * 按 geohash 九宫格取候选
     *
     * @param bizType 业务类型（可空）
     * @param center  中心点（存储坐标系）
     * @param radius  半径（米）
     * @return 候选行
     */
    private List<MapLocation> selectCandidates(String bizType, GeoPoint center, int radius) {
        Set<String> prefixes = GeoHash.coveringPrefixes(center.lng(), center.lat(), radius);
        boolean hasType = StringUtils.hasText(bizType);
        LambdaQueryWrapper<MapLocation> wrapper = new LambdaQueryWrapper<MapLocation>()
                .in(MapLocation::getGeohash, prefixes)
                .eq(hasType, MapLocation::getBizType, hasType ? bizType.trim() : null)
                .last("LIMIT " + CANDIDATE_LIMIT);
        return locationMapper.selectList(wrapper);
    }

    /**
     * 解析这次打点的坐标来源
     *
     * @param request 请求
     * @return 坐标来源（坐标为存储坐标系）
     */
    private LocationSource resolveSource(LocationSaveRequest request) {
        if (request.getLng() != null && request.getLat() != null) {
            GeoPoint point = coords.toStorage(request.getLng(), request.getLat(), request.getCoordType());
            return new LocationSource(point, "MANUAL", "MANUAL", null, null, null, null, null);
        }
        if (!StringUtils.hasText(request.getAddress())) {
            throw new BizException(MapErrorCode.LOCATION_SOURCE_MISSING, "请提供坐标或地址");
        }
        GeocodeRequest geoRequest = new GeocodeRequest();
        geoRequest.setAddress(request.getAddress());
        GeocodeVO geo = geoService.geocode(geoRequest);
        GeoPoint point = coords.toStorage(geo.getLng(), geo.getLat(), geo.getCoordType());
        String address = StringUtils.hasText(geo.getFormattedAddress())
                ? geo.getFormattedAddress() : request.getAddress();
        return new LocationSource(point, "GEOCODE", geo.getProvider(), address, geo.getProvince(),
                geo.getCity(), geo.getDistrict(), geo.getAdcode());
    }

    /**
     * 把坐标来源写入实体
     *
     * @param row     实体
     * @param source  坐标来源
     * @param request 请求（取备注）
     */
    private void applySource(MapLocation row, LocationSource source, LocationSaveRequest request) {
        row.setLng(BigDecimal.valueOf(source.point.lng()).setScale(COORD_SCALE, RoundingMode.HALF_UP));
        row.setLat(BigDecimal.valueOf(source.point.lat()).setScale(COORD_SCALE, RoundingMode.HALF_UP));
        row.setCoordType(coords.storage().name());
        row.setProvider(source.provider);
        row.setSource(source.source);
        row.setAddress(source.address);
        row.setProvince(source.province);
        row.setCity(source.city);
        row.setDistrict(source.district);
        row.setAdcode(source.adcode);
        row.setRemark(request.getRemark());
    }

    /**
     * 查询已存在的打点行
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     * @return 实体；不存在返回 {@code null}
     */
    private MapLocation findRow(String bizType, String bizId) {
        LambdaQueryWrapper<MapLocation> wrapper = new LambdaQueryWrapper<MapLocation>()
                .eq(MapLocation::getBizType, bizType)
                .eq(MapLocation::getBizId, bizId)
                .last("LIMIT 1");
        return locationMapper.selectOne(wrapper);
    }

    /**
     * 实体转视图（坐标转展示坐标系）
     *
     * @param row 实体
     * @return 视图
     */
    private MapLocationVO toVo(MapLocation row) {
        GeoPoint storage = new GeoPoint(row.getLng().doubleValue(), row.getLat().doubleValue(), coords.storage());
        GeoPoint display = coords.toDisplay(storage);
        MapLocationVO vo = new MapLocationVO();
        vo.setBizType(row.getBizType());
        vo.setBizId(row.getBizId());
        vo.setLng(coords.round(display.lng()));
        vo.setLat(coords.round(display.lat()));
        vo.setCoordType(coords.display().name());
        vo.setAddress(row.getAddress());
        vo.setProvince(row.getProvince());
        vo.setCity(row.getCity());
        vo.setDistrict(row.getDistrict());
        vo.setAdcode(row.getAdcode());
        vo.setProvider(row.getProvider());
        vo.setSource(row.getSource());
        vo.setRemark(row.getRemark());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /**
     * 校验业务标识
     *
     * @param value 原始值
     * @param label 字段名（用于报错）
     * @param max   长度上限
     * @return 规整后的值
     */
    private String requireBizKey(String value, String label, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(MapErrorCode.BIZ_KEY_INVALID, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new BizException(MapErrorCode.BIZ_KEY_INVALID, label + "不能超过 " + max + " 个字符");
        }
        return trimmed;
    }

    /**
     * 坐标来源的中间载体
     */
    private static final class LocationSource {

        /** 坐标（存储坐标系） */
        private final GeoPoint point;

        /** 来源类型 */
        private final String source;

        /** 坐标来源供应商（人工填写为 MANUAL） */
        private final String provider;

        /** 地址 */
        private final String address;

        /** 省 */
        private final String province;

        /** 市 */
        private final String city;

        /** 区县 */
        private final String district;

        /** 行政区划码 */
        private final String adcode;

        /**
         * 构造坐标来源
         *
         * @param point    坐标
         * @param source   来源类型
         * @param provider 坐标来源供应商
         * @param address  地址
         * @param province 省
         * @param city     市
         * @param district 区县
         * @param adcode   行政区划码
         */
        private LocationSource(GeoPoint point, String source, String provider, String address, String province,
                               String city, String district, String adcode) {
            this.point = point;
            this.source = source;
            this.provider = provider;
            this.address = address;
            this.province = province;
            this.city = city;
            this.district = district;
            this.adcode = adcode;
        }
    }
}
