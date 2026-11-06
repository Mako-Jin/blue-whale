package com.bluewhale.map.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.dto.DistanceRequest;
import com.bluewhale.map.dto.GeocodeRequest;
import com.bluewhale.map.dto.PointRequest;
import com.bluewhale.map.provider.GeocodeResult;
import com.bluewhale.map.provider.MapCapability;
import com.bluewhale.map.provider.MapProvider;
import com.bluewhale.map.provider.ProviderRegistry;
import com.bluewhale.map.service.MapQuotaService;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.support.MapCacheSupport;
import com.bluewhale.map.vo.DistanceVO;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.ProviderHealthVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 地图解析服务测试。
 *
 * <p>重点覆盖「降级链」与「配额准入」两条最容易写错的路径：降级的价值在于主供应商挂了
 * 仍能出结果，配额的价值在于不把接口打到供应商限流；两者都在编排层，适配器测试覆盖不到。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapGeoServiceImplTest {

    /** 天安门 WGS84 经度 */
    private static final double LNG = 116.397428;

    /** 天安门 WGS84 纬度 */
    private static final double LAT = 39.90923;

    /** 配额服务 */
    @Mock
    private MapQuotaService quotaService;

    /** 缓存 */
    @Mock
    private MapCacheSupport cache;

    /** 服务配置 */
    private MapProperties properties;

    /** 主供应商 */
    private MapProvider tencent;

    /** 备供应商 */
    private MapProvider amap;

    /** 被测服务 */
    private MapGeoServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MapProperties();
        properties.setDefaultProvider("tencent");
        properties.getCapabilityRouting().put(MapCapability.GEOCODE.name(), "tencent");
        properties.setFallback(List.of("amap"));
        tencent = provider("tencent");
        amap = provider("amap");
        ProviderRegistry registry = new ProviderRegistry(List.of(tencent, amap), properties);
        service = new MapGeoServiceImpl(registry, quotaService, cache, new CoordContext(properties), properties);
        when(cache.normalize(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value == null ? "" : String.valueOf(value).trim();
        });
        when(cache.keyOf(anyString())).thenReturn("cache-key");
        when(quotaService.tryAcquire(anyString())).thenReturn(true);
        when(quotaService.quotaOf(anyString())).thenAnswer(invocation -> {
            ProviderHealthVO view = new ProviderHealthVO();
            view.setName(invocation.getArgument(0));
            return view;
        });
    }

    @Test
    @DisplayName("未命中缓存时调用主供应商，并把坐标转成展示坐标系")
    void geocodeUsesPreferredProvider() {
        when(cache.get(anyString(), anyString(), anyString(), any())).thenReturn(null);
        when(tencent.geocode(any())).thenReturn(result("tencent"));
        GeocodeVO vo = service.geocode(request());
        assertNotNull(vo);
        assertEquals("tencent", vo.getProvider());
        assertEquals("GCJ02", vo.getCoordType());
        assertTrue(vo.getLng() > LNG, "展示坐标应已加偏");
        assertEquals(false, vo.isCached());
    }

    @Test
    @DisplayName("命中缓存直接返回，且标记 cached")
    void geocodeReturnsCached() {
        GeocodeVO cached = new GeocodeVO();
        cached.setProvider("tencent");
        when(cache.get(anyString(), anyString(), anyString(), any())).thenReturn(cached);
        GeocodeVO vo = service.geocode(request());
        assertTrue(vo.isCached());
        assertEquals("tencent", vo.getProvider());
    }

    @Test
    @DisplayName("主供应商失败时降级到备供应商")
    void geocodeFallsBack() {
        when(cache.get(anyString(), anyString(), anyString(), any())).thenReturn(null);
        when(tencent.geocode(any())).thenThrow(new BizException(MapErrorCode.PROVIDER_FAILED, "超时"));
        when(amap.geocode(any())).thenReturn(result("amap"));
        GeocodeVO vo = service.geocode(request());
        assertEquals("amap", vo.getProvider());
    }

    @Test
    @DisplayName("全部供应商失败时抛出最后一次失败原因")
    void geocodeAllProvidersFail() {
        when(cache.get(anyString(), anyString(), anyString(), any())).thenReturn(null);
        when(tencent.geocode(any())).thenThrow(new BizException(MapErrorCode.PROVIDER_FAILED, "tencent 超时"));
        when(amap.geocode(any())).thenThrow(new BizException(MapErrorCode.GEOCODE_EMPTY, "amap 无结果"));
        BizException ex = assertThrows(BizException.class, () -> service.geocode(request()));
        assertEquals(MapErrorCode.GEOCODE_EMPTY, ex.getCode());
    }

    @Test
    @DisplayName("全部供应商配额用尽时返回配额错误")
    void quotaExhausted() {
        when(cache.get(anyString(), anyString(), anyString(), any())).thenReturn(null);
        when(quotaService.tryAcquire(anyString())).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> service.geocode(request()));
        assertEquals(MapErrorCode.QUOTA_EXCEEDED, ex.getCode());
    }

    @Test
    @DisplayName("批量距离按请求下标对齐，且不调用供应商")
    void distanceIsLocal() {
        DistanceRequest request = new DistanceRequest();
        request.setOriginLng(LNG);
        request.setOriginLat(LAT);
        request.setCoordType("WGS84");
        request.setTargets(List.of(point(LNG + 0.01, LAT), point(LNG, LAT + 0.01)));
        List<DistanceVO> distances = service.distance(request);
        assertEquals(2, distances.size());
        assertEquals(0, distances.get(0).getIndex());
        assertTrue(distances.get(0).getDistanceMeters() > 800 && distances.get(0).getDistanceMeters() < 900,
                "0.01 度经度在北京约 855 米，实际 " + distances.get(0).getDistanceMeters());
    }

    @Test
    @DisplayName("批量距离超上限时拒绝，避免单请求打满 CPU")
    void distanceBatchLimited() {
        properties.getLocation().setDistanceMaxBatch(1);
        DistanceRequest request = new DistanceRequest();
        request.setOriginLng(LNG);
        request.setOriginLat(LAT);
        request.setTargets(List.of(point(LNG, LAT), point(LNG, LAT)));
        BizException ex = assertThrows(BizException.class, () -> service.distance(request));
        assertEquals(MapErrorCode.DISTANCE_BATCH_TOO_LARGE, ex.getCode());
    }

    @Test
    @DisplayName("健康检查汇总已注册供应商")
    void healthListsProviders() {
        List<ProviderHealthVO> list = service.health();
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(item -> "tencent".equals(item.getName())));
    }

    /**
     * 构造一个已配置的供应商桩
     *
     * @param name 供应商名
     * @return 供应商桩
     */
    private MapProvider provider(String name) {
        MapProvider provider = mock(MapProvider.class);
        when(provider.name()).thenReturn(name);
        when(provider.configured()).thenReturn(true);
        when(provider.supports(any())).thenReturn(true);
        return provider;
    }

    /**
     * 构造地址解析请求
     *
     * @return 请求
     */
    private GeocodeRequest request() {
        GeocodeRequest request = new GeocodeRequest();
        request.setAddress("北京市东城区东长安街");
        request.setCity("北京");
        return request;
    }

    /**
     * 构造坐标点请求
     *
     * @param lng 经度
     * @param lat 纬度
     * @return 点请求
     */
    private PointRequest point(double lng, double lat) {
        PointRequest point = new PointRequest();
        point.setLng(lng);
        point.setLat(lat);
        return point;
    }

    /**
     * 构造供应商解析结果（WGS84）
     *
     * @param provider 供应商名
     * @return 解析结果
     */
    private GeocodeResult result(String provider) {
        return new GeocodeResult(new GeoPoint(LNG, LAT, CoordType.WGS84), "北京市东城区东长安街",
                "北京市", "北京市", "东城区", "110101", "门址", provider);
    }
}
