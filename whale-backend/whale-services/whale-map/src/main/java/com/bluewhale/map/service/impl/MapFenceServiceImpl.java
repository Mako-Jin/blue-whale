package com.bluewhale.map.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.bluewhale.map.common.geo.FenceHit;
import com.bluewhale.map.common.geo.GeoFence;
import com.bluewhale.map.common.geo.LocalProjection;
import com.bluewhale.map.dto.FenceCheckRequest;
import com.bluewhale.map.dto.FencePointRequest;
import com.bluewhale.map.dto.FenceQuery;
import com.bluewhale.map.dto.FenceSaveRequest;
import com.bluewhale.map.entity.MapFence;
import com.bluewhale.map.mapper.MapFenceMapper;
import com.bluewhale.map.service.MapFenceService;
import com.bluewhale.map.support.CoordContext;
import com.bluewhale.map.vo.FenceHitVO;
import com.bluewhale.map.vo.FenceVO;
import com.bluewhale.map.vo.GeoPointVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 围栏服务实现。
 *
 * <p><b>坐标口径</b>：入口一律按请求声明的 {@code coordType}（缺省展示坐标系）解释，
 * 转成存储坐标系（WGS84）后落库与判定；出口一律转回展示坐标系（GCJ-02），
 * 供前端叠加到 GCJ-02 底图。围栏顶点与点位同源，判定时不再需要问「画在哪个底图上」。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MapFenceServiceImpl implements MapFenceService {

    /** 圆形围栏类型 */
    private static final String TYPE_CIRCLE = "CIRCLE";

    /** 多边形围栏类型 */
    private static final String TYPE_POLYGON = "POLYGON";

    /** 多边形最少顶点数 */
    private static final int MIN_VERTICES = 3;

    /** 多边形最多顶点数：再多说明有人在拿行政区边界当打卡范围，判定会变慢且没有意义 */
    private static final int MAX_VERTICES = 200;

    /** 单租户围栏数量上限 */
    private static final int MAX_FENCES = 500;

    /** 外接矩形余量：投影换算有微小误差，bbox 卡得太紧会让边缘点被粗筛掉 */
    private static final double BBOX_MARGIN = 1.05;

    /** 1 纬度对应的米数 */
    private static final double METERS_PER_LAT_DEGREE = 111319.4907932735;

    /** 经度跨度超过该值即视为跨越 180 度经线 */
    private static final double HALF_WORLD_LNG_SPAN = 180.0;

    /** 全球经度边界 */
    private static final double WORLD_MIN_LNG = -180.0;

    /** 全球经度边界 */
    private static final double WORLD_MAX_LNG = 180.0;

    /** 坐标保留位数（7 位约 1 厘米） */
    private static final double ROUND_SCALE = 1.0E7;

    /** 围栏 Mapper */
    private final MapFenceMapper fenceMapper;

    /** 坐标口径中心 */
    private final CoordContext coords;

    /** JSON 编解码 */
    private final ObjectMapper objectMapper;

    @Override
    public FenceVO save(FenceSaveRequest request) {
        String type = normalizeType(request.getFenceType());
        MapFence entity;
        if (request.getId() == null) {
            checkQuota();
            entity = new MapFence();
            entity.setDeleted(0);
        } else {
            entity = requireFence(request.getId());
        }
        entity.setName(request.getName().trim());
        entity.setFenceType(type);
        entity.setBizType(blankToNull(request.getBizType()));
        entity.setBizId(blankToNull(request.getBizId()));
        entity.setCoordType(coords.storage().name());
        entity.setEnabled(Integer.valueOf(0).equals(request.getEnabled()) ? 0 : 1);
        entity.setRemark(request.getRemark());
        if (TYPE_CIRCLE.equals(type)) {
            fillCircle(entity, request);
        } else {
            fillPolygon(entity, request);
        }
        if (request.getId() == null) {
            fenceMapper.insert(entity);
        } else {
            fenceMapper.updateById(entity);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<FenceVO> page(FenceQuery query) {
        Page<MapFence> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<MapFence> wrapper = new LambdaQueryWrapper<MapFence>()
                .like(StringUtils.hasText(query.getName()), MapFence::getName, query.getName())
                .eq(StringUtils.hasText(query.getBizType()), MapFence::getBizType, query.getBizType())
                .eq(StringUtils.hasText(query.getBizId()), MapFence::getBizId, query.getBizId())
                .eq(query.getEnabled() != null, MapFence::getEnabled, query.getEnabled())
                .orderByDesc(MapFence::getUpdateTime);
        Page<MapFence> result = fenceMapper.selectPage(page, wrapper);
        return PageResult.of(result.convert(this::toVO));
    }

    @Override
    public FenceVO detail(Long id) {
        return toVO(requireFence(id));
    }

    @Override
    public void remove(Long id) {
        requireFence(id);
        // 逻辑删除：打卡记录、配送单仍要能指回这个围栏的名字
        fenceMapper.deleteById(id);
    }

    @Override
    public FenceHitVO contains(Long id, FenceCheckRequest request) {
        MapFence fence = requireFence(id);
        GeoPoint query = coords.toStorage(request.getLng(), request.getLat(), request.getCoordType());
        return toHitVO(fence, evaluate(fence, query));
    }

    @Override
    public List<FenceHitVO> match(FenceCheckRequest request) {
        GeoPoint query = coords.toStorage(request.getLng(), request.getLat(), request.getCoordType());
        List<MapFence> candidates = fenceMapper.selectCandidates(TenantContext.requireId(),
                blankToNull(request.getBizType()), blankToNull(request.getBizId()),
                BigDecimal.valueOf(query.lng()), BigDecimal.valueOf(query.lat()));
        List<FenceHitVO> hits = new ArrayList<>();
        for (MapFence fence : candidates) {
            FenceHit hit = evaluate(fence, query);
            if (hit.inside()) {
                hits.add(toHitVO(fence, hit));
            }
        }
        // 越靠围栏中心越前：打卡场景里「最稳的那一圈」应该排在第一条
        hits.sort(Comparator.comparingDouble(FenceHitVO::getDistanceMeters).reversed());
        return hits;
    }

    /**
     * 校验并填入圆形围栏参数
     *
     * @param entity  实体
     * @param request 请求
     */
    private void fillCircle(MapFence entity, FenceSaveRequest request) {
        if (request.getCenterLng() == null || request.getCenterLat() == null) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.circle-center-required",
                    "圆形围栏需要圆心坐标");
        }
        Double radius = request.getRadiusMeters();
        if (radius == null || radius <= 0) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.circle-radius-invalid",
                    "圆形围栏半径必须大于 0 米");
        }
        GeoPoint center = coords.toStorage(request.getCenterLng(), request.getCenterLat(), request.getCoordType());
        entity.setCenterLng(toDecimal(center.lng()));
        entity.setCenterLat(toDecimal(center.lat()));
        entity.setRadiusMeters(BigDecimal.valueOf(radius).setScale(2, RoundingMode.HALF_UP));
        entity.setPoints(null);
        double deltaLat = radius * BBOX_MARGIN / METERS_PER_LAT_DEGREE;
        double deltaLng = radius * BBOX_MARGIN / LocalProjection.metersPerLngDegree(center.lat());
        applyBbox(entity, center.lng() - deltaLng, center.lng() + deltaLng,
                center.lat() - deltaLat, center.lat() + deltaLat);
    }

    /**
     * 校验并填入多边形围栏参数
     *
     * @param entity  实体
     * @param request 请求
     */
    private void fillPolygon(MapFence entity, FenceSaveRequest request) {
        List<FencePointRequest> points = request.getPoints();
        if (points == null || points.size() < MIN_VERTICES) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.polygon-min-vertices",
                    "多边形围栏至少需要 " + MIN_VERTICES + " 个顶点", MIN_VERTICES);
        }
        if (points.size() > MAX_VERTICES) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.polygon-max-vertices",
                    "多边形围栏顶点不能超过 " + MAX_VERTICES + " 个", MAX_VERTICES);
        }
        List<GeoPoint> storagePoints = new ArrayList<>(points.size());
        for (FencePointRequest point : points) {
            storagePoints.add(coords.toStorage(point.getLng(), point.getLat(), request.getCoordType()));
        }
        dropClosingVertex(storagePoints);
        if (storagePoints.size() < MIN_VERTICES) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.polygon-vertices-after-dedup",
                    "多边形顶点去掉重复闭合点后不足 " + MIN_VERTICES + " 个", MIN_VERTICES);
        }
        entity.setPoints(writePoints(storagePoints));
        entity.setCenterLng(null);
        entity.setCenterLat(null);
        entity.setRadiusMeters(null);
        applyPolygonBbox(entity, storagePoints);
    }

    /**
     * 若首尾顶点重合则去掉末点
     *
     * @param points 顶点列表（就地修改）
     */
    private void dropClosingVertex(List<GeoPoint> points) {
        int last = points.size() - 1;
        GeoPoint first = points.get(0);
        GeoPoint tail = points.get(last);
        if (Math.abs(first.lng() - tail.lng()) < 1.0E-9 && Math.abs(first.lat() - tail.lat()) < 1.0E-9) {
            points.remove(last);
        }
    }

    /**
     * 计算多边形外接矩形
     *
     * @param entity 实体
     * @param points 顶点（存储坐标系）
     */
    private void applyPolygonBbox(MapFence entity, List<GeoPoint> points) {
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        for (GeoPoint point : points) {
            minLng = Math.min(minLng, point.lng());
            maxLng = Math.max(maxLng, point.lng());
            minLat = Math.min(minLat, point.lat());
            maxLat = Math.max(maxLat, point.lat());
        }
        applyBbox(entity, minLng, maxLng, minLat, maxLat);
    }

    /**
     * 写入外接矩形，跨 180 度经线时放成全球经度
     *
     * <p>跨线围栏的顶点经度一半在 179.x、一半在 -179.x，直接取 min/max 会得到一个
     * 「几乎覆盖全球」的矩形，看起来能用；但若按「最小/最大」写成 179→-179，
     * SQL 的 {@code min_lng <= ? AND max_lng >= ?} 永远为假，围栏会被静默跳过。
     * 所以这里显式识别跨线并写全球经度——粗筛宽松是可接受的，漏判不是。</p>
     *
     * @param entity 实体
     * @param minLng 最小经度
     * @param maxLng 最大经度
     * @param minLat 最小纬度
     * @param maxLat 最大纬度
     */
    private void applyBbox(MapFence entity, double minLng, double maxLng, double minLat, double maxLat) {
        boolean crosses = maxLng - minLng > HALF_WORLD_LNG_SPAN
                || minLng < WORLD_MIN_LNG || maxLng > WORLD_MAX_LNG;
        if (crosses) {
            entity.setMinLng(BigDecimal.valueOf(WORLD_MIN_LNG));
            entity.setMaxLng(BigDecimal.valueOf(WORLD_MAX_LNG));
        } else {
            entity.setMinLng(toDecimal(minLng));
            entity.setMaxLng(toDecimal(maxLng));
        }
        entity.setMinLat(toDecimal(Math.max(-90.0, minLat)));
        entity.setMaxLat(toDecimal(Math.min(90.0, maxLat)));
    }

    /**
     * 判定某点与某个围栏的关系
     *
     * @param fence 围栏（存储坐标系）
     * @param query 查询点（存储坐标系）
     * @return 判定结果
     */
    private FenceHit evaluate(MapFence fence, GeoPoint query) {
        if (TYPE_CIRCLE.equals(fence.getFenceType())) {
            GeoPoint center = new GeoPoint(fence.getCenterLng().doubleValue(),
                    fence.getCenterLat().doubleValue(), storageType(fence));
            return GeoFence.circle(center, fence.getRadiusMeters().doubleValue(), query);
        }
        return GeoFence.polygon(readPoints(fence), query);
    }

    /**
     * 围栏自身的坐标系（兜底为服务存储坐标系）
     *
     * @param fence 围栏
     * @return 坐标系
     */
    private CoordType storageType(MapFence fence) {
        CoordType type = CoordType.parse(fence.getCoordType());
        return type == null ? coords.storage() : type;
    }

    /**
     * 解析多边形顶点
     *
     * @param fence 围栏
     * @return 顶点（存储坐标系）
     */
    private List<GeoPoint> readPoints(MapFence fence) {
        if (!StringUtils.hasText(fence.getPoints())) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.polygon-points-missing",
                    "多边形围栏缺少顶点数据");
        }
        CoordType type = storageType(fence);
        try {
            double[][] raw = objectMapper.readValue(fence.getPoints(), double[][].class);
            List<GeoPoint> points = new ArrayList<>(raw.length);
            for (double[] pair : raw) {
                points.add(new GeoPoint(pair[0], pair[1], type));
            }
            return points;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.points-corrupted",
                    "围栏顶点数据损坏：" + ex.getMessage(), ex.getMessage());
        }
    }

    /**
     * 序列化多边形顶点
     *
     * @param points 顶点
     * @return JSON（{@code [[lng,lat],...]}）
     */
    private String writePoints(List<GeoPoint> points) {
        double[][] raw = new double[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            raw[i][0] = round(points.get(i).lng());
            raw[i][1] = round(points.get(i).lat());
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            throw new BizException(MapErrorCode.FENCE_GEOMETRY_INVALID, "map.fence.points-serialize-failed",
                    "围栏顶点序列化失败");
        }
    }

    /**
     * 转展示视图（坐标转回展示坐标系）
     *
     * @param fence 围栏
     * @return 视图
     */
    private FenceVO toVO(MapFence fence) {
        FenceVO vo = new FenceVO();
        vo.setId(fence.getId());
        vo.setName(fence.getName());
        vo.setFenceType(fence.getFenceType());
        vo.setBizType(fence.getBizType());
        vo.setBizId(fence.getBizId());
        vo.setCoordType(coords.display().name());
        vo.setEnabled(fence.getEnabled());
        vo.setRemark(fence.getRemark());
        vo.setUpdateTime(fence.getUpdateTime());
        if (TYPE_CIRCLE.equals(fence.getFenceType())) {
            GeoPoint center = coords.toDisplay(new GeoPoint(fence.getCenterLng().doubleValue(),
                    fence.getCenterLat().doubleValue(), storageType(fence)));
            vo.setCenterLng(round(center.lng()));
            vo.setCenterLat(round(center.lat()));
            vo.setRadiusMeters(fence.getRadiusMeters().doubleValue());
        } else {
            List<GeoPointVO> displayPoints = new ArrayList<>();
            for (GeoPoint point : readPoints(fence)) {
                GeoPoint shown = coords.toDisplay(point);
                GeoPointVO item = new GeoPointVO();
                item.setLng(round(shown.lng()));
                item.setLat(round(shown.lat()));
                item.setCoordType(coords.display().name());
                displayPoints.add(item);
            }
            vo.setPoints(displayPoints);
        }
        return vo;
    }

    /**
     * 转命中视图
     *
     * @param fence 围栏
     * @param hit   判定结果
     * @return 视图
     */
    private FenceHitVO toHitVO(MapFence fence, FenceHit hit) {
        FenceHitVO vo = new FenceHitVO();
        vo.setId(fence.getId());
        vo.setName(fence.getName());
        vo.setFenceType(fence.getFenceType());
        vo.setBizType(fence.getBizType());
        vo.setBizId(fence.getBizId());
        vo.setInside(hit.inside());
        vo.setDistanceMeters(Math.round(hit.distanceMeters() * 100.0) / 100.0);
        return vo;
    }

    /**
     * 取围栏（不存在或已删除则报错）
     *
     * @param id 围栏ID
     * @return 围栏
     */
    private MapFence requireFence(Long id) {
        if (id == null) {
            throw new BizException(MapErrorCode.FENCE_NOT_FOUND, "map.fence.id-required", "围栏ID不能为空");
        }
        MapFence fence = fenceMapper.selectById(id);
        if (fence == null) {
            throw new BizException(MapErrorCode.FENCE_NOT_FOUND, "map.fence.not-found", "围栏不存在或已删除");
        }
        return fence;
    }

    /**
     * 校验围栏类型
     *
     * @param fenceType 类型文本
     * @return 归一化后的类型
     */
    private String normalizeType(String fenceType) {
        String type = fenceType == null ? "" : fenceType.trim().toUpperCase();
        if (!TYPE_CIRCLE.equals(type) && !TYPE_POLYGON.equals(type)) {
            throw new BizException(MapErrorCode.FENCE_TYPE_INVALID, "map.fence.type-invalid",
                    "围栏类型只支持 CIRCLE 或 POLYGON");
        }
        return type;
    }

    /**
     * 校验租户围栏数量
     */
    private void checkQuota() {
        Long count = fenceMapper.selectCount(null);
        if (count != null && count >= MAX_FENCES) {
            throw new BizException(MapErrorCode.FENCE_LIMIT_EXCEEDED, "map.fence.limit-exceeded",
                    "单租户围栏数量已达上限 " + MAX_FENCES + " 个", MAX_FENCES);
        }
    }

    /**
     * 空串归一为 null（避免 biz_type 存空串导致「通用围栏」判定失效）
     *
     * @param text 文本
     * @return 去空后的值
     */
    private String blankToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    /**
     * 转 DECIMAL 精度
     *
     * @param value 坐标值
     * @return 保留 7 位的值
     */
    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }

    /**
     * 坐标四舍五入到 7 位
     *
     * @param value 坐标值
     * @return 结果
     */
    private double round(double value) {
        return Math.round(value * ROUND_SCALE) / ROUND_SCALE;
    }
}
