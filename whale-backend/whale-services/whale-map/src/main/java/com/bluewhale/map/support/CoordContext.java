package com.bluewhale.map.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.config.MapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 坐标系口径中心。
 *
 * <p>「对外 GCJ-02、对内 WGS84」这条约定必须只有一个实现：散落在各 Service 里各写一份
 * {@code CoordType.parse}，只要有一处兜底值写错，那一条链路就会把 GCJ-02 当 WGS84 存进库，
 * 而且从数据上看不出任何异常。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CoordContext {

    /** 坐标保留位数（7 位约 1 厘米） */
    private static final double ROUND_SCALE = 1.0E7;

    /** 服务配置 */
    private final MapProperties properties;

    /**
     * 对外展示坐标系
     *
     * @return 坐标系（配置非法时回落 GCJ-02）
     */
    public CoordType display() {
        CoordType type = CoordType.parse(properties.getDisplayCoord());
        return type == null ? CoordType.GCJ02 : type;
    }

    /**
     * 内部存储坐标系
     *
     * @return 坐标系（配置非法时回落 WGS84）
     */
    public CoordType storage() {
        CoordType type = CoordType.parse(properties.getStorageCoord());
        return type == null ? CoordType.WGS84 : type;
    }

    /**
     * 把外部坐标转到存储坐标系
     *
     * @param lng       经度
     * @param lat       纬度
     * @param coordText 坐标系文本（可空，缺省按展示坐标系）
     * @return 存储坐标系下的点
     */
    public GeoPoint toStorage(Double lng, Double lat, String coordText) {
        if (lng == null || lat == null) {
            throw new BizException(MapErrorCode.COORD_INVALID, "map.coord.empty", "坐标不能为空");
        }
        CoordType type = CoordType.parse(coordText);
        if (type == null) {
            type = display();
        }
        try {
            return CoordTransform.convert(new GeoPoint(lng, lat, type), storage());
        } catch (IllegalArgumentException ex) {
            throw new BizException(MapErrorCode.COORD_INVALID, ex.getMessage());
        }
    }

    /**
     * 把存储坐标转到展示坐标系
     *
     * @param storage 存储坐标系下的点
     * @return 展示坐标系下的点
     */
    public GeoPoint toDisplay(GeoPoint storage) {
        return CoordTransform.convert(storage, display());
    }

    /**
     * 坐标舍入，避免把浮点噪声吐给前端
     *
     * @param value 原始值
     * @return 舍入到 7 位小数的值
     */
    public double round(double value) {
        return Math.round(value * ROUND_SCALE) / ROUND_SCALE;
    }
}
