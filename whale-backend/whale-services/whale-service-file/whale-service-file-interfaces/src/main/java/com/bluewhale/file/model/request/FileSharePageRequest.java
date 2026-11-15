package com.bluewhale.file.model.request;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分享链接分页查询请求。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileSharePageRequest extends PageQuery {

    private static final long serialVersionUID = 1L;
}
