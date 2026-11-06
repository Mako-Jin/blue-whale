package com.bluewhale.map.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.dto.LocationSaveRequest;
import com.bluewhale.map.entity.MapLocation;
import com.bluewhale.map.mapper.MapLocationMapper;
import com.bluewhale.map.service.MapGeoService;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.vo.GeocodeVO;
import com.bluewhale.map.vo.MapLocationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务打点服务测试。
 *
 * <p>重点：打点是覆盖语义（同一对象不产生第二行）、只给地址时要先解析、
 * 附近查询必须做半径精筛（geohash 只保证在格子里）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapLocationServiceImplTest {

    /** 测试租户ID */
    private static final long TENANT_ID = 1L;

    /** 业务类型 */
    private static final String BIZ_TYPE = "CRM_CONTACT";

    /** 业务对象ID */
    private static final String BIZ_ID = "10086";

    /** 打点 Mapper */
    @Mock
    private MapLocationMapper locationMapper;

    /** 地图解析服务 */
    @Mock
    private MapGeoService geoService;

    /** 服务配置 */
    private MapProperties properties;

    /** 被测服务 */
    private MapLocationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MapLocation.class);
    }

    @BeforeEach
    void setUp() {
        properties = new MapProperties();
        service = new MapLocationServiceImpl(locationMapper, geoService, new CoordContext(properties), properties);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("给坐标时直接落库，且不调用解析服务")
    void saveWithCoordinate() {
        when(locationMapper.selectOne(any())).thenReturn(null);
        MapLocationVO vo = service.save(BIZ_TYPE, BIZ_ID, requestWithPoint());
        assertNotNull(vo);
        ArgumentCaptor<MapLocation> captor = ArgumentCaptor.forClass(MapLocation.class);
        verify(locationMapper).insert(captor.capture());
        MapLocation saved = captor.getValue();
        assertEquals("MANUAL", saved.getSource());
        assertEquals(9, saved.getGeohash().length());
        assertEquals("WGS84", saved.getCoordType());
        verify(geoService, never()).geocode(any());
    }

    @Test
    @DisplayName("只给地址时先解析再落库，并把供应商与行政区回填")
    void saveWithAddress() {
        when(locationMapper.selectOne(any())).thenReturn(null);
        when(geoService.geocode(any())).thenReturn(geocodeResult());
        LocationSaveRequest request = new LocationSaveRequest();
        request.setAddress("北京市东城区东长安街");
        MapLocationVO vo = service.save(BIZ_TYPE, BIZ_ID, request);
        assertEquals("GEOCODE", vo.getSource());
        assertEquals("tencent", vo.getProvider());
        assertEquals("110101", vo.getAdcode());
    }

    @Test
    @DisplayName("既无坐标也无地址时拒绝，不写空记录")
    void saveWithoutSourceRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> service.save(BIZ_TYPE, BIZ_ID, new LocationSaveRequest()));
        assertEquals(MapErrorCode.LOCATION_SOURCE_MISSING, ex.getCode());
        verify(locationMapper, never()).insert(any(MapLocation.class));
    }

    @Test
    @DisplayName("重复打点更新原行，不新增")
    void saveIsUpsert() {
        when(locationMapper.selectOne(any())).thenReturn(existingRow());
        service.save(BIZ_TYPE, BIZ_ID, requestWithPoint());
        verify(locationMapper).updateById(any(MapLocation.class));
        verify(locationMapper, never()).insert(any(MapLocation.class));
    }

    @Test
    @DisplayName("附近查询做半径精筛并按距离升序")
    void nearbyFiltersByRadius() {
        when(locationMapper.selectList(any())).thenReturn(List.of(row(116.404, 39.915), row(116.55, 39.915)));
        List<MapLocationVO> hits = service.nearby(BIZ_TYPE, 116.404, 39.915, "GCJ02", 500, 10);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getDistanceMeters() < 5, "中心点距离应接近 0");
    }

    @Test
    @DisplayName("附近查询半径超上限时拒绝")
    void nearbyRadiusLimited() {
        properties.getLocation().setNearbyMaxRadiusMeters(1000);
        BizException ex = assertThrows(BizException.class,
                () -> service.nearby(BIZ_TYPE, 116.404, 39.915, "GCJ02", 5000, 10));
        assertEquals(MapErrorCode.NEARBY_RADIUS_TOO_LARGE, ex.getCode());
    }

    @Test
    @DisplayName("未打点时返回空而不是报错")
    void getMissingReturnsNull() {
        when(locationMapper.selectOne(any())).thenReturn(null);
        assertNull(service.get(BIZ_TYPE, BIZ_ID));
    }

    @Test
    @DisplayName("取消打点走物理删除")
    void removeDeletesPhysically() {
        when(locationMapper.selectOne(any())).thenReturn(existingRow());
        service.remove(BIZ_TYPE, BIZ_ID);
        verify(locationMapper).deletePhysical(anyLong(), org.mockito.ArgumentMatchers.eq(TENANT_ID));
    }

    /**
     * 构造带坐标的请求（GCJ-02）
     *
     * @return 请求
     */
    private LocationSaveRequest requestWithPoint() {
        LocationSaveRequest request = new LocationSaveRequest();
        request.setLng(116.404);
        request.setLat(39.915);
        request.setCoordType("GCJ02");
        request.setRemark("总部");
        return request;
    }

    /**
     * 构造解析结果（展示坐标系）
     *
     * @return 解析结果
     */
    private GeocodeVO geocodeResult() {
        GeocodeVO vo = new GeocodeVO();
        vo.setLng(116.404);
        vo.setLat(39.915);
        vo.setCoordType("GCJ02");
        vo.setFormattedAddress("北京市东城区东长安街");
        vo.setProvince("北京市");
        vo.setCity("北京市");
        vo.setDistrict("东城区");
        vo.setAdcode("110101");
        vo.setProvider("tencent");
        return vo;
    }

    /**
     * 构造已存在的打点行
     *
     * @return 实体
     */
    private MapLocation existingRow() {
        MapLocation row = row(116.404, 39.915);
        row.setId(1L);
        row.setBizType(BIZ_TYPE);
        row.setBizId(BIZ_ID);
        return row;
    }

    /**
     * 构造一行打点：入参是展示坐标（GCJ-02），落库按存储坐标（WGS84）
     *
     * @param displayLng 展示经度
     * @param displayLat 展示纬度
     * @return 实体
     */
    private MapLocation row(double displayLng, double displayLat) {
        GeoPoint wgs = CoordTransform.convert(
                new GeoPoint(displayLng, displayLat, CoordType.GCJ02), CoordType.WGS84);
        MapLocation row = new MapLocation();
        row.setBizType(BIZ_TYPE);
        row.setBizId(BIZ_ID);
        row.setLng(BigDecimal.valueOf(wgs.lng()));
        row.setLat(BigDecimal.valueOf(wgs.lat()));
        row.setCoordType("WGS84");
        return row;
    }
}
