package com.bluewhale.map.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图服务配置。
 *
 * <p><b>坐标系口径</b>：{@code storageCoord} 决定落库坐标，{@code displayCoord} 决定对前端输出，
 * 两者都在服务端定死。原因是坐标一旦允许调用方自选，同一张表里就会混进 GCJ-02 与 WGS84
 * 两种点，差值 300–500 米，后续任何距离计算都会错，且错得「看起来正常」。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.map")
public class MapProperties {

    /** 默认供应商（能力路由未配置时使用） */
    private String defaultProvider = "tencent";

    /** 对外输出的坐标系（前端展示用） */
    private String displayCoord = "GCJ02";

    /** 落库存储的坐标系（内部计算基准） */
    private String storageCoord = "WGS84";

    /** 能力 → 供应商名，键为 {@code MapCapability} 枚举名 */
    private Map<String, String> capabilityRouting = new LinkedHashMap<>();

    /** 降级顺序：主供应商不可用/超配额/结果为空时按序尝试 */
    private List<String> fallback = new ArrayList<>();

    /** 供应商配置，键为供应商名 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 缓存配置 */
    private Cache cache = new Cache();

    /** 打点与附近查询配置 */
    private LocationConfig location = new LocationConfig();

    /**
     * 单个供应商的接入配置。
     */
    @Data
    public static class ProviderConfig {

        /** 服务端 Key（空表示未开通，该供应商自动视为不可用） */
        private String key;

        /** 接口基址（测试环境或代理网关可改） */
        private String baseUrl;

        /** 日配额上限（0 表示不限） */
        private int quotaPerDay = 10000;

        /** 建连超时（毫秒） */
        private int connectTimeoutMillis = 2000;

        /** 读超时（毫秒） */
        private int readTimeoutMillis = 5000;
    }

    /**
     * 解析结果缓存配置。
     */
    @Data
    public static class Cache {

        /** 是否启用缓存 */
        private boolean enabled = true;

        /** 缓存有效期（小时），默认 90 天：行政区划与地址库变动远慢于此 */
        private int ttlHours = 2160;

        /** 允许缓存的最大地址长度，超长不缓存 */
        private int maxTextLength = 200;
    }

    /**
     * 打点与附近查询配置。
     */
    @Data
    public static class LocationConfig {

        /** 附近查询半径上限（米） */
        private int nearbyMaxRadiusMeters = 5000;

        /** 附近查询返回条数上限 */
        private int nearbyMaxRows = 200;

        /** 单次批量距离计算点数上限 */
        private int distanceMaxBatch = 200;
    }
}
