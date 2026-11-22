package com.bluewhale.i18n.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 国际化消息新增 / 更新请求（服务本地管理接口）
 *
 * @author BlueWhale
 */
@Data
public class I18nMessageSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息编码，如 login.success（与 locale 组成唯一键）
     */
    @NotBlank(message = "{validation.i18n.i18n-message.message-key-required}")
    @Size(max = 128, message = "{validation.i18n.i18n-message.message-key-size}")
    private String messageKey;

    /**
     * 语言环境，如 zh_CN、en_US（与 messageKey 组成唯一键）
     */
    @NotBlank(message = "{validation.i18n.i18n-message.locale-required}")
    @Size(max = 16, message = "{validation.i18n.i18n-message.locale-size}")
    private String locale;

    /**
     * 消息内容
     */
    @NotBlank(message = "{validation.i18n.i18n-message.message-content-required}")
    private String messageContent;

    /**
     * 所属业务模块
     */
    @Size(max = 64, message = "{validation.i18n.i18n-message.module-size}")
    private String module;

    /**
     * 备注
     */
    @Size(max = 255, message = "{validation.i18n.i18n-message.remark-size}")
    private String remark;
}
