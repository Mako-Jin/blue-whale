package com.bluewhale.party.common.sequence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务编号流水实体，对应表 {@code pty_sequence}。
 *
 * <p>刻意<b>不继承</b> {@code BaseEntity}：该表没有 {@code deleted} 列，也不做逻辑删除。
 * 若继承基类，MyBatis-Plus 会为每条查询追加 {@code deleted = 0}，直接报列不存在。
 * 也不声明 {@code tenantId}：租户列由行级隔离插件统一写入，实体再声明一次会重复。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("pty_sequence")
public class SequenceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 编号键：BRANCH / MEMBER / PROPOSAL 等 */
    private String seqKey;

    /** 周期标识：空串=不重置；yyyy / yyyyMM / yyyyMMdd=按周期重置 */
    private String seqDate;

    /** 当前已发到的最末流水号（只增不减） */
    private Long currentSeq;

    /** 备注（取号规则说明） */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
