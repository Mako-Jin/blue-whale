package com.bluewhale.map.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务对象打点实体，对应表 {@code map_location}。
 *
 * <p><b>经纬度用 DECIMAL 而不是 DOUBLE</b>：坐标要参与唯一性判断、缓存键与外发对接，
 * 浮点累加误差会让「同一个点」在不同路径下算出不同的第 8 位小数；DECIMAL(10,7)
 * 约 1 厘米精度，足够表达任何门址，且比较是精确的。</p>
 *
 * <p><b>不继承 BaseEntity</b>：打点是状态而非单据，取消打点即物理删除。
 * 挂上逻辑删除会让唯一键 {@code (tenant_id, biz_type, biz_id)} 在「删掉再打点」时撞键。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("map_location")
public class MapLocation {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务类型（CRM_CONTACT / ERP_SUPPLIER 等） */
    private String bizType;

    /** 业务对象ID（字符串，兼容各类主键） */
    private String bizId;

    /** 经度（存储坐标系） */
    private BigDecimal lng;

    /** 纬度（存储坐标系） */
    private BigDecimal lat;

    /** 坐标系 */
    private String coordType;

    /** geohash（按存储坐标编码） */
    private String geohash;

    /** 标准地址 */
    private String address;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 行政区划码 */
    private String adcode;

    /** 坐标来源供应商 */
    private String provider;

    /** 来源：GEOCODE / MANUAL / IMPORT */
    private String source;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
