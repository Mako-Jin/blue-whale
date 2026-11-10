package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.bluewhale.mall.catalog.domain.model.MallMember;
import com.bluewhale.mall.catalog.domain.model.MemberStatus;
import com.bluewhale.mall.catalog.domain.repository.MemberRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.MallMemberEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.MallMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商城会员仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MallMemberMapper mallMemberMapper;

    @Override
    public List<MallMember> page(String keyword, String status, long offset, int size) {
        return mallMemberMapper.selectList(buildWrapper(keyword, status)
                        .last("LIMIT " + Math.max(offset, 0) + "," + Math.max(size, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(String keyword, String status) {
        return mallMemberMapper.selectCount(buildWrapper(keyword, status));
    }

    @Override
    public MallMember findById(Long id) {
        if (id == null) {
            return null;
        }
        MallMemberEntity entity = mallMemberMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public MallMember findByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        MallMemberEntity entity = mallMemberMapper.selectOne(new LambdaQueryWrapper<MallMemberEntity>()
                .eq(MallMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public Long save(MallMember member) {
        MallMemberEntity entity = toEntity(member);
        if (entity.getId() == null) {
            MallMemberEntity existing = mallMemberMapper.selectOne(new LambdaQueryWrapper<MallMemberEntity>()
                    .eq(MallMemberEntity::getUserId, member.getUserId())
                    .last("LIMIT 1"));
            if (existing != null) {
                entity.setId(existing.getId());
            }
        }
        if (entity.getId() == null) {
            mallMemberMapper.insert(entity);
        } else {
            mallMemberMapper.updateById(entity);
        }
        member.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void delete(Long id) {
        mallMemberMapper.deleteById(id);
    }

    /**
     * 组装查询条件。
     *
     * @param keyword 会员号/用户 ID（可空）
     * @param status  状态（可空）
     * @return 查询条件
     */
    private LambdaQueryWrapper<MallMemberEntity> buildWrapper(String keyword, String status) {
        LambdaQueryWrapper<MallMemberEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.isNotBlank(keyword), w -> w
                        .like(MallMemberEntity::getMemberNo, keyword)
                        .or().eq(MallMemberEntity::getUserId, parseUserId(keyword)))
                .eq(StringUtils.isNotBlank(status), MallMemberEntity::getStatus, status)
                .orderByDesc(MallMemberEntity::getId);
        return wrapper;
    }

    /**
     * 关键字解析为用户 ID（非数字时用不可能的 -1，让条件自然落空）。
     *
     * @param keyword 关键字
     * @return 用户 ID
     */
    private Long parseUserId(String keyword) {
        try {
            return Long.valueOf(keyword.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MallMember toDomain(MallMemberEntity entity) {
        MallMember member = new MallMember();
        member.setId(entity.getId());
        member.setUserId(entity.getUserId());
        member.setMemberNo(entity.getMemberNo());
        member.setLevelCode(entity.getLevelCode());
        member.setValidTo(entity.getValidTo());
        member.setStatus(MemberStatus.parse(entity.getStatus()));
        member.setRemark(entity.getRemark());
        member.setCreateTime(entity.getCreateTime());
        member.setUpdateTime(entity.getUpdateTime());
        return member;
    }

    /**
     * 领域模型转实体。
     *
     * @param member 领域模型
     * @return 实体
     */
    private MallMemberEntity toEntity(MallMember member) {
        MallMemberEntity entity = new MallMemberEntity();
        entity.setId(member.getId());
        entity.setUserId(member.getUserId());
        entity.setMemberNo(member.getMemberNo());
        entity.setLevelCode(member.getLevelCode());
        entity.setValidTo(member.getValidTo());
        entity.setStatus(member.getStatus() == null
                ? MemberStatus.ACTIVE.name() : member.getStatus().name());
        entity.setRemark(member.getRemark());
        return entity;
    }
}
