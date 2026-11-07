package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.mall.catalog.application.command.CategorySaveCommand;
import com.bluewhale.mall.catalog.application.view.CategoryTreeView;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;
import com.bluewhale.mall.common.MallActor;

import java.util.List;

/**
 * 类目应用服务。
 *
 * @author BlueWhale
 */
public interface CategoryAppService {

    /**
     * 类目树。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 树根列表
     */
    List<CategoryTreeView> tree(CategoryQuery query, MallActor actor);

    /**
     * 新建类目。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 新建的类目节点
     */
    CategoryTreeView create(CategorySaveCommand command, MallActor actor);

    /**
     * 编辑类目。
     *
     * @param id 类目 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 编辑后的类目节点
     */
    CategoryTreeView update(Long id, CategorySaveCommand command, MallActor actor);

    /**
     * 删除类目（无子类目且无商品时允许）。
     *
     * @param id 类目 ID
     * @param actor 操作人
     */
    void remove(Long id, MallActor actor);
}
