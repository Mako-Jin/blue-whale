package com.bluewhale.map.common;

/**
 * 地图服务业务错误码。
 *
 * <p>段位约定：{@code 44xxx} 归 {@code whale-map}，与 LMS 的 42xxx、党政工会的 43xxx 并列不交叉。
 * 44000–44009 为坐标与供应商选择，44010–44019 为地址解析与 POI，44020–44029 为打点与附近查询。</p>
 *
 * @author BlueWhale
 */
public final class MapErrorCode {

    /** 无可用地图供应商（未配置 Key 或全部超配额） */
    public static final int PROVIDER_UNAVAILABLE = 44000;

    /** 供应商调用失败（网络错误或返回体不合契约） */
    public static final int PROVIDER_FAILED = 44001;

    /** 坐标系非法或缺失 */
    public static final int COORD_INVALID = 44002;

    /** 坐标点不在服务范围内（境外坐标不做偏移换算） */
    public static final int COORD_OUT_OF_CHINA = 44003;

    /** 供应商日配额已用尽 */
    public static final int QUOTA_EXCEEDED = 44004;

    /** 地址解析无结果 */
    public static final int GEOCODE_EMPTY = 44010;

    /** 逆地理无结果 */
    public static final int REGEO_EMPTY = 44011;

    /** 路径规划无结果（起终点过近或不可达） */
    public static final int ROUTE_EMPTY = 44012;

    /** 地点检索无结果 */
    public static final int POI_EMPTY = 44013;

    /** 业务对象标识非法（类型或 ID 为空） */
    public static final int BIZ_KEY_INVALID = 44020;

    /** 打点缺少定位信息（地址与坐标都未提供） */
    public static final int LOCATION_SOURCE_MISSING = 44021;

    /** 附近查询半径超出服务上限 */
    public static final int NEARBY_RADIUS_TOO_LARGE = 44022;

    /** 批量距离点数超过上限 */
    public static final int DISTANCE_BATCH_TOO_LARGE = 44023;

    /**
     * 私有构造器：常量类，禁止实例化
     */
    private MapErrorCode() {
    }
}
