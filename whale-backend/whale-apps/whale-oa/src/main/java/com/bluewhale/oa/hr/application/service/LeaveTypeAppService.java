package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.LeaveTypeSaveCommand;
import com.bluewhale.oa.hr.application.view.LeaveTypeView;
import com.bluewhale.oa.hr.domain.model.LeaveTypeQuery;

import java.util.List;

/**
 * 假期类型应用服务。
 *
 * @author BlueWhale
 */
public interface LeaveTypeAppService {

    /**
     * 分页查询假期类型。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LeaveTypeView> page(LeaveTypeQuery query);

    /**
     * 查询全部启用假期类型。
     *
     * @return 启用类型列表
     */
    List<LeaveTypeView> listEnabled();

    /**
     * 新增假期类型。
     *
     * @param command 保存命令
     * @return 新增后的类型
     */
    LeaveTypeView create(LeaveTypeSaveCommand command);

    /**
     * 更新假期类型。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的类型
     */
    LeaveTypeView update(Long id, LeaveTypeSaveCommand command);

    /**
     * 启用 / 停用假期类型。
     *
     * @param id 主键
     * @param enabled 是否启用
     * @return 更新后的类型
     */
    LeaveTypeView toggle(Long id, boolean enabled);
}
