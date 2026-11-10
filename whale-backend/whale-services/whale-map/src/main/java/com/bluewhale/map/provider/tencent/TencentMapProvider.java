package com.bluewhale.map.provider.tencent;

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
 * 腾讯位置服务适配器。
 *
 * <p><b>坐标系</b>：腾讯全系使用 GCJ-02，入参出参都在这里与 WGS84 互转，上层无感知。</p>
 *
 * <p><b>字段陷阱</b>：地理编码返回 {@code address_components}（复数），逆地理返回
 * {@code address_component}（单数）。照文档抄错一个字母就会静默拿到空的省市区，
 * 所以两者各写各的取法，不做「通用组件提取」。</p>
 *
 * <p><b>耗时单位</b>：路径规划的 {@code duration} 是分钟，与高德的秒不同，
 * 在这里就地换算成分钟，避免上层拿到两种量纲。</p>
 *
 * @author BlueWhale
 */
@Component
public class TencentMapProvider implements MapProvider {

    /** 供应商名（与配置键一致） */
    private static final String NAME = "tencent";

    /** 状态码：成功 */
    private static final int STATUS_OK = 0;

    /** 状态码：无匹配结果 */
    private static final int STATUS_EMPTY = 347;

    /** 地理编码与逆地理共用路径 */
    private static final String PATH_GEOCODER = "/ws/geocoder/v1/";

    /** 地点检索路径 */
    private static final String PATH_PLACE = "/ws/place/v1/search";

    /** 驾车路径 */
    private static final String PATH_DRIVING = "/ws/direction/v1/driving/";

    /** 步行路径 */
    private static final String PATH_WALKING = "/ws/direction/v1/walking/";

    /** 骑行路径 */
    private static final String PATH_CYCLING = "/ws/direction/v1/bicycling/";

    /** 参数名：Key */
    private static final String PARAM_KEY = "key";

    /** 供应商配置；未开通时为 {@code null} */
    private final MapProperties.ProviderConfig config;

    /** HTTP 客户端；未开通时为 {@code null} */
    private final RestClient client;

