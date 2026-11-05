package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.SafetyStockCreateCommand;
import com.bluewhale.erp.scm.application.command.SafetyStockUpdateCommand;
import com.bluewhale.erp.scm.application.view.IdleStockView;
import com.bluewhale.erp.scm.application.view.SafetyStockView;
import com.bluewhale.erp.scm.application.view.ShortageView;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 安全库存应用服务：配置维护、缺料预警与呆滞分析。
 *
 * @author BlueWhale
 */
public interface SafetyStockAppService {

    /**
     * 分页查询安全库存。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SafetyStockView> page(SafetyStockQuery query);

    /**
     * 查询安全库存详情。
     *
     * @param id 主键
     * @return 视图
     */
    SafetyStockView detail(Long id);

    /**
     * 新增安全库存配置。
     *
     * @param command 创建命令
     * @return 视图
     */
    SafetyStockView create(SafetyStockCreateCommand command);

    /**
     * 更新安全库存量与最高库存量。
     *
     * @param id 主键
     * @param command 更新命令
     * @return 视图
     */
    SafetyStockView update(Long id, SafetyStockUpdateCommand command);

    /**
     * 删除安全库存配置（逻辑删除）。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 缺料预警：安全库存与实际余额比较，仅返回缺口大于 0 的项。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ShortageView> shortage(SafetyStockQuery query);

    /**
     * 呆滞分析：余额大于 0 且最近出库早于阈值天数。
     *
     * @param query 查询条件（current/size 分页）
     * @param idleDays 呆滞天数阈值（默认 90）
     * @return 分页结果
     */
    PageResult<IdleStockView> idle(ScmQuery query, Integer idleDays);
}
