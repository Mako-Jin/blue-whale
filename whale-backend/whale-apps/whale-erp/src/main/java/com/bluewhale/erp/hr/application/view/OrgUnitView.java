package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.OrgUnit;
import lombok.Data;

/**
 * 组织单元视图。
 *
 * @author BlueWhale
 */
@Data
public class OrgUnitView {

    /** 主键 */
    private Long id;

    /** 组织编码 */
    private String code;

    /** 组织名称 */
    private String name;

    /** 上级组织编码 */
    private String parentCode;

    /** 负责人 */
    private String manager;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param org 组织
     * @return 视图
     */
    public static OrgUnitView of(OrgUnit org) {
        OrgUnitView view = new OrgUnitView();
        view.setId(org.getId());
        view.setCode(org.getCode());
        view.setName(org.getName());
        view.setParentCode(org.getParentCode());
        view.setManager(org.getManager());
        view.setStatus(org.getStatus());
        view.setRemark(org.getRemark());
        return view;
    }
}
