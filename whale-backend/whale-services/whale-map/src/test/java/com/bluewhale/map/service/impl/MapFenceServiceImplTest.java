package com.bluewhale.map.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.dto.FenceCheckRequest;
import com.bluewhale.map.dto.FencePointRequest;
import com.bluewhale.map.dto.FenceQuery;
import com.bluewhale.map.dto.FenceSaveRequest;
import com.bluewhale.map.entity.MapFence;
import com.bluewhale.map.mapper.MapFenceMapper;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.vo.FenceHitVO;
import com.bluewhale.map.vo.FenceVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 围栏服务测试。
 *
 * <p>重点：坐标出入口各转一次（GCJ-02 进、WGS84 存、GCJ-02 出）、
 * bbox 跨经线时要放全球（否则 SQL 粗筛会把围栏静默漏掉）、
 * 闭合点要去掉（零长度边会让射线法误判）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapFenceServiceImplTest {

    /** 测试租户ID */
    private static final long TENANT_ID = 1L;

    /** 天安门 GCJ-02 经度 */
    private static final double GCJ_LNG = 116.404;

    /** 天安门 GCJ-02 纬度 */
    private static final double GCJ_LAT = 39.915;

    /** 1 纬度对应的米数 */
    private static final double METERS_PER_LAT_DEGREE = 111319.4907932735;

    /** 围栏 Mapper */
    @Mock
    private MapFenceMapper fenceMapper;

    /** 被测服务 */
    private MapFenceServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MapFence.class);
    }

    @BeforeEach
    void setUp() {
        MapProperties properties = new MapProperties();
        CoordContext coords = new CoordContext(properties);
        service = new MapFenceServiceImpl(fenceMapper, coords, new ObjectMapper());
        TenantContext.set(TENANT_ID);
        when(fenceMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("圆形围栏：GCJ-02 进、WGS84 落库、返回仍为 GCJ-02")
    void saveCircle() {
        FenceVO vo = service.save(circleRequest(GCJ_LNG, GCJ_LAT, 200.0));
        ArgumentCaptor<MapFence> captor = ArgumentCaptor.forClass(MapFence.class);
        verify(fenceMapper).insert(captor.capture());
        MapFence saved = captor.getValue();
        assertEquals("CIRCLE", saved.getFenceType());
        assertEquals("WGS84", saved.getCoordType());
        assertTrue(saved.getCenterLng().doubleValue() < GCJ_LNG, "WGS84 经度应小于同位置的 GCJ-02");
        assertTrue(saved.getCenterLat().doubleValue() < GCJ_LAT, "WGS84 纬度应小于同位置的 GCJ-02");
        assertNull(saved.getPoints());
        // bbox 要把半径外扩一点，否则边缘点会被粗筛掉
        assertTrue(saved.getMaxLng().doubleValue() > saved.getCenterLng().doubleValue());
        assertEquals("GCJ02", vo.getCoordType());
        assertEquals(GCJ_LNG, vo.getCenterLng().doubleValue(), 1.0E-5);
    }

    @Test
    @DisplayName("多边形围栏：顶点落 WGS84，GeoJSON 式闭合点被去掉，bbox 覆盖全部顶点")
    void savePolygon() {
        List<FencePointRequest> points = new ArrayList<>(List.of(
                point(GCJ_LNG, GCJ_LAT),
                point(GCJ_LNG + 0.002, GCJ_LAT),
                point(GCJ_LNG + 0.002, GCJ_LAT + 0.002),
                point(GCJ_LNG, GCJ_LAT + 0.002)));
        points.add(point(GCJ_LNG, GCJ_LAT));

        FenceVO vo = service.save(polygonRequest(points));
        ArgumentCaptor<MapFence> captor = ArgumentCaptor.forClass(MapFence.class);
        verify(fenceMapper).insert(captor.capture());
        MapFence saved = captor.getValue();
        assertEquals("POLYGON", saved.getFenceType());
        assertNotNull(saved.getPoints());
        // 4 个真实顶点 + 1 个闭合点 → 去掉闭合点
        assertEquals(4, countPoints(saved.getPoints()));
        assertEquals(4, vo.getPoints().size());
        assertTrue(saved.getMinLng().doubleValue() < saved.getMaxLng().doubleValue());
        assertNull(saved.getRadiusMeters());
    }

    @Test
    @DisplayName("跨 180 度经线的多边形：bbox 放成全球经度，不能写成 179 → -179")
    void savePolygonCrossingAntimeridian() {
        List<FencePointRequest> points = List.of(
                point(179.9991, -0.0009),
                point(-179.9991, -0.0009),
                point(-179.9991, 0.0009),
                point(179.9991, 0.0009));
        FenceSaveRequest request = polygonRequest(points);
        request.setCoordType("WGS84");
        service.save(request);
        ArgumentCaptor<MapFence> captor = ArgumentCaptor.forClass(MapFence.class);
        verify(fenceMapper).insert(captor.capture());
        MapFence saved = captor.getValue();
        assertEquals(-180.0, saved.getMinLng().doubleValue(), 1.0E-9);
        assertEquals(180.0, saved.getMaxLng().doubleValue(), 1.0E-9);
    }

    @Test
    @DisplayName("顶点不足、类型非法、半径非法都拒绝")
    void invalidRequests() {
        FenceSaveRequest tooFew = polygonRequest(List.of(point(GCJ_LNG, GCJ_LAT), point(GCJ_LNG, GCJ_LAT)));
        assertEquals(MapErrorCode.FENCE_GEOMETRY_INVALID, errorCode(() -> service.save(tooFew)));

        FenceSaveRequest badType = circleRequest(GCJ_LNG, GCJ_LAT, 100.0);
        badType.setFenceType("TRIANGLE");
        assertEquals(MapErrorCode.FENCE_TYPE_INVALID, errorCode(() -> service.save(badType)));

        assertEquals(MapErrorCode.FENCE_GEOMETRY_INVALID,
                errorCode(() -> service.save(circleRequest(GCJ_LNG, GCJ_LAT, 0.0))));
    }

    @Test
    @DisplayName("围栏数量达到上限后拒绝新建")
    void quotaExceeded() {
        when(fenceMapper.selectCount(any())).thenReturn(500L);
        assertEquals(MapErrorCode.FENCE_LIMIT_EXCEEDED,
                errorCode(() -> service.save(circleRequest(GCJ_LNG, GCJ_LAT, 100.0))));
    }

    @Test
    @DisplayName("contains：围栏内点 inside=true，距离为到边界的正数")
    void containsInside() {
        when(fenceMapper.selectById(1L)).thenReturn(storedCircle(100.0));
        FenceCheckRequest request = checkRequest(116.404, 39.915, "GCJ02");
        FenceHitVO hit = service.contains(1L, request);
        assertTrue(hit.isInside());
        // 站在圆心，到边界正好等于半径
        assertEquals(100.0, hit.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("contains：围栏外点 inside=false，距离为负")
    void containsOutside() {
        when(fenceMapper.selectById(1L)).thenReturn(storedCircle(100.0));
        // 往北 200 米，明显在半径之外
        FenceCheckRequest request = checkRequest(116.404, 39.915 + 200.0 / METERS_PER_LAT_DEGREE, "GCJ02");
        FenceHitVO hit = service.contains(1L, request);
        assertFalse(hit.isInside());
        assertTrue(hit.getDistanceMeters() < 0);
    }

    @Test
    @DisplayName("match：只返回命中的围栏，并按距边界从远到近排序")
    void matchFiltersAndSorts() {
        MapFence wide = storedCircle(500.0);
        MapFence narrow = storedCircle(100.0);
        narrow.setId(2L);
        narrow.setName("小范围");
        when(fenceMapper.selectCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(narrow, wide));
        List<FenceHitVO> hits = service.match(checkRequest(116.404, 39.915, "GCJ02"));
        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).getId().longValue(), "距边界更远的（500 米）应排在 100 米的前面");
        assertTrue(hits.get(0).getDistanceMeters() > hits.get(1).getDistanceMeters());
    }

    @Test
    @DisplayName("match：不在任何围栏内返回空列表（不是报错）")
    void matchEmpty() {
        when(fenceMapper.selectCandidates(any(), any(), any(), any(), any())).thenReturn(List.of());
        assertTrue(service.match(checkRequest(116.404, 39.915, "GCJ02")).isEmpty());
    }

    @Test
    @DisplayName("详情：围栏不存在抛 FENCE_NOT_FOUND")
    void detailNotFound() {
        when(fenceMapper.selectById(9L)).thenReturn(null);
        assertEquals(MapErrorCode.FENCE_NOT_FOUND, errorCode(() -> service.detail(9L)));
    }

    @Test
    @DisplayName("分页返回展示坐标系的顶点")
    void pageConvertsPoints() {
        MapFence stored = storedCircle(100.0);
        Page<MapFence> page = new Page<>(1, 20);
        page.setRecords(List.of(stored));
        page.setTotal(1);
        when(fenceMapper.selectPage(any(), any())).thenReturn(page);
        PageResult<FenceVO> result = service.page(new FenceQuery());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("GCJ02", result.getRecords().get(0).getCoordType());
    }

    @Test
    @DisplayName("删除走逻辑删除，打卡记录仍能指回围栏名字")
    void removeUsesLogicalDelete() {
        when(fenceMapper.selectById(1L)).thenReturn(storedCircle(100.0));
        service.remove(1L);
        verify(fenceMapper).deleteById(1L);
    }

    /**
     * 构造落库形态的圆（WGS84 圆心）
     *
     * @param radius 半径（米）
     * @return 实体
     */
    private MapFence storedCircle(double radius) {
        MapFence fence = new MapFence();
        fence.setId(1L);
        fence.setName("总部");
        fence.setFenceType("CIRCLE");
        fence.setCoordType("WGS84");
        GeoPoint center = CoordTransform.convert(new GeoPoint(GCJ_LNG, GCJ_LAT, CoordType.GCJ02), CoordType.WGS84);
        fence.setCenterLng(BigDecimal.valueOf(center.lng()));
        fence.setCenterLat(BigDecimal.valueOf(center.lat()));
        fence.setRadiusMeters(BigDecimal.valueOf(radius));
        fence.setEnabled(1);
        fence.setMinLng(BigDecimal.valueOf(116.39));
        fence.setMaxLng(BigDecimal.valueOf(116.41));
        fence.setMinLat(BigDecimal.valueOf(39.90));
        fence.setMaxLat(BigDecimal.valueOf(39.92));
        return fence;
    }

    /**
     * 构造圆形保存请求
     *
     * @param lng    圆心经度
     * @param lat    圆心纬度
     * @param radius 半径（米）
     * @return 请求
     */
    private FenceSaveRequest circleRequest(double lng, double lat, double radius) {
        FenceSaveRequest request = new FenceSaveRequest();
        request.setName("总部");
        request.setFenceType("CIRCLE");
        request.setCenterLng(lng);
        request.setCenterLat(lat);
        request.setRadiusMeters(radius);
        request.setCoordType("GCJ02");
        return request;
    }

    /**
     * 构造多边形保存请求
     *
     * @param points 顶点
     * @return 请求
     */
    private FenceSaveRequest polygonRequest(List<FencePointRequest> points) {
        FenceSaveRequest request = new FenceSaveRequest();
        request.setName("园区");
        request.setFenceType("POLYGON");
        request.setPoints(points);
        request.setCoordType("GCJ02");
        return request;
    }

    /**
     * 构造顶点
     *
     * @param lng 经度
     * @param lat 纬度
     * @return 顶点
     */
    private FencePointRequest point(double lng, double lat) {
        FencePointRequest point = new FencePointRequest();
        point.setLng(lng);
        point.setLat(lat);
        return point;
    }

    /**
     * 构造判定请求
     *
     * @param lng       经度
     * @param lat       纬度
     * @param coordType 坐标系
     * @return 请求
     */
    private FenceCheckRequest checkRequest(double lng, double lat, String coordType) {
        FenceCheckRequest request = new FenceCheckRequest();
        request.setLng(lng);
        request.setLat(lat);
        request.setCoordType(coordType);
        return request;
    }

    /**
     * 统计 JSON 顶点个数
     *
     * @param json 顶点 JSON
     * @return 个数
     */
    private int countPoints(String json) {
        try {
            return new ObjectMapper().readValue(json, double[][].class).length;
        } catch (Exception ex) {
            return -1;
        }
    }

    /**
     * 断言并取出业务错误码
     *
     * @param action 待执行动作
     * @return 错误码
     */
    private int errorCode(Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run);
        return ex.getCode();
    }
}
