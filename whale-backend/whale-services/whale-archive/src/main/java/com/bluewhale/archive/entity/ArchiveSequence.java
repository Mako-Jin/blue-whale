package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档号流水实体，对应表 {@code archive_sequence}。
 *
 * <p>按「门类编码 + 年度」维度原子自增，{@code (categoryCode, year)} 唯一。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_sequence")
public class ArchiveSequence extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 门类编码 */
    private String categoryCode;

    /** 归档年度 */
    private Integer year;

    /** 当前已分配流水号 */
    private Long currentValue;
}
