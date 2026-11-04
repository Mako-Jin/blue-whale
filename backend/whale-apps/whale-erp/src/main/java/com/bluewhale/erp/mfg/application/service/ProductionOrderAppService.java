package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.command.ProductionIssueCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderCreateCommand;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;

/**
 * 生产订单应用服务。
 *
 * @author BlueWhale
 */
public interface ProductionOrderAppService {

    /**
     * 分页查询生产订单。
     *
     * @param query 查询条件
     * @return 生产订单分页
     */
    PageResult<ProductionOrderView> page(MfgQuery query);

    /**
     * 查询生产订单详情。
     *
     * @param id 主键
     * @return 生产订单
     */
    ProductionOrderView detail(Long id);

    /**
     * 新增生产订单（草稿）。
     *
     * @param command 创建命令
     * @return 生产订单
     */
    ProductionOrderView create(ProductionOrderCreateCommand command);

    /**
     * 下达生产订单。
     *
     * @param id 主键
     * @return 生产订单
     */
    ProductionOrderView release(Long id);

    /**
     * 生产领料：从领料仓出库并累计材料成本。
     *
     * @param id 主键
     * @param command 领料命令
     * @return 生产订单
     */
    ProductionOrderView issue(Long id, ProductionIssueCommand command);

    /**
     * 完工入库：产出物料入完工仓，并生成生产领料/完工凭证。
     *
     * @param id 主键
     * @param command 完工命令
     * @param operatorId 操作人 ID
     * @return 生产订单
     */
    ProductionOrderView complete(Long id, ProductionCompleteCommand command, Long operatorId);

    /**
     * 结案。
     *
     * @param id 主键
     * @return 生产订单
     */
    ProductionOrderView close(Long id);

    /**
     * 取消生产订单（仅草稿或已下达）。
     *
     * @param id 主键
     * @return 生产订单
     */
    ProductionOrderView cancel(Long id);
}
