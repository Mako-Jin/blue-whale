package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织单元保存命令（新增与编辑共用，编码不可修改）。
 *
 * @author BlueWhale
 */
@Data
public class OrgUnitSaveCommand {

    /** 组织编码 */
    @NotBlank
    private String code;

    /** 组织名称 */
    @NotBlank
    private String name;

    /** 上级组织编码 */
    private String parentCode;

    /** 负责人 */
    private String manager;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
