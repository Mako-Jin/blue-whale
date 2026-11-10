package com.bluewhale.mall.channel.domain.repository;

import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.channel.domain.model.MallChannel;

import java.util.List;
import java.util.Optional;

/**
 * 渠道仓储。
 *
 * @author BlueWhale
 */
public interface ChannelRepository {

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 渠道列表
     */
    List<MallChannel> page(ChannelQuery query, long offset, int size);

    /**
     * 统计数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ChannelQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 渠道 ID
     * @return 渠道
     */
    Optional<MallChannel> findById(Long id);

    /**
     * 编码是否已存在。
     *
     * @param channelCode 编码
     * @param excludeId 需排除的 ID
     * @return 存在返回 true
     */
    boolean existsCode(String channelCode, Long excludeId);

    /**
     * 保存（新建或更新）。
     *
     * @param channel 渠道
     * @return 渠道 ID
     */
    Long save(MallChannel channel);

    /**
     * 更新状态。
     *
     * @param id 渠道 ID
     * @param status 状态
     */
    void updateStatus(Long id, String status);
}
