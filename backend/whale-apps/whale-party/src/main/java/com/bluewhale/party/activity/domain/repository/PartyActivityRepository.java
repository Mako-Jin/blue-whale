package com.bluewhale.party.activity.domain.repository;

import com.bluewhale.party.activity.domain.model.ActivitySignin;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import com.bluewhale.party.activity.domain.model.PartyActivityQuery;

import java.util.List;
import java.util.Optional;

/**
 * 组织生活仓储端口。
 *
 * @author BlueWhale
 */
public interface PartyActivityRepository {

    /**
     * 分页查询组织生活。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 列表
     */
    List<PartyActivity> page(PartyActivityQuery query, long offset, int size);

    /**
     * 统计组织生活总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PartyActivityQuery query);

    /**
     * 按主键查询。
     *
     * @param id 组织生活 ID
     * @return 组织生活
     */
    Optional<PartyActivity> findById(Long id);

    /**
     * 保存组织生活。
     *
     * @param activity 组织生活
     * @return 主键
     */
    Long save(PartyActivity activity);

    /**
     * 查询某场组织生活的全部签到。
     *
     * @param activityId 组织生活 ID
     * @return 签到列表
     */
    List<ActivitySignin> listSignins(Long activityId);

    /**
     * 保存签到（同一场次同一人唯一，存在即更新）。
     *
     * @param signin 签到记录
     * @return 主键
     */
    Long saveSignin(ActivitySignin signin);

    /**
     * 统计某场组织生活到会人数。
     *
     * @param activityId 组织生活 ID
     * @return 到会人数
     */
    long countPresent(Long activityId);
}
