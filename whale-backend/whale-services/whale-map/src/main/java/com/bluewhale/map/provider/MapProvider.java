package com.bluewhale.map.provider;

/**
 * 地图供应商统一契约。
 *
 * <p><b>坐标系约定</b>：入参与返回值一律 WGS84。各家的原生坐标（腾讯/高德 GCJ-02、
 * 百度 BD-09）由适配器自己在边界换算。这条约定让上层彻底不用关心「这次调用是谁算的」——
 * 一旦允许适配器透传原生坐标，缓存就会把不同坐标系的点混在一张表里。</p>
 *
 * <p><b>失败语义</b>：无法完成时抛 {@code BizException}，不返回 null。
 * 上层靠异常决定是否降级；返回 null 会让「供应商不可用」与「查无此地」混为一谈。</p>
 *
 * @author BlueWhale
 */
public interface MapProvider {

    /**
     * 供应商名（与配置键一致）
     *
     * @return 供应商名
     */
    String name();

    /**
     * 是否支持某能力
     *
     * @param capability 能力
     * @return 支持返回 {@code true}
     */
    boolean supports(MapCapability capability);

    /**
     * 是否已配置可用（Key 与基址齐全）
     *
     * @return 可用返回 {@code true}
     */
    boolean configured();

    /**
     * 地址解析
     *
     * @param query 查询条件
     * @return 解析结果（WGS84）
     */
    GeocodeResult geocode(GeocodeQuery query);

    /**
     * 逆地理
     *
     * @param query 查询条件
     * @return 逆地理结果
     */
    RegeoResult regeo(RegeoQuery query);

    /**
     * 地点检索
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PoiPage searchPoi(PoiQuery query);

    /**
     * 路径规划
     *
     * @param query 查询条件
     * @return 路径结果
     */
    RouteResult route(RouteQuery query);
}
