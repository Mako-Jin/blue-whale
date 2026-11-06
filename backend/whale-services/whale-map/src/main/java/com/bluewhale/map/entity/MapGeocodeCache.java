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
 * 地址解析缓存实体，对应表 {@code map_geocode_cache}。
 *
 * <p>缓存键是「供应商 + 查询类型 + 查询串哈希」：漏掉任一段都会把不同语义的查询混成一条。
 * 尤其是供应商——同一地址在腾讯与高德可能落在相邻两栋楼，缓存串了之后，
 * 切供应商时位置会「自己变」，而这种变化没有任何日志可查。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("map_geocode_cache")
public class MapGeocodeCache {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 供应商名 */
    private String provider;

    /** 查询类型：GEOCODE / REGEO / POI */
    private String queryType;

    /** 查询串的 SHA-256（十六进制，64 字符） */
    private String cacheKey;

    /** 原始查询串（排障用） */
    private String queryText;

    /** 标准化结果 JSON */
    private String resultJson;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    /** 坐标系 */
    private String coordType;

    /** 命中次数 */
    private Long hitCount;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
