package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.MpsCreateCommand;
import com.bluewhale.erp.mfg.application.view.MpsPlanView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;

/**
 * 主生产计划应用服务。
 *
 * @author BlueWhale
 */
public interface MpsAppService {

    /**
     * 分页查询主生产计划。
     *
     * @param query 查询条件
     * @return 计划分页
     */
    PageResult<MpsPlanView> page(MfgQuery query);

    /**
     * 查询计划详情。
     *
     * @param id 主键
     * @return 计划
     */
    MpsPlanView detail(Long id);

    /**
     * 新增主生产计划（草稿）。
     *
     * @param command 创建命令
     * @return 计划
     */
    MpsPlanView create(MpsCreateCommand command);

    /**
     * 下达计划。
     *
     * @param id 主键
     * @return 计划
     */
    MpsPlanView release(Long id);

    /**
     * 取消计划。
     *
     * @param id 主键
     * @return 计划
     */
    MpsPlanView cancel(Long id);
}
