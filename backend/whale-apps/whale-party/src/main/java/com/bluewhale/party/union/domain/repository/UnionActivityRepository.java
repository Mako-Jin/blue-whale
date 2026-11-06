package com.bluewhale.party.union.domain.repository;

import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityQuery;
import com.bluewhale.party.union.domain.model.UnionSignup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 工会活动仓储端口。
 *
 * @author BlueWhale
 */
public interface UnionActivityRepository {

    /**
     * 分页查询活动（管理端）。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 活动列表
     */
    List<UnionActivity> page(UnionActivityQuery query, long offset, int size);

    /**
     * 统计活动（管理端）。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(UnionActivityQuery query);

    /**
     * 分页查询园地活动（仅已发布，按开始时间升序）。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 活动列表
     */
    List<UnionActivity> portalPage(UnionActivityQuery query, long offset, int size);

    /**
     * 统计园地活动。
     *
     * @param query 查询条件
     * @return 总数
     */
    long portalCount(UnionActivityQuery query);

    /**
     * 按主键查询活动。
     *
     * @param id 活动 ID
     * @return 活动
     */
    Optional<UnionActivity> findById(Long id);

    /**
     * 按主键集合查询活动（用于我的报名回填标题，避免逐条查询）。
     *
     * @param ids 活动 ID 集合
     * @return 活动列表
     */
    List<UnionActivity> findByIds(Collection<Long> ids);

    /**
     * 保存活动。
     *
     * @param activity 活动
     * @return 主键
     */
    Long save(UnionActivity activity);

    /**
     * 查询指定活动指定会员的报名记录。
     *
     * @param activityId 活动 ID
     * @param unionMemberId 会员 ID
     * @return 报名记录
     */
    Optional<UnionSignup> findSignup(Long activityId, Long unionMemberId);

    /**
     * 统计活动已报名（未取消）人数。
     *
     * @param activityId 活动 ID
     * @return 已报名人数
     */
    long countSigned(Long activityId);

    /**
     * 保存报名记录。
     *
     * @param signup 报名记录
     * @return 主键
     */
    Long saveSignup(UnionSignup signup);

    /**
     * 分页查询活动报名名单。
     *
     * @param activityId 活动 ID
     * @param offset 偏移量
     * @param size 每页条数
     * @return 报名列表
     */
    List<UnionSignup> pageSignups(Long activityId, long offset, int size);

    /**
     * 统计活动报名名单。
     *
     * @param activityId 活动 ID
     * @return 总数
     */
    long countSignups(Long activityId);

    /**
     * 分页查询我的报名。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param size 每页条数
     * @return 报名列表
     */
    List<UnionSignup> pageMySignups(Long userId, long offset, int size);

    /**
     * 统计我的报名。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countMySignups(Long userId);
}
