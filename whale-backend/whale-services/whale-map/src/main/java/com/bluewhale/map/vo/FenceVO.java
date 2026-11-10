package com.bluewhale.map.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 围栏视图。
 *
 * <p><b>坐标一律是展示坐标系</b>（配置的 {@code displayCoord}，默认 GCJ-02）：
 * 前端要把它画到腾讯/高德底图上，而底图是 GCJ-02。若把库里存的 WGS84 原样返回，
 * 围栏会在底图上偏移 300–500 米——「围栏画错了」这类问题排查起来极费时间，
 * 因为数据本身没错，错的是它离开服务端时没有换口径。</p>
 *
 * @author BlueWhale
 */
@Data
public class FenceVO {

    /** 围栏ID */
    private Long id;

    /** 围栏名称 */
    private String name;

    /** 围栏类型：CIRCLE / POLYGON */
    private String fenceType;

    /** 绑定业务类型 */
    private String bizType;

    /** 绑定业务对象ID */
    private String bizId;

    /** 圆心经度（展示坐标系，仅 CIRCLE） */
    private Double centerLng;

    /** 圆心纬度（展示坐标系，仅 CIRCLE） */
    private Double centerLat;

    /** 半径（米，仅 CIRCLE） */
    private Double radiusMeters;

    /** 多边形顶点（展示坐标系，仅 POLYGON） */
    private List<GeoPointVO> points;

    /** 坐标系（固定的展示坐标系） */
    private String coordType;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 最近更新时间 */
    private LocalDateTime updateTime;
}
