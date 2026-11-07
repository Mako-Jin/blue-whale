package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.SpuSaveCommand;
import com.bluewhale.mall.catalog.application.view.SpuDetailView;
import com.bluewhale.mall.catalog.application.view.SpuView;
import com.bluewhale.mall.catalog.domain.model.SpuQuery;
import com.bluewhale.mall.common.MallActor;

/**
 * 商品应用服务。
 *
 * @author BlueWhale
 */
public interface ProductAppService {

    /**
     * 商品分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<SpuView> page(SpuQuery query, MallActor actor);

    /**
     * 商品详情（含 SKU、价目与库存）。
     *
     * @param id 商品 ID
     * @param actor 操作人
     * @return 详情
     */
    SpuDetailView detail(Long id, MallActor actor);

    /**
     * 新建商品（落为草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 商品 ID
     */
    Long create(SpuSaveCommand command, MallActor actor);

    /**
     * 编辑商品（SKU 与价目全量覆盖）。
     *
     * @param id 商品 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, SpuSaveCommand command, MallActor actor);

    /**
     * 上架 / 下架。
     *
     * @param id 商品 ID
     * @param target 目标状态（ON_SALE / OFF_SALE）
     * @param actor 操作人
     */
    void changeStatus(Long id, String target, MallActor actor);

    /**
     * 删除商品（在售商品需先下架）。
     *
     * @param id 商品 ID
     * @param actor 操作人
     */
    void remove(Long id, MallActor actor);
}
