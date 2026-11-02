package com.bluewhale.message.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统公告分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 公告状态：PUBLISHED / REVOKED */
    private String status;
}
