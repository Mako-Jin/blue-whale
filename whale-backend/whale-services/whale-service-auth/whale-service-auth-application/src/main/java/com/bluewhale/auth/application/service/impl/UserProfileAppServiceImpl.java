package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveUserProfileCommand;
import com.bluewhale.auth.application.model.vo.UserProfileVO;
import com.bluewhale.auth.application.port.SensitiveFieldCipher;
import com.bluewhale.auth.application.service.UserProfileAppService;
import com.bluewhale.auth.domain.aggregate.UserProfileAggregate;
import com.bluewhale.auth.domain.event.UserProfileDomainEvent;
import com.bluewhale.auth.domain.model.entity.UserCredential;
import com.bluewhale.auth.domain.model.entity.UserEducation;
import com.bluewhale.auth.domain.model.entity.UserEmergencyContact;
import com.bluewhale.auth.domain.model.entity.UserEmployment;
import com.bluewhale.auth.domain.model.entity.UserProfileContact;
import com.bluewhale.auth.domain.model.entity.UserSecretQuestion;
import com.bluewhale.auth.domain.repository.UserProfileRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户身份档案应用服务实现（权限中心扩展 M3）。
 *
 * <p>明文敏感字段（证件号 / 密保答案）在本层经 {@link SensitiveFieldCipher} 加密后
 * 进入聚合；领域不变量（主联系方式 / 主证件同类型唯一）由聚合根强制；
 * 事务包裹「聚合加载 → 整体替换 → 保存」。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserProfileAppServiceImpl implements UserProfileAppService {

    private final UserProfileRepository userProfileRepository;
    private final SensitiveFieldCipher sensitiveFieldCipher;
    private final DomainEventPublisher domainEventPublisher;
    private final com.bluewhale.auth.application.port.CurrentUserProvider currentUserProvider;

    /**
     * 保存档案（无档案则建，有则更新并整体重建子表）
     *
     * @param command 保存命令
     * @return 脱敏视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO save(SaveUserProfileCommand command) {
        if (command.userId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        Long tenantId = currentUserProvider.current().tokenTenantId();
        UserProfileAggregate profile = userProfileRepository.findByUserId(command.userId())
                .orElseGet(() -> UserProfileAggregate.create(tenantId, command.userId()));
        profile.updateBasic(command.gender(), command.birthDate(), command.birthPlace(), command.homeAddress(),
                command.nation(), command.politicalStatus(), command.maritalStatus(), command.remarks());
        profile.replaceContacts(toContacts(command.contacts()));
        profile.replaceCredentials(toCredentials(command.credentials()));
        profile.replaceEducations(toEducations(command.educations()));
        profile.replaceEmployments(toEmployments(command.employments()));
        profile.replaceEmergencyContacts(toEmergencyContacts(command.emergencyContacts()));
        profile.replaceSecretQuestions(toSecretQuestions(command.secretQuestions()));
        UserProfileAggregate saved = userProfileRepository.save(profile);
        domainEventPublisher.publish(new UserProfileDomainEvent(saved.getId(), saved.getTenantId(),
                saved.getUserId(), com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED,
                System.currentTimeMillis()));
        return UserProfileVO.from(saved, sensitiveFieldCipher);
    }

    /**
     * 按用户查询档案（脱敏视图）
     *
     * @param userId 用户 ID
     * @return 脱敏视图
     */
    @Override
    public UserProfileVO getByUserId(Long userId) {
        UserProfileAggregate profile = require(userId);
        return UserProfileVO.from(profile, sensitiveFieldCipher);
    }

    /**
     * 查看证件号明文（专用权限）
     *
     * @param userId 用户 ID
     * @param credentialId 证件条目主键
     * @return 证件号明文
     */
    @Override
    public String getCredentialPlain(Long userId, Long credentialId) {
        UserProfileAggregate profile = require(userId);
        return profile.getCredentials().stream()
                .filter(c -> credentialId != null && credentialId.equals(c.getId()))
                .findFirst()
                .map(c -> sensitiveFieldCipher.decrypt(c.getCredentialNoEncrypted()))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "证件条目不存在"));
    }

    /**
     * 标记联系方式已验证
     *
     * @param userId 用户 ID
     * @param contactId 联系方式条目主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markContactVerified(Long userId, Long contactId) {
        UserProfileAggregate profile = require(userId);
        profile.markContactVerified(contactId);
        UserProfileAggregate saved = userProfileRepository.save(profile);
        domainEventPublisher.publish(new UserProfileDomainEvent(saved.getId(), saved.getTenantId(),
                saved.getUserId(), com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED,
                System.currentTimeMillis()));
    }

    /**
     * 删除档案（逻辑删除主表并清理子表）
     *
     * @param userId 用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserId(Long userId) {
        UserProfileAggregate profile = require(userId);
        userProfileRepository.deleteByUserId(userId);
        domainEventPublisher.publish(new UserProfileDomainEvent(profile.getId(), profile.getTenantId(),
                profile.getUserId(), com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.DELETED,
                System.currentTimeMillis()));
    }

    /**
     * 按用户主键加载身份档案聚合根，不存在时抛业务异常。
     *
     * @param userId 用户主键
     * @return 身份档案聚合根
     */
    private UserProfileAggregate require(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户尚未建立身份档案"));
    }

    /**
     * 把联系方式入参转换为领域对象；排序号缺省为 0。
     *
     * @param items 联系方式入参，允许为空
     * @return 联系方式领域对象列表
     */
    private List<UserProfileContact> toContacts(List<SaveUserProfileCommand.ContactItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserProfileContact.create(i.contactType(), i.contactValue(),
                        i.primary(), i.verified(), i.sortNo() == null ? 0 : i.sortNo()))
                .toList();
    }

    /**
     * 把证件入参转换为领域对象，证件号在落库前加密。
     *
     * @param items 证件入参，允许为空
     * @return 证件领域对象列表
     */
    private List<UserCredential> toCredentials(List<SaveUserProfileCommand.CredentialItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserCredential.create(i.credentialType(),
                        sensitiveFieldCipher.encrypt(i.credentialNo()), i.holderName(),
                        i.primary(), i.issueDate(), i.expiryDate()))
                .toList();
    }

    /**
     * 把教育经历入参转换为领域对象。
     *
     * @param items 教育经历入参，允许为空
     * @return 教育经历领域对象列表
     */
    private List<UserEducation> toEducations(List<SaveUserProfileCommand.EducationItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserEducation.create(i.school(), i.major(), i.degree(),
                        i.startDate(), i.endDate(), i.sortNo()))
                .toList();
    }

    /**
     * 把工作经历入参转换为领域对象。
     *
     * @param items 工作经历入参，允许为空
     * @return 工作经历领域对象列表
     */
    private List<UserEmployment> toEmployments(List<SaveUserProfileCommand.EmploymentItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserEmployment.create(i.company(), i.department(), i.position(),
                        i.employmentType(), i.startDate(), i.endDate(), i.sortNo()))
                .toList();
    }

    /**
     * 把紧急联系人入参转换为领域对象。
     *
     * @param items 紧急联系人入参，允许为空
     * @return 紧急联系人领域对象列表
     */
    private List<UserEmergencyContact> toEmergencyContacts(List<SaveUserProfileCommand.EmergencyContactItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserEmergencyContact.create(i.contactName(), i.relationship(), i.phone(), i.sortNo()))
                .toList();
    }

    /**
     * 把密保问题入参转换为领域对象，答案在落库前加密。
     *
     * @param items 密保问题入参，允许为空
     * @return 密保问题领域对象列表
     */
    private List<UserSecretQuestion> toSecretQuestions(List<SaveUserProfileCommand.SecretQuestionItem> items) {
        return items == null ? List.of() : items.stream()
                .map(i -> UserSecretQuestion.create(i.question(),
                        sensitiveFieldCipher.encrypt(i.answer()), i.sortNo()))
                .toList();
    }
}
