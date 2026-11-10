package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 内容收藏实体，对应表 {@code vd_favorite}。
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_favorite")
public class ContentFavoriteEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 内容 ID */
    private Long contentId;
}
