package com.bluewhale.mall.channel.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.channel.application.command.ChannelSaveCommand;
import com.bluewhale.mall.channel.application.view.ChannelView;
import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.common.MallActor;

/**
 * 渠道应用服务。
 *
 * @author BlueWhale
 */
public interface ChannelAppService {

    /**
     * 渠道分页。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ChannelView> page(ChannelQuery query, Long current, Long size, MallActor actor);

    /**
     * 新建渠道。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 渠道视图
     */
    ChannelView create(ChannelSaveCommand command, MallActor actor);

    /**
     * 编辑渠道。
     *
     * @param id 渠道 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 渠道视图
     */
    ChannelView update(Long id, ChannelSaveCommand command, MallActor actor);

    /**
     * 启用 / 停用渠道。
     *
     * @param id 渠道 ID
     * @param status 目标状态
     * @param actor 操作人
     */
    void changeStatus(Long id, String status, MallActor actor);
}
