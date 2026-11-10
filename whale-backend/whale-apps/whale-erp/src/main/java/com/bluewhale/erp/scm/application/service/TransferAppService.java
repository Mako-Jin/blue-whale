package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.TransferCreateCommand;
import com.bluewhale.erp.scm.application.view.TransferView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 库存调拨应用服务。
 *
 * @author BlueWhale
 */
public interface TransferAppService {

    /**
     * 分页查询调拨单。
     *
     * @param query 查询条件
     * @return 调拨单分页
     */
    PageResult<TransferView> page(ScmQuery query);

    /**
     * 查询调拨单详情。
     *
     * @param id 主键
     * @return 调拨单
     */
    TransferView detail(Long id);

    /**
     * 新增调拨单（草稿）。
     *
     * @param command 创建命令
     * @return 调拨单
     */
    TransferView create(TransferCreateCommand command);

    /**
     * 过账：调出仓出库、调入仓入库，同成本结转。
     *
     * @param id 主键
     * @return 调拨单
     */
    TransferView post(Long id);

    /**
     * 取消调拨单（仅草稿）。
     *
     * @param id 主键
     * @return 调拨单
     */
    TransferView cancel(Long id);
}
