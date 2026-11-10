package com.bluewhale.i18n.admin.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 国际化消息分页查询条件（服务本地管理接口）
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class I18nMessageQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 关键字：消息编码模糊匹配
     */
    private String keyword;

    /**
     * 语言环境（精确匹配），如 zh_CN、en_US
     */
    private String locale;

    /**
     * 所属业务模块（精确匹配）
     */
    private String module;
}
