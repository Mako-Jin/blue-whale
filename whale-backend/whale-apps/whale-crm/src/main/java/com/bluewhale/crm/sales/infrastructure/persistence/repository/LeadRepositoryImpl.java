package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.sales.domain.model.Lead;
import com.bluewhale.crm.sales.domain.model.LeadQuery;
import com.bluewhale.crm.sales.domain.model.LeadStatus;
import com.bluewhale.crm.sales.domain.repository.LeadRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.LeadEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.LeadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeadRepositoryImpl implements LeadRepository {

    private final LeadMapper leadMapper;

    @Override
    public List<Lead> page(LeadQuery query) {
        Page<LeadEntity> page = leadMapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(LeadQuery query) {
        return leadMapper.selectCount(buildWrapper(query));
    }

    @Override
    public Lead findById(Long id) {
        LeadEntity entity = leadMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public Lead findByNo(String leadNo) {
        LeadEntity entity = leadMapper.selectOne(new LambdaQueryWrapper<LeadEntity>()
                .eq(LeadEntity::getLeadNo, leadNo)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<Lead> findDuplicates(String normalizedPhone, String normalizedEmail, Long excludeId) {
        boolean hasPhone = normalizedPhone != null && !normalizedPhone.isBlank();
        boolean hasEmail = normalizedEmail != null && !normalizedEmail.isBlank();
        if (!hasPhone && !hasEmail) {
            return List.of();
        }
        LambdaQueryWrapper<LeadEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(excludeId != null, LeadEntity::getId, excludeId);
        wrapper.and(w -> {
            if (hasPhone) {
                w.eq(LeadEntity::getNormalizedPhone, normalizedPhone);
            }
            if (hasEmail) {
                if (hasPhone) {
                    w.or();
                }
                w.eq(LeadEntity::getNormalizedEmail, normalizedEmail);
            }
        });
        return leadMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public String findMaxNo(String prefix) {
        LeadEntity entity = leadMapper.selectOne(new LambdaQueryWrapper<LeadEntity>()
                .likeRight(LeadEntity::getLeadNo, prefix)
                .orderByDesc(LeadEntity::getLeadNo)
                .last("LIMIT 1"));
        return entity == null ? null : entity.getLeadNo();
    }

    @Override
    public void insert(Lead lead) {
        LeadEntity entity = toEntity(lead);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        leadMapper.insert(entity);
        lead.setId(entity.getId());
        lead.setVersion(entity.getVersion());
        lead.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void update(Lead lead) {
        LeadEntity entity = toEntity(lead);
        entity.setId(lead.getId());
        leadMapper.updateById(entity);
    }

    @Override
    public boolean markConverted(Long id, Long customerId, Long opportunityId, LocalDateTime at) {
        int rows = leadMapper.update(null, new LambdaUpdateWrapper<LeadEntity>()
                .eq(LeadEntity::getId, id)
                .ne(LeadEntity::getStatus, LeadStatus.CONVERTED.name())
                .set(LeadEntity::getStatus, LeadStatus.CONVERTED.name())
                .set(LeadEntity::getCustomerId, customerId)
                .set(LeadEntity::getOpportunityId, opportunityId)
                .set(LeadEntity::getConvertedAt, at)
                .setSql("version = version + 1"));
        return rows > 0;
    }

    @Override
    public void updateOwner(Long id, Long ownerId, Long deptId) {
        leadMapper.update(null, new LambdaUpdateWrapper<LeadEntity>()
                .eq(LeadEntity::getId, id)
                .set(LeadEntity::getOwnerId, ownerId)
                .set(LeadEntity::getDeptId, deptId));
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<LeadEntity> buildWrapper(LeadQuery query) {
        LambdaQueryWrapper<LeadEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(LeadEntity::getName, text)
                    .or().like(LeadEntity::getLeadNo, text)
                    .or().like(LeadEntity::getContactName, text));
        }
        wrapper.eq(!isBlank(query.getStatus()), LeadEntity::getStatus, query.getStatus());
        wrapper.eq(!isBlank(query.getSource()), LeadEntity::getSource, query.getSource());
        wrapper.eq(query.getOwnerId() != null, LeadEntity::getOwnerId, query.getOwnerId());
        wrapper.orderByDesc(LeadEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 判断字符串是否为空。
     *
     * @param text 文本
     * @return 空白返回 true
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Lead toDomain(LeadEntity entity) {
        Lead lead = new Lead();
        lead.setId(entity.getId());
        lead.setLeadNo(entity.getLeadNo());
        lead.setName(entity.getName());
        lead.setSource(entity.getSource());
        lead.setIndustry(entity.getIndustry());
        lead.setRegion(entity.getRegion());
        lead.setContactName(entity.getContactName());
        lead.setContactPhone(entity.getContactPhone());
        lead.setContactEmail(entity.getContactEmail());
        lead.setStatus(LeadStatus.from(entity.getStatus()));
        lead.setInvalidReason(entity.getInvalidReason());
        lead.setOwnerId(entity.getOwnerId());
        lead.setDeptId(entity.getDeptId());
        lead.setCustomerId(entity.getCustomerId());
        lead.setOpportunityId(entity.getOpportunityId());
        lead.setConvertedAt(entity.getConvertedAt());
        lead.setNormalizedName(entity.getNormalizedName());
        lead.setNormalizedPhone(entity.getNormalizedPhone());
        lead.setNormalizedEmail(entity.getNormalizedEmail());
        lead.setRemark(entity.getRemark());
        lead.setVersion(entity.getVersion());
        lead.setCreateTime(entity.getCreateTime());
        lead.setUpdateTime(entity.getUpdateTime());
        return lead;
    }

    /**
     * 领域模型转实体。
     *
     * @param lead 领域模型
     * @return 实体
     */
    private LeadEntity toEntity(Lead lead) {
        LeadEntity entity = new LeadEntity();
        entity.setLeadNo(lead.getLeadNo());
        entity.setName(lead.getName());
        entity.setSource(lead.getSource());
        entity.setIndustry(lead.getIndustry());
        entity.setRegion(lead.getRegion());
        entity.setContactName(lead.getContactName());
        entity.setContactPhone(lead.getContactPhone());
        entity.setContactEmail(lead.getContactEmail());
        entity.setStatus(lead.getStatus() == null ? null : lead.getStatus().name());
        entity.setInvalidReason(lead.getInvalidReason());
        entity.setOwnerId(lead.getOwnerId());
        entity.setDeptId(lead.getDeptId());
        entity.setCustomerId(lead.getCustomerId());
        entity.setOpportunityId(lead.getOpportunityId());
        entity.setConvertedAt(lead.getConvertedAt());
        entity.setNormalizedName(lead.getNormalizedName());
        entity.setNormalizedPhone(lead.getNormalizedPhone());
        entity.setNormalizedEmail(lead.getNormalizedEmail());
        entity.setRemark(lead.getRemark());
        entity.setVersion(lead.getVersion());
        return entity;
    }
}
