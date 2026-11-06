package com.bluewhale.party.branch.domain.repository;

import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchQuery;

import java.util.List;
import java.util.Optional;

/**
 * 支部仓储端口。
 *
 * @author BlueWhale
 */
public interface BranchRepository {

    /**
     * 分页查询支部。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 支部列表
     */
    List<Branch> page(BranchQuery query, long offset, int size);

    /**
     * 统计支部总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(BranchQuery query);

    /**
     * 按主键查询。
     *
     * @param id 支部 ID
     * @return 支部
     */
    Optional<Branch> findById(Long id);

    /**
     * 按编码查询（编码租户内唯一）。
     *
     * @param branchCode 支部编码
     * @return 支部
     */
    Optional<Branch> findByCode(String branchCode);

    /**
     * 查询全部在册支部（构树与层级计算用）。
     *
     * <p>党务支部数量在百级，一次取全量比逐层递归查询更简单也更快；
     * 若将来单租户支部数进入千级，这里换成按层懒加载即可，调用方不用改。</p>
     *
     * @return 支部列表
     */
    List<Branch> listAll();

    /**
     * 查询直接下级支部。
     *
     * @param parentId 上级支部 ID
     * @return 下级支部列表
     */
    List<Branch> listByParent(Long parentId);

    /**
     * 统计直接下级支部数。
     *
     * @param parentId 上级支部 ID
     * @return 下级数量
     */
    long countByParent(Long parentId);

    /**
     * 保存支部（主键为空时插入，否则更新）。
     *
     * @param branch 支部
     * @return 主键
     */
    Long save(Branch branch);

    /**
     * 统计在册支部数（工作台与看板用）。
     *
     * @return 在册支部数
     */
    long countActive();
}
