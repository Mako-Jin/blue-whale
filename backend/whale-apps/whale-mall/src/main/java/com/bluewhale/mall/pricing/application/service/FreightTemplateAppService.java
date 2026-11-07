package com.bluewhale.mall.pricing.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.pricing.application.command.FreightTemplateSaveCommand;
import com.bluewhale.mall.pricing.application.view.FreightTemplateView;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateQuery;

import java.math.BigDecimal;

/**
 * 运费模板应用服务。
 *
 * @author BlueWhale
 */
public interface FreightTemplateAppService {

    /**
     * 运费模板分页。
     *
     * @param query   查询条件
     * @param current 页码
     * @param size    每页条数
     * @param actor   操作人
     * @return 分页结果
     */
    PageResult<FreightTemplateView> page(FreightTemplateQuery query, Long current, Long size, MallActor actor);

    /**
     * 运费模板详情。
     *
     * @param id    模板 ID
     * @param actor 操作人
     * @return 模板视图
     */
    FreightTemplateView detail(Long id, MallActor actor);

    /**
     * 新建运费模板。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 模板视图
     */
    FreightTemplateView create(FreightTemplateSaveCommand command, MallActor actor);

    /**
     * 编辑运费模板。
     *
     * @param id      模板 ID
     * @param command 保存命令
     * @param actor   操作人
     * @return 模板视图
     */
    FreightTemplateView update(Long id, FreightTemplateSaveCommand command, MallActor actor);

    /**
     * 启停运费模板。
     *
     * @param id     模板 ID
     * @param status 目标状态
     * @param actor  操作人
     */
    void changeStatus(Long id, String status, MallActor actor);

    /**
     * 按渠道绑定的模板计算运费。
     *
     * <p>模板为空、已删除或已停用时返回 0：运费配置是运营侧的疏漏，
     * 不该阻断买家下单。这一条与「停用券模板不影响已领券」是同一个取舍——
     * 配置变更不能让正在进行的交易卡死。</p>
     *
     * @param freightTemplateId 运费模板 ID（可空）
     * @param goodsAmount       商品总额（优惠前）
     * @return 运费金额，不小于 0
     */
    BigDecimal freightFor(Long freightTemplateId, BigDecimal goodsAmount);
}
