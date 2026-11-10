package com.bluewhale.map.provider.amap;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.provider.GeocodeQuery;
import com.bluewhale.map.provider.GeocodeResult;
import com.bluewhale.map.provider.MapCapability;
import com.bluewhale.map.provider.MapProvider;
import com.bluewhale.map.provider.Poi;
import com.bluewhale.map.provider.PoiPage;
import com.bluewhale.map.provider.PoiQuery;
import com.bluewhale.map.provider.RegeoQuery;
import com.bluewhale.map.provider.RegeoResult;
import com.bluewhale.map.provider.RouteMode;
import com.bluewhale.map.provider.RouteQuery;
import com.bluewhale.map.provider.RouteResult;
import com.bluewhale.map.provider.support.JsonNodes;
import com.bluewhale.map.provider.support.ProviderHttp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图适配器。
 *
 * <p><b>为什么第二家是高德</b>：它和腾讯同为 GCJ-02，转换口径一致，出问题时不会把
 * 「坐标系算错」和「供应商差异」混在一起；同时它的路径规划在国内覆盖较好，
 * 适合作为 {@code ROUTE} 能力的首选。</p>
 *
 * <p><b>数字都是字符串</b>：高德把 {@code distance}、{@code duration}、{@code count}
 * 一律返回成字符串，且空字段可能是字面量 {@code "[]"}。取数统一走 {@link JsonNodes}，
 * 它已把这类空值折成 {@code null}。</p>
 *
 * <p><b>耗时单位</b>：高德返回秒，这里换算成分钟，与腾讯适配器保持一致。</p>
 *
 * @author BlueWhale
 */
@Component
public class AmapMapProvider implements MapProvider {

    /** 供应商名（与配置键一致） */
    private static final String NAME = "amap";

    /** 状态码：成功（高德用字符串表达） */
    private static final String STATUS_OK = "1";

    /** 地理编码路径 */
    private static final String PATH_GEOCODE = "/v3/geocode/geo";

    /** 逆地理路径 */
    private static final String PATH_REGEO = "/v3/geocode/regeo";

    /** 地点检索路径 */
    private static final String PATH_PLACE = "/v3/place/text";

    /** 驾车路径 */
    private static final String PATH_DRIVING = "/v3/direction/driving";

    /** 步行路径 */
    private static final String PATH_WALKING = "/v3/direction/walking";

    /** 骑行路径 */
    private static final String PATH_CYCLING = "/v3/direction/bicycling";

    /** 参数名：Key */
    private static final String PARAM_KEY = "key";

    /** 折线点数量上限：超出只取前 N 个，避免一条长途路线把响应撑到几 MB */
    private static final int MAX_POLYLINE_POINTS = 2000;

    /** 供应商配置；未开通时为 {@code null} */
    private final MapProperties.ProviderConfig config;

    /** HTTP 客户端；未开通时为 {@code null} */
    private final RestClient client;

