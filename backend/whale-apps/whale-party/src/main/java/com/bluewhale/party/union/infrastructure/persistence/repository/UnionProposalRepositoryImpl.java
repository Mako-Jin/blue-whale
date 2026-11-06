package com.bluewhale.party.union.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.union.domain.model.ProposalStatus;
import com.bluewhale.party.union.domain.model.UnionProposal;
import com.bluewhale.party.union.domain.model.UnionProposalQuery;
import com.bluewhale.party.union.domain.repository.UnionProposalRepository;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionProposalEntity;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionProposalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 职代会提案仓储实现（MyBatis-Plus）。
 *
 * <p>{@code mineUserId} 作为 SQL 条件而不是内存过滤：园地分页必须由数据库
 * 直接限定提交人，否则分页条数与可见范围会不一致。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UnionProposalRepositoryImpl implements UnionProposalRepository {

    private final UnionProposalMapper unionProposalMapper;

    @Override
    public List<UnionProposal> page(UnionProposalQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionProposalEntity> page = unionProposalMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(UnionProposalQuery query) {
        return unionProposalMapper.selectCount(filter(query));
    }

    @Override
    public Optional<UnionProposal> findById(Long id) {
        return Optional.ofNullable(unionProposalMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(UnionProposal proposal) {
        UnionProposalEntity entity = toEntity(proposal);
        if (entity.getId() == null) {
            unionProposalMapper.insert(entity);
        } else {
            unionProposalMapper.updateById(entity);
        }
        proposal.setId(entity.getId());
        proposal.setCreateTime(entity.getCreateTime());
        proposal.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<UnionProposal> listByCreateTimeBetween(LocalDateTime from, LocalDateTime to) {
        return unionProposalMapper.selectList(new LambdaQueryWrapper<UnionProposalEntity>()
                        .ge(UnionProposalEntity::getCreateTime, from)
                        .lt(UnionProposalEntity::getCreateTime, to))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionProposalEntity> filter(UnionProposalQuery query) {
        LambdaQueryWrapper<UnionProposalEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(UnionProposalEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(UnionProposalEntity::getProposalNo, keyword)
                    .or().like(UnionProposalEntity::getTitle, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(UnionProposalEntity::getStatus,
                    query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (query.getMineUserId() != null) {
            wrapper.eq(UnionProposalEntity::getSubmitterUserId, query.getMineUserId());
        }
        return wrapper.orderByDesc(UnionProposalEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionProposal toDomain(UnionProposalEntity entity) {
        UnionProposal proposal = new UnionProposal();
        proposal.setId(entity.getId());
        proposal.setProposalNo(entity.getProposalNo());
        proposal.setTitle(entity.getTitle());
        proposal.setContent(entity.getContent());
        proposal.setSubmitterUserId(entity.getSubmitterUserId());
        proposal.setSubmitterName(entity.getSubmitterName());
        proposal.setStatus(ProposalStatus.parse(entity.getStatus()));
        proposal.setHandler(entity.getHandler());
        proposal.setFiledAt(entity.getFiledAt());
        proposal.setHandledAt(entity.getHandledAt());
        proposal.setFeedback(entity.getFeedback());
        proposal.setFeedbackAt(entity.getFeedbackAt());
        proposal.setRating(entity.getRating());
        proposal.setRatedAt(entity.getRatedAt());
        proposal.setCreateTime(entity.getCreateTime());
        proposal.setUpdateTime(entity.getUpdateTime());
        return proposal;
    }

    /**
     * 领域模型转实体。
     *
     * @param proposal 领域模型
     * @return 实体
     */
    private UnionProposalEntity toEntity(UnionProposal proposal) {
        UnionProposalEntity entity = new UnionProposalEntity();
        entity.setId(proposal.getId());
        entity.setProposalNo(proposal.getProposalNo());
        entity.setTitle(proposal.getTitle());
        entity.setContent(proposal.getContent());
        entity.setSubmitterUserId(proposal.getSubmitterUserId());
        entity.setSubmitterName(proposal.getSubmitterName());
        entity.setStatus(proposal.getStatus() == null
                ? ProposalStatus.SUBMITTED.name() : proposal.getStatus().name());
        entity.setHandler(proposal.getHandler());
        entity.setFiledAt(proposal.getFiledAt());
        entity.setHandledAt(proposal.getHandledAt());
        entity.setFeedback(proposal.getFeedback());
        entity.setFeedbackAt(proposal.getFeedbackAt());
        entity.setRating(proposal.getRating());
        entity.setRatedAt(proposal.getRatedAt());
        return entity;
    }
}
