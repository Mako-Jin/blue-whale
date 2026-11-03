package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 内容点赞实体，对应表 {@code vd_like}。
 *
 * <p>该表仅需主键、用户与内容三个字段，{@code create_time} 由数据库默认值填充。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_like")
public class ContentLikeEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 内容 ID */
    private Long contentId;
}
