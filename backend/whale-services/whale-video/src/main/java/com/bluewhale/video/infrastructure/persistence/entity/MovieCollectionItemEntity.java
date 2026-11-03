package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电影片单条目实体，对应表 {@code vd_collection_item}。
 *
 * <p>该表无 {@code update_time} / {@code deleted} 列，移除条目为物理删除，
 * 故不复用 {@code BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_collection_item")
public class MovieCollectionItemEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 片单 ID */
    private Long collectionId;

    /** 电影内容 ID */
    private Long contentId;

    /** 片单内排序 */
    private Integer sortNo;

    /** 加入时间 */
    private LocalDateTime createTime;
}