    /**
     * 按配置构建适配器
     *
     * @param properties 地图服务配置
     */
    public AmapMapProvider(MapProperties properties) {
        this.config = properties.getProviders().get(NAME);
        boolean ready = config != null && StringUtils.hasText(config.getKey())
                && StringUtils.hasText(config.getBaseUrl());
        this.client = ready ? ProviderHttp.build(config) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(MapCapability capability) {
        return capability != MapCapability.DISTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configured() {
        return client != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeocodeResult geocode(GeocodeQuery query) {
        MultiValueMap<String, String> params = baseParams();
        params.add("address", query.address());
        if (query.city() != null) {
            params.add("city", query.city());
        }
        JsonNode root = ProviderHttp.get(client, NAME, PATH_GEOCODE, params);
        requireOk(root);
        List<JsonNode> geocodes = JsonNodes.items(root, "geocodes");
        if (geocodes.isEmpty()) {
            throw new BizException(MapErrorCode.GEOCODE_EMPTY, "amap 未匹配到地址");
        }
        JsonNode first = geocodes.get(0);
        GeoPoint point = parseLngLat(JsonNodes.text(first, "location"), MapErrorCode.GEOCODE_EMPTY);
        return new GeocodeResult(point, JsonNodes.text(first, "formatted_address"),
                JsonNodes.text(first, "province"), JsonNodes.text(first, "city"),
                JsonNodes.text(first, "district"), JsonNodes.text(first, "adcode"),
                JsonNodes.text(first, "level"), NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RegeoResult regeo(RegeoQuery query) {
        GeoPoint gcj = CoordTransform.convert(query.point(), CoordType.GCJ02);
        MultiValueMap<String, String> params = baseParams();
        params.add("location", gcj.lng() + "," + gcj.lat());
        params.add("radius", String.valueOf(query.radiusMeters()));
        params.add("extensions", "base");
        JsonNode root = ProviderHttp.get(client, NAME, PATH_REGEO, params);
        requireOk(root);
        JsonNode regeocode = root.get("regeocode");
        String formatted = JsonNodes.text(regeocode, "formatted_address");
        if (formatted == null) {
            throw new BizException(MapErrorCode.REGEO_EMPTY, "amap 未匹配到地址");
        }
        JsonNode comp = JsonNodes.child(regeocode, "addressComponent");
        return new RegeoResult(formatted, JsonNodes.text(comp, "province"), JsonNodes.text(comp, "city"),
                JsonNodes.text(comp, "district"), JsonNodes.text(comp, "adcode"),
                JsonNodes.text(comp, "street"), JsonNodes.text(comp, "streetNumber"), NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoiPage searchPoi(PoiQuery query) {
        MultiValueMap<String, String> params = baseParams();
        params.add("keywords", query.keyword());
        params.add("city", query.city() == null ? "" : query.city());
        params.add("page", String.valueOf(query.page()));
        params.add("offset", String.valueOf(query.size()));
        JsonNode root = ProviderHttp.get(client, NAME, PATH_PLACE, params);
        requireOk(root);
        List<Poi> items = new ArrayList<>();
        for (JsonNode node : JsonNodes.items(root, "pois")) {
            items.add(new Poi(JsonNodes.text(node, "id"), JsonNodes.text(node, "name"),
                    JsonNodes.text(node, "address"),
                    parseLngLat(JsonNodes.text(node, "location"), MapErrorCode.POI_EMPTY),
                    JsonNodes.text(node, "adcode"), JsonNodes.text(node, "type"),
                    JsonNodes.number(node, "distance")));
        }
        Double total = JsonNodes.number(root, "count");
        return new PoiPage(items, total == null ? items.size() : total.intValue(),
                query.page(), query.size(), NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RouteResult route(RouteQuery query) {
        GeoPoint from = CoordTransform.convert(query.origin(), CoordType.GCJ02);
        GeoPoint to = CoordTransform.convert(query.destination(), CoordType.GCJ02);
        MultiValueMap<String, String> params = baseParams();
        params.add("origin", from.lng() + "," + from.lat());
        params.add("destination", to.lng() + "," + to.lat());
        JsonNode root = ProviderHttp.get(client, NAME, pathOf(query.mode()), params);
        requireOk(root);
        List<JsonNode> paths = JsonNodes.items(JsonNodes.child(root, "route"), "paths");
        if (paths.isEmpty()) {
            throw new BizException(MapErrorCode.ROUTE_EMPTY, "amap 未返回可用路线");
        }
        JsonNode first = paths.get(0);
        Double distance = JsonNodes.number(first, "distance");
        Double seconds = JsonNodes.number(first, "duration");
        int minutes = seconds == null ? 0 : (int) Math.ceil(seconds / 60.0);
        return new RouteResult(distance == null ? 0 : distance, minutes, query.mode(), NAME,
                parsePolyline(JsonNodes.text(first, "polyline")));
    }

    /**
     * 组装带 Key 的公共参数
     *
     * @return 参数表
     */
    private MultiValueMap<String, String> baseParams() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PARAM_KEY, config.getKey());
        return params;
    }

    /**
     * 校验高德状态码
     *
     * @param root 响应根节点
     */
    private void requireOk(JsonNode root) {
        String status = JsonNodes.text(root, "status");
        if (!STATUS_OK.equals(status)) {
            throw new BizException(MapErrorCode.PROVIDER_FAILED,
                    "amap 返回 status=" + status + "，infocode=" + JsonNodes.text(root, "infocode"));
        }
    }

    /**
     * 解析 {@code lng,lat} 坐标串并转为 WGS84
     *
     * @param text      坐标串
     * @param errorCode 解析失败使用的错误码
     * @return WGS84 坐标点
     */
    private GeoPoint parseLngLat(String text, int errorCode) {
        GeoPoint gcj = JsonNodes.parseLngLat(text, CoordType.GCJ02);
        if (gcj == null) {
            throw new BizException(errorCode, "amap 返回结果缺少坐标");
        }
        return CoordTransform.convert(gcj, CoordType.WGS84);
    }

    /**
     * 解析高德折线串（{@code lng,lat;lng,lat;...}）
     *
     * @param text 折线串
     * @return 折线点列表（WGS84）
     */
    private List<GeoPoint> parsePolyline(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] points = text.split(";");
        int limit = Math.min(points.length, MAX_POLYLINE_POINTS);
        List<GeoPoint> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            GeoPoint gcj = JsonNodes.parseLngLat(points[i], CoordType.GCJ02);
            if (gcj != null) {
                result.add(CoordTransform.convert(gcj, CoordType.WGS84));
            }
        }
        return result;
    }

    /**
     * 出行方式映射到高德路径
     *
     * @param mode 出行方式
     * @return 接口路径
     */
    private String pathOf(RouteMode mode) {
        return switch (mode) {
            case DRIVING -> PATH_DRIVING;
            case WALKING -> PATH_WALKING;
            case CYCLING -> PATH_CYCLING;
        };
    }
}
