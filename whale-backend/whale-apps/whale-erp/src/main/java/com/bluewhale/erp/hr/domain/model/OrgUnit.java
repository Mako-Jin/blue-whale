package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

/**
 * 组织单元。
 *
 * <p>不变量：编码与名称非空；上级组织不能是自身；停用组织不允许新挂员工（由应用服务校验）。</p>
 *
 * @author BlueWhale
 */
@Data
public class OrgUnit {

    /** 主键 */
    private Long id;

    /** 组织编码 */
    private String code;

    /** 组织名称 */
    private String name;

    /** 上级组织编码（顶层为空） */
    private String parentCode;

    /** 负责人 */
    private String manager;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验组织基本信息。
     */
    public void validate() {
        if (code == null || code.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "组织编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "组织名称不能为空");
        }
        if (code.equals(parentCode)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "上级组织不能是自身");
        }
    }

    /**
     * 判断组织是否启用。
     *
     * @return 是否启用
     */
    public boolean enabled() {
        return HrStatus.ORG_ENABLED.equals(status);
    }
}