    /**
     * 按配置构建适配器
     *
     * @param properties 地图服务配置
     */
    public TencentMapProvider(MapProperties properties) {
        this.config = properties.getProviders().get(NAME);
        boolean ready = config != null && StringUtils.hasText(config.getKey()) && StringUtils.hasText(config.getBaseUrl());
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
            params.add("region", query.city());
        }
        JsonNode root = ProviderHttp.get(client, NAME, PATH_GEOCODER, params);
        JsonNode result = requireResult(root, MapErrorCode.GEOCODE_EMPTY);
        GeoPoint point = toWgs84(result.get("location"), MapErrorCode.GEOCODE_EMPTY);
        JsonNode comp = result.get("address_components");
        return new GeocodeResult(point, JsonNodes.text(result, "title"),
                JsonNodes.text(comp, "province"), JsonNodes.text(comp, "city"),
                JsonNodes.text(comp, "district"), JsonNodes.text(result, "adcode"),
                JsonNodes.text(result, "level"), NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RegeoResult regeo(RegeoQuery query) {
        GeoPoint gcj = CoordTransform.convert(query.point(), CoordType.GCJ02);
        MultiValueMap<String, String> params = baseParams();
        params.add("location", gcj.lat() + "," + gcj.lng());
        params.add("get_poi", "0");
        params.add("radius", String.valueOf(query.radiusMeters()));
        JsonNode root = ProviderHttp.get(client, NAME, PATH_GEOCODER, params);
        JsonNode result = requireResult(root, MapErrorCode.REGEO_EMPTY);
        JsonNode comp = result.get("address_component");
        return new RegeoResult(JsonNodes.text(result, "address"),
                JsonNodes.text(comp, "province"), JsonNodes.text(comp, "city"),
                JsonNodes.text(comp, "district"), JsonNodes.text(result.get("ad_info"), "adcode"),
                JsonNodes.text(comp, "street"), JsonNodes.text(comp, "street_number"), NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoiPage searchPoi(PoiQuery query) {
        MultiValueMap<String, String> params = baseParams();
        params.add("keyword", query.keyword());
        params.add("boundary", "region(" + (query.city() == null ? "全国" : query.city()) + ",0)");
        params.add("page_index", String.valueOf(query.page()));
        params.add("page_size", String.valueOf(query.size()));
        JsonNode root = ProviderHttp.get(client, NAME, PATH_PLACE, params);
        requireStatus(root, MapErrorCode.PROVIDER_FAILED);
        List<Poi> items = new ArrayList<>();
        for (JsonNode node : JsonNodes.items(root, "data")) {
            items.add(new Poi(JsonNodes.text(node, "id"), JsonNodes.text(node, "title"),
                    JsonNodes.text(node, "address"), toWgs84(node.get("location"), MapErrorCode.POI_EMPTY),
                    JsonNodes.text(node.get("ad_info"), "adcode"), JsonNodes.text(node, "category"),
                    JsonNodes.number(node, "_distance")));
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
        params.add("from", from.lat() + "," + from.lng());
        params.add("to", to.lat() + "," + to.lng());
        JsonNode root = ProviderHttp.get(client, NAME, pathOf(query.mode()), params);
        requireStatus(root, MapErrorCode.ROUTE_EMPTY);
        JsonNode routes = JsonNodes.child(root, "result", "routes");
        JsonNode first = routes == null || !routes.isArray() || routes.isEmpty() ? null : routes.get(0);
        if (first == null) {
            throw new BizException(MapErrorCode.ROUTE_EMPTY, "tencent 未返回可用路线");
        }
        Double distance = JsonNodes.number(first, "distance");
        Double duration = JsonNodes.number(first, "duration");
        // 腾讯的 duration 单位是分钟，不需要再除 60
        return new RouteResult(distance == null ? 0 : distance, duration == null ? 0 : duration.intValue(),
                query.mode(), NAME, List.of());
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
     * 校验状态码，允许无结果错误码透传
     *
     * @param root      响应根节点
     * @param emptyCode 无结果时使用的错误码
     */
    private void requireStatus(JsonNode root, int emptyCode) {
        JsonNode status = root.get("status");
        int code = status == null ? -1 : status.asInt(-1);
        if (code == STATUS_EMPTY) {
            throw new BizException(emptyCode, "tencent 未匹配到结果");
        }
        if (code != STATUS_OK) {
            throw new BizException(MapErrorCode.PROVIDER_FAILED,
                    "tencent 返回 status=" + code + "，" + JsonNodes.text(root, "message"));
        }
    }

    /**
     * 校验状态码并取出 {@code result} 节点
     *
     * @param root      响应根节点
     * @param emptyCode 无结果时使用的错误码
     * @return result 节点
     */
    private JsonNode requireResult(JsonNode root, int emptyCode) {
        requireStatus(root, emptyCode);
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            throw new BizException(emptyCode, "tencent 未返回结果");
        }
        return result;
    }

    /**
     * 把腾讯的 GCJ-02 坐标点转为 WGS84
     *
     * @param node     含 {@code lng}/{@code lat} 的节点
     * @param errorCode 坐标缺失时使用的错误码
     * @return WGS84 坐标点
     */
    private GeoPoint toWgs84(JsonNode node, int errorCode) {
        Double lng = JsonNodes.number(node, "lng");
        Double lat = JsonNodes.number(node, "lat");
        if (lng == null || lat == null) {
            throw new BizException(errorCode, "tencent 返回结果缺少坐标");
        }
        return CoordTransform.convert(new GeoPoint(lng, lat, CoordType.GCJ02), CoordType.WGS84);
    }

    /**
     * 出行方式映射到腾讯路径
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
