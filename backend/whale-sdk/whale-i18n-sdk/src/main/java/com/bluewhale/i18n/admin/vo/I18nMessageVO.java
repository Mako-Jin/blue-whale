package com.bluewhale.i18n.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 国际化消息视图对象（字段与 sys_i18n_message 表一致）
 *
 * @author BlueWhale
 */
@Data
public class I18nMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 消息编码
     */
    private String messageKey;

    /**
     * 语言环境
     */
    private String locale;

    /**
     * 消息内容
     */
    private String messageContent;

    /**
     * 所属业务模块
     */
    private String module;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
