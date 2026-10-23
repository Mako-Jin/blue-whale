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
    @NotBlank(message = "消息编码不能为空")
    @Size(max = 128, message = "消息编码长度不能超过 128")
    private String messageKey;

    /**
     * 语言环境，如 zh_CN、en_US（与 messageKey 组成唯一键）
     */
    @NotBlank(message = "语言环境不能为空")
    @Size(max = 16, message = "语言环境长度不能超过 16")
    private String locale;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String messageContent;

    /**
     * 所属业务模块
     */
    @Size(max = 64, message = "业务模块长度不能超过 64")
    private String module;

    /**
     * 备注
     */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
