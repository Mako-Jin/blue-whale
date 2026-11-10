package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.scene.domain.model.UserPreference;
import com.bluewhale.scene.domain.repository.UserPreferenceRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.UserPreferenceEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户场景偏好仓储实现（MyBatis-Plus）。
 *
 * <p>租户条件由 starter 的租户行插件统一追加，本类不做租户判断。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserPreferenceRepositoryImpl implements UserPreferenceRepository {

    private final UserPreferenceMapper userPreferenceMapper;

    @Override
    public Optional<UserPreference> findByUserId(Long userId) {
        return Optional.ofNullable(userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreferenceEntity>()
                .eq(UserPreferenceEntity::getUserId, userId))).map(this::toDomain);
    }

    @Override
    public void save(UserPreference preference) {
        UserPreferenceEntity entity = new UserPreferenceEntity();
        entity.setId(preference.getId());
        entity.setUserId(preference.getUserId());
        entity.setViewMode(preference.getViewMode());
        entity.setDefaultSceneKey(preference.getDefaultSceneKey());
        if (preference.getId() == null) {
            userPreferenceMapper.insert(entity);
            preference.setId(entity.getId());
        } else {
            userPreferenceMapper.updateById(entity);
        }
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private UserPreference toDomain(UserPreferenceEntity entity) {
        UserPreference preference = new UserPreference();
        preference.setId(entity.getId());
        preference.setUserId(entity.getUserId());
        preference.setViewMode(entity.getViewMode());
        preference.setDefaultSceneKey(entity.getDefaultSceneKey());
        return preference;
    }
}
