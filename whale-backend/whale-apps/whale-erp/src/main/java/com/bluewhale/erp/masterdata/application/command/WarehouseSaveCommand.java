package com.bluewhale.erp.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仓库新增 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class WarehouseSaveCommand {

    /** 仓库编码 */
    @NotBlank(message = "仓库编码不能为空")
    private String code;

    /** 仓库名称 */
    @NotBlank(message = "仓库名称不能为空")
    private String name;

    /** 负责人 */
    private String manager;

    /** 地址 */
    private String address;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
