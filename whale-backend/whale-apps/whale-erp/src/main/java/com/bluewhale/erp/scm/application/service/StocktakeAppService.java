package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.StocktakeCreateCommand;
import com.bluewhale.erp.scm.application.view.StocktakeView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 库存盘点应用服务。
 *
 * @author BlueWhale
 */
public interface StocktakeAppService {

    /**
     * 分页查询盘点单。
     *
     * @param query 查询条件
     * @return 盘点单分页
     */
    PageResult<StocktakeView> page(ScmQuery query);

    /**
     * 查询盘点单详情。
     *
     * @param id 主键
     * @return 盘点单
     */
    StocktakeView detail(Long id);

    /**
     * 新增盘点单（草稿，账面数量按当前余额快照）。
     *
     * @param command 创建命令
     * @return 盘点单
     */
    StocktakeView create(StocktakeCreateCommand command);

    /**
     * 过账：按差异生成出入库流水并自动记账。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 盘点单
     */
    StocktakeView post(Long id, Long operatorId);

    /**
     * 取消盘点单（仅草稿）。
     *
     * @param id 主键
     * @return 盘点单
     */
    StocktakeView cancel(Long id);
}
