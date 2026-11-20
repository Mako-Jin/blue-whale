package com.bluewhale.map.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.common.geo.GeoDistance;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.dto.DistanceRequest;
import com.bluewhale.map.dto.GeocodeRequest;
import com.bluewhale.map.dto.PointRequest;
import com.bluewhale.map.dto.RegeoRequest;
import com.bluewhale.map.dto.RouteRequest;
import com.bluewhale.map.provider.GeocodeQuery;
import com.bluewhale.map.provider.GeocodeResult;
import com.bluewhale.map.provider.MapCapability;
import com.bluewhale.map.provider.MapProvider;
import com.bluewhale.map.provider.Poi;
import com.bluewhale.map.provider.PoiPage;
import com.bluewhale.map.provider.PoiQuery;
import com.bluewhale.map.provider.ProviderRegistry;
import com.bluewhale.map.provider.RegeoQuery;
import com.bluewhale.map.provider.RegeoResult;
import com.bluewhale.map.provider.RouteMode;
import com.bluewhale.map.provider.RouteQuery;
import com.bluewhale.map.provider.RouteResult;
import com.bluewhale.map.service.MapGeoService;
import com.bluewhale.map.service.MapQuotaService;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.support.MapCacheSupport;
import com.bluewhale.map.vo.DistanceVO;
import com.bluewhale.map.vo.GeoPointVO;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.PoiPageVO;
import com.bluewhale.map.vo.PoiVO;
import com.bluewhale.map.vo.ProviderHealthVO;
import com.bluewhale.map.vo.RegeoVO;
import com.bluewhale.map.vo.RouteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 地图解析服务实现。
 *
 * <p><b>降级不是重试</b>：主供应商超时后立刻换下一家，而不是原样重试。重试只会让用户多等
 * 一个超时周期，且对「Key 失效」「配额用尽」这类确定性失败完全无效。</p>
 *
 * <p><b>每次都先占配额再调用</b>：占配额与调用是两步，天然存在「占了没用完」的浪费，
 * 但反过来（调用完再计数）会在并发突发时直接打穿上限。配额是稀缺资源，宁可少用不可超用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapGeoServiceImpl implements MapGeoService {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 逆地理默认半径（米） */
    private static final int DEFAULT_REGEO_RADIUS = 1000;

    /** 缓存键中的坐标精度（小数点后位数，6 位约 0.1 米） */
    private static final int CACHE_COORD_SCALE = 6;

    /** 地图供应商注册表 */
    private final ProviderRegistry registry;

    /** 配额服务 */
    private final MapQuotaService quotaService;

    /** 缓存 */
    private final MapCacheSupport cache;

    /** 坐标系口径 */
    private final CoordContext coords;

    /** 服务配置 */
    private final MapProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public GeocodeVO geocode(GeocodeRequest request) {
        CoordType display = displayType();
        GeocodeQuery query = new GeocodeQuery(request.getAddress(), request.getCity());
        String key = cacheKey(MapCacheSupport.TYPE_GEOCODE, display.name(), query.address(), query.city());
        GeocodeVO hit = cache.get(registry.preferred(MapCapability.GEOCODE).name(),
                MapCacheSupport.TYPE_GEOCODE, key, GeocodeVO.class);
        if (hit != null) {
            hit.setCached(true);
            return hit;
        }
        GeocodeVO result = withProviders(MapCapability.GEOCODE, provider -> toVo(provider.geocode(query), display));
        cache.put(result.getProvider(), MapCacheSupport.TYPE_GEOCODE, key, query.address(), result);
        result.setCached(false);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RegeoVO regeo(RegeoRequest request) {
        CoordType display = displayType();
        GeoPoint storage = toStoragePoint(request.getLng(), request.getLat(), request.getCoordType());
        int radius = request.getRadiusMeters() == null ? DEFAULT_REGEO_RADIUS : request.getRadiusMeters();
        String coordKey = String.format(Locale.ROOT, "%." + CACHE_COORD_SCALE + "f,%." + CACHE_COORD_SCALE + "f",
                storage.lng(), storage.lat());
        String key = cacheKey(MapCacheSupport.TYPE_REGEO, display.name(), coordKey, String.valueOf(radius));
        RegeoVO hit = cache.get(registry.preferred(MapCapability.REGEO).name(),
                MapCacheSupport.TYPE_REGEO, key, RegeoVO.class);
        if (hit != null) {
            return hit;
        }
        RegeoQuery query = new RegeoQuery(storage, radius);
        RegeoVO result = withProviders(MapCapability.REGEO, provider -> toVo(provider.regeo(query), storage, display));
        cache.put(result.getProvider(), MapCacheSupport.TYPE_REGEO, key, null, result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoiPageVO searchPoi(String keyword, String city, Integer page, Integer size) {
        int pageNo = page == null ? 1 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        PoiQuery query = new PoiQuery(keyword, city, pageNo, pageSize);
        CoordType display = displayType();
        String key = cacheKey(MapCacheSupport.TYPE_POI, display.name(), query.keyword(), query.city(),
                String.valueOf(pageNo), String.valueOf(pageSize));
        PoiPageVO hit = cache.get(registry.preferred(MapCapability.POI).name(),
                MapCacheSupport.TYPE_POI, key, PoiPageVO.class);
        if (hit != null) {
            return hit;
        }
        PoiPageVO result = withProviders(MapCapability.POI, provider -> toVo(provider.searchPoi(query), display));
        cache.put(result.getProvider(), MapCacheSupport.TYPE_POI, key, query.keyword(), result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RouteVO route(RouteRequest request) {
        CoordType display = displayType();
        GeoPoint origin = toStoragePoint(request.getOriginLng(), request.getOriginLat(), request.getCoordType());
        GeoPoint destination = toStoragePoint(request.getDestinationLng(), request.getDestinationLat(),
                request.getCoordType());
        RouteMode mode = RouteMode.parse(request.getMode());
        RouteQuery query = new RouteQuery(origin, destination, mode);
        return withProviders(MapCapability.ROUTE, provider -> toVo(provider.route(query), display));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DistanceVO> distance(DistanceRequest request) {
        GeoPoint origin = toStoragePoint(request.getOriginLng(), request.getOriginLat(), request.getCoordType());
        List<PointRequest> targets = request.getTargets();
        if (targets.size() > properties.getLocation().getDistanceMaxBatch()) {
            throw new BizException(MapErrorCode.DISTANCE_BATCH_TOO_LARGE, "map.geo.distance-batch-exceeded",
                    "单次最多 " + properties.getLocation().getDistanceMaxBatch() + " 个目标点",
                    properties.getLocation().getDistanceMaxBatch());
        }
        List<DistanceVO> results = new ArrayList<>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            PointRequest target = targets.get(index);
            GeoPoint point = toStoragePoint(target.getLng(), target.getLat(), request.getCoordType());
            DistanceVO item = new DistanceVO();
            item.setIndex(index);
            item.setDistanceMeters(round(GeoDistance.meters(origin, point)));
            results.add(item);
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProviderHealthVO> health() {
        List<ProviderHealthVO> list = new ArrayList<>();
        for (MapProvider provider : registry.all().values()) {
            ProviderHealthVO view = quotaService.quotaOf(provider.name());
            view.setConfigured(provider.configured());
            view.setCapabilities(Arrays.stream(MapCapability.values())
                    .filter(provider::supports).map(Enum::name).toList());
            view.setPreferred(Arrays.stream(MapCapability.values())
                    .anyMatch(capability -> provider.name().equals(registry.routeOf(capability))));
            list.add(view);
        }
        return list;
    }

    /**
     * 按能力依次尝试候选供应商
     *
     * @param capability 能力
     * @param call       单次调用
     * @param <T>        结果类型
     * @return 首个成功的结果
     */
    private <T> T withProviders(MapCapability capability, Function<MapProvider, T> call) {
        List<MapProvider> chain = registry.chain(capability);
        if (chain.isEmpty()) {
            throw new BizException(MapErrorCode.PROVIDER_UNAVAILABLE, "map.geo.provider-unavailable",
                    "没有可用的地图供应商（能力 " + capability + "）", capability);
        }
        BizException last = null;
        for (MapProvider provider : chain) {
            if (!quotaService.tryAcquire(provider.name())) {
                log.warn("供应商 {} 今日配额已用尽，跳过", provider.name());
                continue;
            }
            try {
                return call.apply(provider);
            } catch (BizException ex) {
                last = ex;
                log.warn("供应商 {} 未完成调用，尝试下一个：{}", provider.name(), ex.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
        throw new BizException(MapErrorCode.QUOTA_EXCEEDED, "map.geo.quota-exhausted", "全部地图供应商今日配额已用尽");
    }

    /**
     * 地址解析结果转视图
     *
     * @param result  供应商结果（WGS84）
     * @param display 展示坐标系
     * @return 视图
     */
    private GeocodeVO toVo(GeocodeResult result, CoordType display) {
        GeoPoint point = CoordTransform.convert(result.point(), display);
        GeocodeVO vo = new GeocodeVO();
        vo.setLng(round(point.lng()));
        vo.setLat(round(point.lat()));
        vo.setCoordType(display.name());
        vo.setFormattedAddress(result.formattedAddress());
        vo.setProvince(result.province());
        vo.setCity(result.city());
        vo.setDistrict(result.district());
        vo.setAdcode(result.adcode());
        vo.setLevel(result.level());
        vo.setProvider(result.provider());
        return vo;
    }

    /**
     * 逆地理结果转视图
     *
     * @param result  供应商结果
     * @param storage 请求点（存储坐标系）
     * @param display 展示坐标系
     * @return 视图
     */
    private RegeoVO toVo(RegeoResult result, GeoPoint storage, CoordType display) {
        GeoPoint point = CoordTransform.convert(storage, display);
        RegeoVO vo = new RegeoVO();
        vo.setLng(round(point.lng()));
        vo.setLat(round(point.lat()));
        vo.setCoordType(display.name());
        vo.setFormattedAddress(result.formattedAddress());
        vo.setProvince(result.province());
        vo.setCity(result.city());
        vo.setDistrict(result.district());
        vo.setAdcode(result.adcode());
        vo.setStreet(result.street());
        vo.setStreetNumber(result.streetNumber());
        vo.setProvider(result.provider());
        return vo;
    }

    /**
     * 地点检索结果转视图
     *
     * @param page    供应商分页结果
     * @param display 展示坐标系
     * @return 视图
     */
    private PoiPageVO toVo(PoiPage page, CoordType display) {
        List<PoiVO> items = new ArrayList<>(page.items().size());
        for (Poi poi : page.items()) {
            PoiVO vo = new PoiVO();
            vo.setId(poi.id());
            vo.setName(poi.name());
            vo.setAddress(poi.address());
            if (poi.point() != null) {
                GeoPoint point = CoordTransform.convert(poi.point(), display);
                vo.setLng(round(point.lng()));
                vo.setLat(round(point.lat()));
                vo.setCoordType(display.name());
            }
            vo.setAdcode(poi.adcode());
            vo.setCategory(poi.category());
            vo.setDistanceMeters(poi.distanceMeters());
            items.add(vo);
        }
        PoiPageVO vo = new PoiPageVO();
        vo.setItems(items);
        vo.setTotal(page.total());
        vo.setPage(page.page());
        vo.setSize(page.size());
        vo.setProvider(page.provider());
        return vo;
    }

    /**
     * 路径结果转视图
     *
     * @param result  供应商结果
     * @param display 展示坐标系
     * @return 视图
     */
    private RouteVO toVo(RouteResult result, CoordType display) {
        RouteVO vo = new RouteVO();
        vo.setDistanceMeters(round(result.distanceMeters()));
        vo.setDurationMinutes(result.durationMinutes());
        vo.setMode(result.mode().name());
        vo.setProvider(result.provider());
        List<GeoPointVO> polyline = new ArrayList<>(result.polyline().size());
        for (GeoPoint point : result.polyline()) {
            GeoPoint converted = CoordTransform.convert(point, display);
            GeoPointVO item = new GeoPointVO();
            item.setLng(round(converted.lng()));
            item.setLat(round(converted.lat()));
            item.setCoordType(display.name());
            polyline.add(item);
        }
        vo.setPolyline(polyline);
        return vo;
    }

    /**
     * 把请求坐标转到存储坐标系
     *
     * @param lng       经度
     * @param lat       纬度
     * @param coordText 坐标系文本（可空，缺省用展示坐标系）
     * @return 存储坐标系下的点
     */
    private GeoPoint toStoragePoint(Double lng, Double lat, String coordText) {
        return coords.toStorage(lng, lat, coordText);
    }

    /**
     * 组装缓存键：类型 + 坐标系 + 各段规整文本
     *
     * @param parts 键组成部分
     * @return SHA-256 键
     */
    private String cacheKey(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(cache.normalize(part)).append('|');
        }
        return cache.keyOf(builder.toString());
    }

    /**
     * 展示坐标系
     *
     * @return 坐标系
     */
    private CoordType displayType() {
        return coords.display();
    }

    /**
     * 存储坐标系
     *
     * @return 坐标系
     */
    private CoordType storageType() {
        return coords.storage();
    }

    /**
     * 坐标保留 7 位小数（约 1 厘米），避免把浮点噪声吐给前端
     *
     * @param value 原始值
     * @return 舍入后的值
     */
    private double round(double value) {
        return coords.round(value);
    }
}
