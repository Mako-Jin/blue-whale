package com.bluewhale.crm.sales.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.sales.application.command.ForecastAdjustCommand;
import com.bluewhale.crm.sales.application.command.ForecastGenerateCommand;
import com.bluewhale.crm.sales.application.view.ForecastAdjustmentView;
import com.bluewhale.crm.sales.application.view.ForecastSnapshotView;
import com.bluewhale.crm.sales.domain.model.ForecastQuery;

import java.util.List;

/**
 * 销售预测应用服务。
 *
 * @author BlueWhale
 */
public interface ForecastAppService {

    /**
     * 分页查询预测快照。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<ForecastSnapshotView> page(ForecastQuery query, CrmActor actor);

    /**
     * 查询预测快照详情。
     *
     * @param id 快照 ID
     * @param actor 当前操作人
     * @return 快照详情
     */
    ForecastSnapshotView detail(Long id, CrmActor actor);

    /**
     * 生成 / 重算预测快照（同周期同范围覆盖）。
     *
     * @param command 生成命令
     * @param actor 当前操作人
     * @return 快照详情
     */
    ForecastSnapshotView generate(ForecastGenerateCommand command, CrmActor actor);

    /**
     * 手工调整预测金额并留痕。
     *
     * @param id 快照 ID
     * @param command 调整命令
     * @param actor 当前操作人
     * @return 快照详情
     */
    ForecastSnapshotView adjust(Long id, ForecastAdjustCommand command, CrmActor actor);

    /**
     * 查询预测调整记录。
     *
     * @param id 快照 ID
     * @param actor 当前操作人
     * @return 调整记录
     */
    List<ForecastAdjustmentView> adjustments(Long id, CrmActor actor);
}
