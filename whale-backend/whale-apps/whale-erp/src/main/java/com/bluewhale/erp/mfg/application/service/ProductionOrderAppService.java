package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.command.ProductionIssueCommand;
import com.bluewhale.erp.mfg.application.command.ProductionMaterialAdjustCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOperationReportCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderCreateCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderFromSalesOrderCommand;
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
     * 销售订单行转生产订单（草稿）。
     *
     * <p>产出物料与数量取自销售订单行，同一销售订单行只能转一张生产订单。</p>
     *
     * @param salesOrderId 销售订单主键
     * @param lineNo       销售订单行号
     * @param command      转单命令
     * @return 生产订单
     */
    ProductionOrderView createFromSalesOrder(Long salesOrderId, Integer lineNo,
                                             ProductionOrderFromSalesOrderCommand command);

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
     * 工序报工（累计合格/报废，非首道不得超过前道累计合格量）。
     *
     * @param id      主键
     * @param stepNo  工序号
     * @param command 报工命令
     * @return 生产订单
     */
    ProductionOrderView reportOperation(Long id, Integer stepNo, ProductionOperationReportCommand command);

    /**
     * 退料：按已领成本比例回冲库存与材料成本。
     *
     * @param id         主键
     * @param command    退料命令
     * @param operatorId 操作人 ID
     * @return 生产订单
     */
    ProductionOrderView returnMaterial(Long id, ProductionMaterialAdjustCommand command, Long operatorId);

    /**
     * 报废：扣减已领数量与成本，不回库存。
     *
     * @param id         主键
     * @param command    报废命令
     * @param operatorId 操作人 ID
     * @return 生产订单
     */
    ProductionOrderView scrapMaterial(Long id, ProductionMaterialAdjustCommand command, Long operatorId);

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

    /**
     * 重新拉取 PDM 工艺路线并覆盖订单工序快照。
     *
     * <p>仅草稿/已下达且尚无报工的订单可同步。</p>
     *
     * @param id 生产订单主键
     * @return 生产订单视图
     */
    ProductionOrderView syncRouting(Long id);
}
