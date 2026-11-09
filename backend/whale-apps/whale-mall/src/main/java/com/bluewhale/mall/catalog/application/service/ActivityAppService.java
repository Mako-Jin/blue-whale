package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.ActivitySaveCommand;
import com.bluewhale.mall.catalog.application.view.ActivityView;
import com.bluewhale.mall.common.MallActor;

import java.util.Collection;
import java.util.Map;

/**
 * 限时特价活动应用服务。
 *
 * @author BlueWhale
 */
public interface ActivityAppService {

    /**
     * 活动分页。
     *
     * @param code      编码关键字
     * @param name      名称关键字
     * @param skuId     SKU ID
     * @param channelId 渠道 ID
     * @param status    状态
     * @param current   页码
     * @param size      每页条数
     * @param actor     操作人
     * @return 分页结果
     */
    PageResult<ActivityView> page(String code, String name, Long skuId, Long channelId, String status,
                                  Long current, Long size, MallActor actor);

    /**
     * 活动详情。
     *
     * @param id    主键
     * @param actor 操作人
     * @return 详情
     */
    ActivityView detail(Long id, MallActor actor);

    /**
     * 新建/更新活动。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 主键
     */
    Long save(ActivitySaveCommand command, MallActor actor);

    /**
     * 启停活动。
     *
     * @param id     主键
     * @param status 目标状态
     * @param actor  操作人
     */
    void changeStatus(Long id, String status, MallActor actor);

    /**
     * 命中活动（计价入口）：返回 SKU ID → 有效活动（同时刻同 SKU 多条时取活动价最低者）。
     *
     * @param skuIds    SKU ID 集合
     * @param channelId 订单渠道 ID
     * @param now       当前时间
     * @return SKU ID → 活动
     */
    Map<Long, com.bluewhale.mall.catalog.domain.model.Activity> matching(Collection<Long> skuIds,
                                                                         Long channelId,
                                                                         java.time.LocalDateTime now);
}
