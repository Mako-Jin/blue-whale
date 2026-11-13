package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.UserProfileAggregate;
import com.bluewhale.auth.domain.model.entity.UserCredential;
import com.bluewhale.auth.domain.model.entity.UserEducation;
import com.bluewhale.auth.domain.model.entity.UserEmergencyContact;
import com.bluewhale.auth.domain.model.entity.UserEmployment;
import com.bluewhale.auth.domain.model.entity.UserProfileContact;
import com.bluewhale.auth.domain.model.entity.UserSecretQuestion;
import com.bluewhale.auth.domain.repository.UserProfileRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserProfilePersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserContactMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserCredentialMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserEducationMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserEmergencyContactMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserEmploymentMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserProfileMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserSecretQuestionMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserContactPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserCredentialPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEducationPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEmergencyContactPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEmploymentPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserProfilePo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSecretQuestionPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户身份档案仓储 MyBatis-Plus 实现（权限中心扩展 M3）。
 *
 * <p>主表逻辑删除由 {@code deleted} 列承担（MyBatis-Plus 全局配置）；6 张子表有逻辑删除列
 * 但聚合口径为整体替换，采用「按 userId 物理删后批量插」避免历史残留与重复主联系方式；
 * 物理删除走 Mapper 上的自定义 SQL（{@code @InterceptorIgnore} 绕过租户行级插件，
 * {@code userId} 全局唯一，跨租户清理是删除语义的一部分）。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final SysUserProfileMapper profileMapper;
    private final SysUserContactMapper contactMapper;
    private final SysUserCredentialMapper credentialMapper;
    private final SysUserEducationMapper educationMapper;
    private final SysUserEmploymentMapper employmentMapper;
    private final SysUserEmergencyContactMapper emergencyContactMapper;
    private final SysUserSecretQuestionMapper secretQuestionMapper;

    /**
     * 级联保存档案聚合：主表无 ID 则新增（回填主键）否则更新，子表 6 类全部先物理删后批量插
     *
     * <p>先删后插要求调用方已开启事务（MANDATORY 强制，应用层保存用例内开启）。</p>
     *
     * @param profile 档案聚合
     * @return 回填主键后的聚合
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UserProfileAggregate save(UserProfileAggregate profile) {
        SysUserProfilePo po = UserProfilePersistenceAssembler.toPo(profile);
        if (po.getId() == null) {
            profileMapper.insert(po);
        } else {
            profileMapper.updateById(po);
        }
        replaceChildren(profile);
        return UserProfilePersistenceAssembler.toDomain(po);
    }

    /**
     * 按用户 ID 查询档案（主表 + 全部子集合，逻辑删除行自动过滤）
     *
     * @param userId 用户 ID
     * @return 档案聚合；不存在时返回空
     */
    @Override
    public Optional<UserProfileAggregate> findByUserId(Long userId) {
        SysUserProfilePo po = profileMapper.selectOne(new LambdaQueryWrapper<SysUserProfilePo>()
                .eq(SysUserProfilePo::getUserId, userId));
        if (po == null) {
            return Optional.empty();
        }
        UserProfileAggregate aggregate = UserProfilePersistenceAssembler.toDomain(po);
        aggregate.replaceContacts(loadContacts(aggregate));
        aggregate.replaceCredentials(loadCredentials(aggregate));
        aggregate.replaceEducations(loadEducations(aggregate));
        aggregate.replaceEmployments(loadEmployments(aggregate));
        aggregate.replaceEmergencyContacts(loadEmergencyContacts(aggregate));
        aggregate.replaceSecretQuestions(loadSecretQuestions(aggregate));
        return Optional.of(aggregate);
    }

    /**
     * 判断用户是否已建档案
     *
     * @param userId 用户 ID
     * @return 已建返回 true
     */
    @Override
    public boolean existsByUserId(Long userId) {
        Long count = profileMapper.selectCount(new LambdaQueryWrapper<SysUserProfilePo>()
                .eq(SysUserProfilePo::getUserId, userId));
        return count != null && count > 0;
    }

    /**
     * 删除档案：主表逻辑删除，子表物理清理
     *
     * @param userId 用户 ID
     */
    @Override
    public void deleteByUserId(Long userId) {
        profileMapper.delete(new LambdaQueryWrapper<SysUserProfilePo>()
                .eq(SysUserProfilePo::getUserId, userId));
        contactMapper.deleteByUserId(userId);
        credentialMapper.deleteByUserId(userId);
        educationMapper.deleteByUserId(userId);
        employmentMapper.deleteByUserId(userId);
        emergencyContactMapper.deleteByUserId(userId);
        secretQuestionMapper.deleteByUserId(userId);
    }

    /**
     * 整体替换 6 类子表数据（先按 userId 物理删，再按聚合内集合逐条插入）
     *
     * @param profile 档案聚合
     */
    private void replaceChildren(UserProfileAggregate profile) {
        contactMapper.deleteByUserId(profile.getUserId());
        for (UserProfileContact entity : profile.getContacts()) {
            contactMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
        credentialMapper.deleteByUserId(profile.getUserId());
        for (UserCredential entity : profile.getCredentials()) {
            credentialMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
        educationMapper.deleteByUserId(profile.getUserId());
        for (UserEducation entity : profile.getEducations()) {
            educationMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
        employmentMapper.deleteByUserId(profile.getUserId());
        for (UserEmployment entity : profile.getEmployments()) {
            employmentMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
        emergencyContactMapper.deleteByUserId(profile.getUserId());
        for (UserEmergencyContact entity : profile.getEmergencyContacts()) {
            emergencyContactMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
        secretQuestionMapper.deleteByUserId(profile.getUserId());
        for (UserSecretQuestion entity : profile.getSecretQuestions()) {
            secretQuestionMapper.insert(UserProfilePersistenceAssembler.toPo(profile, entity));
        }
    }

    /**
     * 加载用户全部联系方式
     *
     * @param aggregate 所属档案聚合
     * @return 联系方式实体列表
     */
    private List<UserProfileContact> loadContacts(UserProfileAggregate aggregate) {
        List<SysUserContactPo> pos = contactMapper.selectList(new LambdaQueryWrapper<SysUserContactPo>()
                .eq(SysUserContactPo::getUserId, aggregate.getUserId())
                .orderByAsc(SysUserContactPo::getSortNo));
        return pos.stream().map(UserProfilePersistenceAssembler::toContact).toList();
    }

    /**
     * 加载用户全部证件
     *
     * @param aggregate 所属档案聚合
     * @return 证件实体列表
     */
    private List<UserCredential> loadCredentials(UserProfileAggregate aggregate) {
        List<SysUserCredentialPo> pos = credentialMapper.selectList(new LambdaQueryWrapper<SysUserCredentialPo>()
                .eq(SysUserCredentialPo::getUserId, aggregate.getUserId())
                .orderByAsc(SysUserCredentialPo::getId));
        return pos.stream().map(UserProfilePersistenceAssembler::toCredential).toList();
    }

    /**
     * 加载用户全部教育经历
     *
     * @param aggregate 所属档案聚合
     * @return 教育经历实体列表
     */
    private List<UserEducation> loadEducations(UserProfileAggregate aggregate) {
        List<SysUserEducationPo> pos = educationMapper.selectList(new LambdaQueryWrapper<SysUserEducationPo>()
                .eq(SysUserEducationPo::getUserId, aggregate.getUserId())
                .orderByAsc(SysUserEducationPo::getSortNo));
        return pos.stream().map(UserProfilePersistenceAssembler::toEducation).toList();
    }

    /**
     * 加载用户全部工作经历
     *
     * @param aggregate 所属档案聚合
     * @return 工作经历实体列表
     */
    private List<UserEmployment> loadEmployments(UserProfileAggregate aggregate) {
        List<SysUserEmploymentPo> pos = employmentMapper.selectList(new LambdaQueryWrapper<SysUserEmploymentPo>()
                .eq(SysUserEmploymentPo::getUserId, aggregate.getUserId())
                .orderByAsc(SysUserEmploymentPo::getSortNo));
        return pos.stream().map(UserProfilePersistenceAssembler::toEmployment).toList();
    }

    /**
     * 加载用户全部紧急联系人
     *
     * @param aggregate 所属档案聚合
     * @return 紧急联系人实体列表
     */
    private List<UserEmergencyContact> loadEmergencyContacts(UserProfileAggregate aggregate) {
        List<SysUserEmergencyContactPo> pos = emergencyContactMapper
                .selectList(new LambdaQueryWrapper<SysUserEmergencyContactPo>()
                        .eq(SysUserEmergencyContactPo::getUserId, aggregate.getUserId())
                        .orderByAsc(SysUserEmergencyContactPo::getSortNo));
        return pos.stream().map(UserProfilePersistenceAssembler::toEmergencyContact).toList();
    }

    /**
     * 加载用户全部密保问题
     *
     * @param aggregate 所属档案聚合
     * @return 密保问题实体列表
     */
    private List<UserSecretQuestion> loadSecretQuestions(UserProfileAggregate aggregate) {
        List<SysUserSecretQuestionPo> pos = secretQuestionMapper
                .selectList(new LambdaQueryWrapper<SysUserSecretQuestionPo>()
                        .eq(SysUserSecretQuestionPo::getUserId, aggregate.getUserId())
                        .orderByAsc(SysUserSecretQuestionPo::getSortNo));
        return pos.stream().map(UserProfilePersistenceAssembler::toSecretQuestion).toList();
    }
}
