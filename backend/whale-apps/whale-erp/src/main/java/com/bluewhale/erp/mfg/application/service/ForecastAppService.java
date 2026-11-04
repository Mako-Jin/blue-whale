package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.ForecastCreateCommand;
import com.bluewhale.erp.mfg.application.view.ForecastView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;

/**
 * 需求预测应用服务。
 *
 * @author BlueWhale
 */
public interface ForecastAppService {

    /**
     * 分页查询需求预测。
     *
     * @param query 查询条件
     * @return 预测分页
     */
    PageResult<ForecastView> page(MfgQuery query);

    /**
     * 查询预测详情。
     *
     * @param id 主键
     * @return 预测
     */
    ForecastView detail(Long id);

    /**
     * 新增预测批次（草稿）。
     *
     * @param command 创建命令
     * @return 预测
     */
    ForecastView create(ForecastCreateCommand command);

    /**
     * 发布预测。
     *
     * @param id 主键
     * @return 预测
     */
    ForecastView publish(Long id);

    /**
     * 取消预测。
     *
     * @param id 主键
     * @return 预测
     */
    ForecastView cancel(Long id);
}
