package com.bluewhale.party.union.domain.repository;

import com.bluewhale.party.union.domain.model.UnionProposal;
import com.bluewhale.party.union.domain.model.UnionProposalQuery;

import java.util.List;
import java.util.Optional;

/**
 * 职代会提案仓储端口。
 *
 * @author BlueWhale
 */
public interface UnionProposalRepository {

    /**
     * 分页查询提案。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 提案列表
     */
    List<UnionProposal> page(UnionProposalQuery query, long offset, int size);

    /**
     * 统计提案。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(UnionProposalQuery query);

    /**
     * 按主键查询提案。
     *
     * @param id 提案 ID
     * @return 提案
     */
    Optional<UnionProposal> findById(Long id);

    /**
     * 保存提案。
     *
     * @param proposal 提案
     * @return 主键
     */
    Long save(UnionProposal proposal);
}
