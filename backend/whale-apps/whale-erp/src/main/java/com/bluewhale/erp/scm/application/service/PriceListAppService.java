package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.PriceListCreateCommand;
import com.bluewhale.erp.scm.application.command.PriceListUpdateCommand;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;

import java.time.LocalDate;

/**
 * 价目表应用服务。
 *
 * @author BlueWhale
 */
public interface PriceListAppService {

    /**
     * 分页查询价目表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PriceListView> page(PriceListQuery query);

    /**
     * 查询价目详情。
     *
     * @param id 主键
     * @return 视图
     */
    PriceListView detail(Long id);

    /**
     * 新建价目。
     *
     * @param command 创建命令
     * @return 视图
     */
    PriceListView create(PriceListCreateCommand command);

    /**
     * 更新价目单价、有效期、备注与状态。
     *
     * @param id 主键
     * @param command 更新命令
     * @return 视图
     */
    PriceListView update(Long id, PriceListUpdateCommand command);

    /**
     * 切换价目启停状态。
     *
     * @param id 主键
     * @return 视图
     */
    PriceListView toggle(Long id);

    /**
     * 按「类型 + 往来方 + 物料 + 日期」取价。
     *
     * @param priceType 价格类型
     * @param partnerId 往来方主键
     * @param itemId 物料主键
     * @param date 业务日期（空取当天）
     * @return 命中的价目
     */
    PriceListView resolve(PriceType priceType, Long partnerId, Long itemId, LocalDate date);
}
