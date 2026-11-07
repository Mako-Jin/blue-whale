package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.customer.domain.model.CustomerProfile;
import com.bluewhale.crm.customer.domain.model.DecisionChainEntry;
import com.bluewhale.crm.customer.domain.repository.CustomerProfileRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerProfileEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerProfileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 客户画像仓储实现（决策链以 JSON 存储）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerProfileRepositoryImpl implements CustomerProfileRepository {

    private final CustomerProfileMapper profileMapper;

    private final ObjectMapper objectMapper;

    @Override
    public Optional<CustomerProfile> findByCustomerId(Long customerId) {
        LambdaQueryWrapper<CustomerProfileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerProfileEntity::getCustomerId, customerId);
        return Optional.ofNullable(profileMapper.selectOne(wrapper)).map(this::toDomain);
    }

    @Override
    public void save(CustomerProfile profile) {
        Optional<CustomerProfileEntity> existing = findByEntity(profile.getCustomerId());
        CustomerProfileEntity entity = existing.orElseGet(CustomerProfileEntity::new);
        entity.setCustomerId(profile.getCustomerId());
        entity.setPreference(profile.getPreference());
        entity.setPainPoints(profile.getPainPoints());
        entity.setDecisionChain(writeChain(profile.getDecisionChain()));
        entity.setKeyPerson(profile.getKeyPerson());
        entity.setSensitiveRemark(profile.getSensitiveRemark());
        entity.setVersion(profile.getVersion() == null ? 0 : profile.getVersion());
        if (entity.getId() == null) {
            profileMapper.insert(entity);
        } else {
            profileMapper.updateById(entity);
        }
        profile.setId(entity.getId());
    }

    @Override
    public int reassignCustomer(Long fromCustomerId, Long toCustomerId) {
        LambdaUpdateWrapper<CustomerProfileEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerProfileEntity::getCustomerId, fromCustomerId)
                .set(CustomerProfileEntity::getCustomerId, toCustomerId);
        return profileMapper.update(null, wrapper);
    }

    /**
     * 查询画像实体。
     *
     * @param customerId 客户 ID
     * @return 画像实体
     */
    private Optional<CustomerProfileEntity> findByEntity(Long customerId) {
        LambdaQueryWrapper<CustomerProfileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerProfileEntity::getCustomerId, customerId);
        return Optional.ofNullable(profileMapper.selectOne(wrapper));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CustomerProfile toDomain(CustomerProfileEntity entity) {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(entity.getId());
        profile.setCustomerId(entity.getCustomerId());
        profile.setPreference(entity.getPreference());
        profile.setPainPoints(entity.getPainPoints());
        profile.setDecisionChain(readChain(entity.getDecisionChain()));
        profile.setKeyPerson(entity.getKeyPerson());
        profile.setSensitiveRemark(entity.getSensitiveRemark());
        profile.setVersion(entity.getVersion());
        return profile;
    }

    /**
     * 反序列化决策链。
     *
     * @param json JSON 文本
     * @return 决策链
     */
    private List<DecisionChainEntry> readChain(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DecisionChainEntry>>() {
            });
        } catch (JsonProcessingException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PROFILE_INVALID, "客户画像决策链数据损坏");
        }
    }

    /**
     * 序列化决策链。
     *
     * @param chain 决策链
     * @return JSON 文本；空链返回 null
     */
    private String writeChain(List<DecisionChainEntry> chain) {
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(chain);
        } catch (JsonProcessingException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PROFILE_INVALID, "客户画像决策链无法序列化");
        }
    }
}
