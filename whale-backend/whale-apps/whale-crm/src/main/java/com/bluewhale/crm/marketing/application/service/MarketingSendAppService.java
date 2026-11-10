package com.bluewhale.crm.marketing.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.marketing.application.command.DeliveryReceiptCommand;
import com.bluewhale.crm.marketing.application.command.MarketingSendCreateCommand;
import com.bluewhale.crm.marketing.application.view.DeliveryReceiptView;
import com.bluewhale.crm.marketing.application.view.MarketingSendView;
import com.bluewhale.crm.marketing.domain.model.MarketingSendQuery;

/**
 * 批量触达应用服务。
 *
 * @author BlueWhale
 */
public interface MarketingSendAppService {

    /**
     * 分页查询批量触达任务。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<MarketingSendView> page(MarketingSendQuery query, CrmActor actor);

    /**
     * 查看触达任务详情（含明细）。
     *
     * @param id 任务 ID
     * @param actor 当前操作人
     * @return 视图
     */
    MarketingSendView detail(Long id, CrmActor actor);

    /**
     * 创建触达任务（从已锁定人群快照复制成员，排除被排除名单）。
     *
     * @param command 创建命令
     * @param actor 当前操作人
     * @return 视图
     */
    MarketingSendView create(MarketingSendCreateCommand command, CrmActor actor);

    /**
     * 执行触达任务（按活动 + 联系人 + 渠道幂等跳过重复触达）。
     *
     * @param id 任务 ID
     * @param actor 当前操作人
     * @return 视图
     */
    MarketingSendView execute(Long id, CrmActor actor);

    /**
     * 承接渠道送达回执（幂等，单向推进明细状态并重算任务计数）。
     *
     * @param command 回执回调命令
     * @return 处理结果
     */
    DeliveryReceiptView applyDeliveryReceipt(DeliveryReceiptCommand command);
}
