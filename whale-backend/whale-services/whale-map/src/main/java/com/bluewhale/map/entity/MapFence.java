package com.bluewhale.map.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 围栏实体，对应表 {@code map_fence}。
 *
 * <p><b>坐标一律是存储坐标系（WGS84）</b>：顶点、圆心都按这个口径落库，与点位同源。
 * 判定时无需再问「这个围栏是 GCJ-02 画的还是 WGS84 的」——问一次就多一个答错的机会。</p>
 *
 * <p><b>与 V1 三张表不同，本表有 {@code deleted}</b>：围栏是配置单据而非状态，
 * 打卡记录、配送单会长期引用它，删掉之后仍需指回一个能解释的名字；误删也要能恢复。
 * 因为允许同名围栏并存，本表不建唯一键，逻辑删除不会引发撞键。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("map_fence")
public class MapFence {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 围栏名称 */
    private String name;

    /** 围栏类型：CIRCLE / POLYGON */
    private String fenceType;

    /** 绑定业务类型（空表示通用围栏） */
    private String bizType;

    /** 绑定业务对象ID */
    private String bizId;

    /** 圆心经度（仅 CIRCLE） */
    private BigDecimal centerLng;

    /** 圆心纬度（仅 CIRCLE） */
    private BigDecimal centerLat;

    /** 半径（米，仅 CIRCLE） */
    private BigDecimal radiusMeters;

    /** 多边形顶点 JSON（仅 POLYGON，存储坐标系，首尾不闭合） */
    private String points;

    /** 坐标系（与点位同源） */
    private String coordType;

    /** 外接矩形最小经度（粗筛用） */
    private BigDecimal minLng;

    /** 外接矩形最大经度 */
    private BigDecimal maxLng;

    /** 外接矩形最小纬度 */
    private BigDecimal minLat;

    /** 外接矩形最大纬度 */
    private BigDecimal maxLat;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 正常 / 1 已删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
