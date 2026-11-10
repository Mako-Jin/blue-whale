package com.bluewhale.mall.cart.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购物车条目实体，对应表 {@code mall_cart_item}。
 *
 * <p>刻意<b>不继承</b> {@code BaseEntity}：该表没有 {@code deleted} 列，
 * 购物车条目的删除是物理删除（语义就是「不在了」）。继承基类后 MyBatis-Plus 会给
 * 每条查询追加 {@code deleted = 0}，直接报列不存在。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("mall_cart_item")
public class CartItemEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 渠道 ID */
    private Long channelId;

    /** 买家用户 ID */
    private Long userId;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 是否选中 */
    private Integer selected;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
