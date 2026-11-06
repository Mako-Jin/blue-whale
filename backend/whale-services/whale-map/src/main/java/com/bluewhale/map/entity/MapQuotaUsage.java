package com.bluewhale.map.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商日配额用量实体，对应表 {@code map_quota_usage}。
 *
 * <p>用量落库而不是放内存：内存计数在多实例下各自为政，四个实例各算一万次，
 * 实际打了四万次；重启还会清零，超配额后供应商直接返回错误码，用户看到的是「地图坏了」。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("map_quota_usage")
public class MapQuotaUsage {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 供应商名 */
    private String provider;

    /** 统计日期 */
    private LocalDate usageDate;

    /** 已用次数 */
    private Integer usedCount;

    /** 上限（0 表示不限） */
    private Integer limitCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
