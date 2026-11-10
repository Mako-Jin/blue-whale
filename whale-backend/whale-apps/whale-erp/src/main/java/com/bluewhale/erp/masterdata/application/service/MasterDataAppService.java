package com.bluewhale.erp.masterdata.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.masterdata.application.command.AccountSaveCommand;
import com.bluewhale.erp.masterdata.application.command.CustomerSaveCommand;
import com.bluewhale.erp.masterdata.application.command.WarehouseSaveCommand;
import com.bluewhale.erp.masterdata.application.view.AccountView;
import com.bluewhale.erp.masterdata.application.view.CustomerView;
import com.bluewhale.erp.masterdata.application.view.PeriodView;
import com.bluewhale.erp.masterdata.application.view.ReferenceDataView;
import com.bluewhale.erp.masterdata.application.view.WarehouseView;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;

/**
 * 主数据应用服务：科目、会计期间、客户、仓库与参考数据。
 *
 * <p>M0 只提供财务记账与应收应付所需的最小主数据；计量单位 / 币种 / 税码
 * 在本波为只读参考数据。</p>
 *
 * @author BlueWhale
 */
public interface MasterDataAppService {

    /**
     * 分页查询科目。
     *
     * @param query 查询条件
     * @return 科目分页
     */
    PageResult<AccountView> accountPage(MasterDataQuery query);

    /**
     * 新增科目。
     *
     * @param command 新增命令
     * @return 科目视图
     */
    AccountView createAccount(AccountSaveCommand command);

    /**
     * 编辑科目。
     *
     * @param id 科目 ID
     * @param command 编辑命令
     * @return 科目视图
     */
    AccountView updateAccount(Long id, AccountSaveCommand command);

    /**
     * 分页查询会计期间。
     *
     * @param query 查询条件
     * @return 期间分页
     */
    PageResult<PeriodView> periodPage(MasterDataQuery query);

    /**
     * 关账。
     *
     * @param id 期间 ID
     * @param actorId 操作人 ID
     * @return 期间视图
     */
    PeriodView closePeriod(Long id, Long actorId);

    /**
     * 反结账（需单独权限）。
     *
     * @param id 期间 ID
     * @return 期间视图
     */
    PeriodView reopenPeriod(Long id);

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 客户分页
     */
    PageResult<CustomerView> customerPage(MasterDataQuery query);

    /**
     * 新增客户。
     *
     * @param command 新增命令
     * @return 客户视图
     */
    CustomerView createCustomer(CustomerSaveCommand command);

    /**
     * 编辑客户。
     *
     * @param id 客户 ID
     * @param command 编辑命令
     * @return 客户视图
     */
    CustomerView updateCustomer(Long id, CustomerSaveCommand command);

    /**
     * 分页查询仓库。
     *
     * @param query 查询条件
     * @return 仓库分页
     */
    PageResult<WarehouseView> warehousePage(MasterDataQuery query);

    /**
     * 新增仓库。
     *
     * @param command 新增命令
     * @return 仓库视图
     */
    WarehouseView createWarehouse(WarehouseSaveCommand command);

    /**
     * 编辑仓库。
     *
     * @param id 仓库 ID
     * @param command 编辑命令
     * @return 仓库视图
     */
    WarehouseView updateWarehouse(Long id, WarehouseSaveCommand command);

    /**
     * 查询参考数据。
     *
     * @return 参考数据
     */
    ReferenceDataView referenceData();
}
