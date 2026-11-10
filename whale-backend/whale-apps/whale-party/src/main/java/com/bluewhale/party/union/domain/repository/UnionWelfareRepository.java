package com.bluewhale.party.union.domain.repository;

import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.UnionWelfareQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 节日福利仓储端口。
 *
 * <p>明细查询全部带批次的 {@code welfare_id}：跨批次的「我的福利」由用户 ID
 * 换算会员 ID 后单独查询，避免把别人的发放记录混进来。</p>
 *
 * @author BlueWhale
 */
public interface UnionWelfareRepository {

    /**
     * 分页查询福利批次。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 批次列表
     */
    List<UnionWelfare> page(UnionWelfareQuery query, long offset, int size);

    /**
     * 统计福利批次。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(UnionWelfareQuery query);

    /**
     * 按主键查询批次。
     *
     * @param id 批次 ID
     * @return 批次
     */
    Optional<UnionWelfare> findById(Long id);

    /**
     * 按主键集合查询批次（用于我的福利回填标题，避免逐条查询）。
     *
     * @param ids 批次 ID 集合
     * @return 批次列表
     */
    List<UnionWelfare> findByIds(Collection<Long> ids);

    /**
     * 保存批次。
     *
     * @param welfare 批次
     * @return 主键
     */
    Long save(UnionWelfare welfare);

    /**
     * 分页查询批次明细。
     *
     * @param welfareId 批次 ID
     * @param signStatus 签收状态文本（可空）
     * @param offset 偏移量
     * @param size 每页条数
     * @return 明细列表
     */
    List<UnionWelfareItem> pageItems(Long welfareId, String signStatus, long offset, int size);

    /**
     * 统计批次明细。
     *
     * @param welfareId 批次 ID
     * @param signStatus 签收状态文本（可空）
     * @return 总数
     */
    long countItems(Long welfareId, String signStatus);

    /**
     * 列出批次全部明细（用于发放幂等校验与我的福利回填）。
     *
     * @param welfareId 批次 ID
     * @return 明细列表
     */
    List<UnionWelfareItem> listItems(Long welfareId);

    /**
     * 按主键查询明细。
     *
     * @param itemId 明细 ID
     * @return 明细
     */
    Optional<UnionWelfareItem> findItemById(Long itemId);

    /**
     * 保存明细。
     *
     * @param item 明细
     * @return 主键
     */
    Long saveItem(UnionWelfareItem item);

    /**
     * 统计批次已签收明细数。
     *
     * @param welfareId 批次 ID
     * @return 已签收数
     */
    long countSigned(Long welfareId);

    /**
     * 分页查询当前用户的福利明细。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param size 每页条数
     * @return 明细列表
     */
    List<UnionWelfareItem> pageMyItems(Long userId, long offset, int size);

    /**
     * 统计当前用户的福利明细。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countMyItems(Long userId);
}
