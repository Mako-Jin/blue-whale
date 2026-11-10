package com.bluewhale.erp.hr.application.service;

import com.bluewhale.erp.hr.application.command.OrgUnitSaveCommand;
import com.bluewhale.erp.hr.application.view.OrgUnitView;
import com.bluewhale.erp.hr.domain.model.HrQuery;

import java.util.List;

/**
 * 组织单元应用服务。
 *
 * @author BlueWhale
 */
public interface OrgAppService {

    /**
     * 查询组织列表。
     *
     * @param query 查询条件
     * @return 组织列表
     */
    List<OrgUnitView> list(HrQuery query);

    /**
     * 新增组织。
     *
     * @param command 保存命令
     * @return 组织
     */
    OrgUnitView create(OrgUnitSaveCommand command);

    /**
     * 编辑组织。
     *
     * @param code 组织编码
     * @param command 保存命令
     * @return 组织
     */
    OrgUnitView update(String code, OrgUnitSaveCommand command);
}
