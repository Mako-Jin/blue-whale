package com.bluewhale.form.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改表单数据请求（审批环节改表单）。
 *
 * <p>以整份 {@code dataJson} 覆盖提交；未提交的既有字段保持原值（服务端合并）。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormDataUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表单数据 JSON（必填，最长 64KB） */
    @NotBlank(message = "表单数据不能为空")
    private String dataJson;
}
