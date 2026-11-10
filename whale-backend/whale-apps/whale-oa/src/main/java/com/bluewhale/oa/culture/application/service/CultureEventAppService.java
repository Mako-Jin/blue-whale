package com.bluewhale.oa.culture.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;

/**
 * 文化动态应用服务。
 *
 * @author BlueWhale
 */
public interface CultureEventAppService {

    /**
     * 文化动态分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CultureViews.Event> page(CultureQuery query);

    /**
     * 文化动态详情。
     *
     * @param id 动态ID
     * @return 详情
     */
    CultureViews.Event detail(Long id);

    /**
     * 新建文化动态（默认草稿）。
     *
     * @param command 保存命令
     * @return 动态
     */
    CultureViews.Event create(CultureCommands.EventSave command);

    /**
     * 编辑文化动态。
     *
     * @param id 动态ID
     * @param command 保存命令
     * @return 动态
     */
    CultureViews.Event update(Long id, CultureCommands.EventSave command);

    /**
     * 删除文化动态。
     *
     * @param id 动态ID
     */
    void delete(Long id);

    /**
     * 发布文化动态。
     *
     * @param id 动态ID
     * @param operatorId 发布人用户ID
     * @param operator 发布人姓名
     * @return 动态
     */
    CultureViews.Event publish(Long id, Long operatorId, String operator);
}
