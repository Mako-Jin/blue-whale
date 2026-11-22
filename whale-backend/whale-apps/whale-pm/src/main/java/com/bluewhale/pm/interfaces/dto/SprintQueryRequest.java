package com.bluewhale.pm.interfaces.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 迭代分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SprintQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    @NotNull(message = "{validation.pm.sprint.project-id-required}")
    private Long projectId;

    /** 状态：FUTURE / ACTIVE / CLOSED */
    private String status;
}
