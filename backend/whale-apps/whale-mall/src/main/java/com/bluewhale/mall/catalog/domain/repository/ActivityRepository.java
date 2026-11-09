package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 限时特价活动仓储。
 *
 * @author BlueWhale
 */
public interface ActivityRepository {

    /**
     * 按条件分页查询。
     *
     * @param code      编码（模糊，可空）
     * @param name      名称（模糊，可空）
     * @param skuId     SKU ID（精确，可空）
     * @param channelId 渠道 ID（精确，可空）
     * @param status    状态（可空）
     * @param offset    偏移量
     * @param size      页大小
     * @return 活动列表
     */
    List<Activity> page(String code, String name, Long skuId, Long channelId, String status, long offset, int size);

    /**
     * 按条件统计总数。
     *
     * @param code      编码（模糊，可空）
     * @param name      名称（模糊，可空）
     * @param skuId     SKU ID（精确，可空）
     * @param channelId 渠道 ID（精确，可空）
     * @param status    状态（可空）
     * @return 总数
     */
    long count(String code, String name, Long skuId, Long channelId, String status);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 活动；不存在或已删除返回空
     */
    Activity findById(Long id);

    /**
     * 编码是否已存在。
     *
     * @param activityCode 活动编码
     * @param excludeId    排除主键（编辑场景，可空）
     * @return 已存在返回 true
     */
    boolean existsCode(String activityCode, Long excludeId);

    /**
     * 查询指定时刻、指定 SKU 集合内的启用中活动（时间窗内）。
     *
     * @param skuIds SKU ID 集合
     * @param now    当前时间
     * @return 命中的活动列表（同一 SKU 可能多条，计价层按活动价孰低取舍）
     */
    List<Activity> matching(List<Long> skuIds, LocalDateTime now);

    /**
     * 保存（新增或更新）。
     *
     * @param activity 活动
     * @return 主键
     */
    Long save(Activity activity);

    /**
     * 更新状态。
     *
     * @param id     主键
     * @param status 状态
     */
    void updateStatus(Long id, String status);
}
