package com.bluewhale.mes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 生产工单新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单编码（租户内唯一） */
    @NotBlank(message = "工单编码不能为空")
    @Size(max = 64, message = "工单编码长度不能超过 64")
    private String code;

    /** 工单名称 */
    @NotBlank(message = "工单名称不能为空")
    @Size(max = 128, message = "工单名称长度不能超过 128")
    private String name;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
