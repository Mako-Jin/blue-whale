package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电影元数据实体，对应表 {@code vd_movie}。
 *
 * <p>主键即 {@code vd_content.id}；该表无 {@code update_time} / {@code deleted} 列，
 * 电影元数据随内容逻辑删除时物理删除，故不复用 {@code BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_movie")
public class MovieEntity {

    /** 内容 ID（主键，与 vd_content.id 一致） */
    @TableId(value = "content_id", type = IdType.INPUT)
    private Long contentId;

    /** 原始片名 */
    private String originalTitle;

    /** 导演（多人逗号分隔） */
    private String director;

    /** 主演（多人逗号分隔） */
    private String actors;

    /** 地区 */
    private String region;

    /** 语言 */
    private String language;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 评分（0.0–10.0） */
    private BigDecimal rating;

    /** 授权来源：OWNED / LICENSED / PUBLIC_DOMAIN */
    private String licenseSource;

    /** 授权编号 */
    private String licenseNo;

    /** 授权到期时间 */
    private LocalDateTime licenseExpireAt;

    /** 海报文件 ID（内部引用） */
    private Long posterFileId;

    /** 背景图文件 ID（内部引用） */
    private Long backdropFileId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
