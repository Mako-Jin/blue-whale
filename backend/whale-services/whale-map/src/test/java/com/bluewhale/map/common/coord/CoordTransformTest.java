package com.bluewhale.map.common.coord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 坐标系转换测试。
 *
 * <p>断言口径：不锁死到小数点后 6 位（不同实现版本的常数略有差异），
 * 而是锁「合理的偏移量」与「可逆」——这两个才是业务真正依赖的性质。</p>
 *
 * @author BlueWhale
 */
class CoordTransformTest {

    /** 天安门 WGS84 坐标 */
    private static final GeoPoint TIANANMEN_WGS84 = new GeoPoint(116.397428, 39.90923, CoordType.WGS84);

    @Test
    @DisplayName("WGS84 转 GCJ-02：国内点偏移量在度级合理区间（北京约 0.006°/0.002°）")
    void wgs84ToGcj02ShiftsInChina() {
        GeoPoint gcj = CoordTransform.wgs84ToGcj02(TIANANMEN_WGS84);
        assertEquals(CoordType.GCJ02, gcj.type());
        // 注意：不能用 GeoDistance 度量偏移——它会先把两点统一到同一坐标系，
        // 于是「加偏前后」被算成零距离。这里直接看经纬度的度级差值。
        double deltaLng = gcj.lng() - TIANANMEN_WGS84.lng();
        double deltaLat = gcj.lat() - TIANANMEN_WGS84.lat();
        assertTrue(deltaLng > 0.003 && deltaLng < 0.009, "经度偏移异常：" + deltaLng);
        assertTrue(deltaLat > 0.001 && deltaLat < 0.006, "纬度偏移异常：" + deltaLat);
    }

    @Test
    @DisplayName("GCJ-02 反算 WGS84：往返误差小于 1 厘米")
    void inverseIsAccurate() {
        GeoPoint gcj = CoordTransform.wgs84ToGcj02(TIANANMEN_WGS84);
        GeoPoint back = CoordTransform.gcj02ToWgs84(gcj);
        assertEquals(TIANANMEN_WGS84.lng(), back.lng(), 1.0E-7);
        assertEquals(TIANANMEN_WGS84.lat(), back.lat(), 1.0E-7);
    }

    @Test
    @DisplayName("境外坐标不做偏移")
    void overseasNotShifted() {
        GeoPoint tokyo = new GeoPoint(139.767125, 35.681236, CoordType.WGS84);
        GeoPoint gcj = CoordTransform.wgs84ToGcj02(tokyo);
        assertEquals(tokyo.lng(), gcj.lng(), 1.0E-9);
        assertEquals(tokyo.lat(), gcj.lat(), 1.0E-9);
        assertEquals(CoordType.GCJ02, gcj.type());
    }

    @Test
    @DisplayName("任意坐标系互转：经 GCJ-02 中转后可回到原点")
    void convertRoundTrip() {
        GeoPoint bd = CoordTransform.convert(TIANANMEN_WGS84, CoordType.BD09);
        assertEquals(CoordType.BD09, bd.type());
        GeoPoint back = CoordTransform.convert(bd, CoordType.WGS84);
        assertEquals(TIANANMEN_WGS84.lng(), back.lng(), 1.0E-6);
        assertEquals(TIANANMEN_WGS84.lat(), back.lat(), 1.0E-6);
    }

    @Test
    @DisplayName("同坐标系直接返回，不做无谓转换")
    void sameTypeReturnsSelf() {
        assertTrue(TIANANMEN_WGS84 == CoordTransform.convert(TIANANMEN_WGS84, CoordType.WGS84));
    }

    @Test
    @DisplayName("非法坐标在构造时即失败")
    void invalidPointRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(200, 39, CoordType.WGS84));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(116, 91, CoordType.WGS84));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(116, 39, null));
    }

    @Test
    @DisplayName("坐标系名宽松解析")
    void parseCoordType() {
        assertEquals(CoordType.GCJ02, CoordType.parse("gcj-02"));
        assertEquals(CoordType.WGS84, CoordType.parse(" WGS_84 "));
        assertEquals(null, CoordType.parse("mars"));
    }
}
