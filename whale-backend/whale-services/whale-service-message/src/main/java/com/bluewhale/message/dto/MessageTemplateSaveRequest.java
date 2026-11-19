package com.bluewhale.message.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息模板新增/编辑请求。
 *
 * <p>模板编码在租户内唯一；保存时统一转为大写，与发送侧 {@code templateCode}
 * 的解析口径保持一致，避免大小写漂移导致渲染回落。</p>
 *
 * @author BlueWhale
 */
@Data
public class MessageTemplateSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板编码（租户内唯一，建议大写字母/数字/下划线） */
    @NotBlank(message = "{validation.message.template.template-code-required}")
    @Size(max = 64, message = "{validation.message.template.template-code-size}")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "{validation.message.template.template-name-required}")
    @Size(max = 128, message = "{validation.message.template.template-name-size}")
    private String templateName;

    /** 标题模板，支持 ${key} 占位符 */
    @Size(max = 255, message = "{validation.message.template.title-template-size}")
    private String titleTemplate;

    /** 内容模板，支持 ${key} 占位符 */
    @Size(max = 8000, message = "{validation.message.template.content-template-size}")
    private String contentTemplate;

    /** 状态：1 启用 / 0 停用；新增时为空默认启用，更新时为空表示不修改 */
    @Min(value = 0, message = "{validation.message.template.status-range}")
    @Max(value = 1, message = "{validation.message.template.status-range}")
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "{validation.message.template.remark-size}")
    private String remark;
}
