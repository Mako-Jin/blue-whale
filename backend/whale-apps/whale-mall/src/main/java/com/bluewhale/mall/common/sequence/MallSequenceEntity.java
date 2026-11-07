package com.bluewhale.mall.common.sequence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务编号流水实体，对应表 {@code mall_sequence}。
 *
 * <p>刻意<b>不继承</b> {@code BaseEntity}：该表没有 {@code deleted} 列、也不做逻辑删除，
 * 继承后 MyBatis-Plus 会为每条查询追加 {@code deleted = 0}，直接报列不存在。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("mall_sequence")
public class MallSequenceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 编号键：ORDER 等 */
    private String seqKey;

    /** 周期标识：yyyyMMdd=按日重置；空串=不重置 */
    private String seqDate;

    /** 当前已发到的最末流水号（只增不减） */
    private Long currentSeq;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
