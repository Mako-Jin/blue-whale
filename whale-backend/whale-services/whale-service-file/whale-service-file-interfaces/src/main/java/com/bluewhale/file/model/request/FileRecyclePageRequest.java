package com.bluewhale.file.model.request;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回收站分页查询请求。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileRecyclePageRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 原始文件名模糊条件 */
    private String originalName;

    /** 桶名称等值条件 */
    private String bucket;
}
