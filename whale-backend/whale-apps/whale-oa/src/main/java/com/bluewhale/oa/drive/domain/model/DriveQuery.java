package com.bluewhale.oa.drive.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网盘文件分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DriveQuery extends PageQuery {

    /** 文件夹ID（必填） */
    private Long folderId;

    /** 关键字（文件名） */
    private String keyword;
}
