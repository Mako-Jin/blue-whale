package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.WorkCenterSaveCommand;
import com.bluewhale.erp.mfg.application.view.WorkCenterView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;

import java.util.List;

/**
 * 工作中心应用服务。
 *
 * @author BlueWhale
 */
public interface WorkCenterAppService {

    /**
     * 分页查询工作中心。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<WorkCenterView> page(MfgQuery query);

    /**
     * 查询全部工作中心（下拉用）。
     *
     * @return 工作中心列表
     */
    List<WorkCenterView> listAll();

    /**
     * 新增工作中心。
     *
     * @param command 保存命令
     * @return 工作中心视图
     */
    WorkCenterView create(WorkCenterSaveCommand command);

    /**
     * 编辑工作中心。
     *
     * @param id      主键
     * @param command 保存命令
     * @return 工作中心视图
     */
    WorkCenterView update(Long id, WorkCenterSaveCommand command);

    /**
     * 删除工作中心。
     *
     * @param id 主键
     */
    void delete(Long id);
}
