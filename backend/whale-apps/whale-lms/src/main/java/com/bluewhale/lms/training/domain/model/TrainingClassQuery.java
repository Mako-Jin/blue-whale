package com.bluewhale.lms.training.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 培训班分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingClassQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（编码 / 名称） */
    private String keyword;

    /** 形式（ONLINE / OFFLINE / BLENDED，空为全部） */
    private String mode;

    /** 状态（DRAFT / PUBLISHED / CLOSED，空为全部） */
    private String status;
}
