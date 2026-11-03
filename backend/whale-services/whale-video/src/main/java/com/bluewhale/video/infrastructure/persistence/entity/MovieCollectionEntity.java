package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 电影片单实体，对应表 {@code vd_collection}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_collection")
public class MovieCollectionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 片单标题 */
    private String title;

    /** 片单简介 */
    private String description;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 运营排序 */
    private Integer sortNo;

    /** 发布状态 */
    private String publishStatus;

    /** 条目数 */
    private Integer itemCount;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;
}
