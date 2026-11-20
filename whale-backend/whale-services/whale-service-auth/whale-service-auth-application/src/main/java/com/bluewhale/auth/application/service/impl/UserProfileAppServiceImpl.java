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
            throw new BizException(ErrorCode.PARAM_ERROR, "auth.user.id-required", "用户 ID 不能为空");
        }
        Long tenantId = currentUserProvider.current().tokenTenantId();
        UserProfileAggregate profile = userProfileRepository.findByUserId(command.userId())
                .orElseGet(() -> UserProfileAggregate.create(tenantId, command.userId()));
        profile.updateBasic(command.gender(), command.birthDate(), command.birthPlace(), command.homeAddress(),
                command.nation(), command.politicalStatus(), command.maritalStatus(), command.remarks());
        profile.replaceContacts(toContacts(command.contacts(), profile.getContacts()));
        profile.replaceCredentials(toCredentials(command.credentials(), profile.getCredentials()));
        profile.replaceEducations(toEducations(command.educations()));
        profile.replaceEmployments(toEmployments(command.employments()));
        profile.replaceEmergencyContacts(
                toEmergencyContacts(command.emergencyContacts(), profile.getEmergencyContacts()));
        if (command.secretQuestions() == null) {
            // 密保答案脱敏视图永不回传，管理端无法回填明文：入参为 null（未提供）时保留既有问答，
            // 防止整体替换把已有密保静默清空；显式空列表仍表示清空（用户自助场景可重录）
        } else {
            profile.replaceSecretQuestions(toSecretQuestions(command.secretQuestions()));
        }
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
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "auth.user.identity-doc-not-found", "证件条目不存在"));
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
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "auth.user.profile-missing", "用户尚未建立身份档案"));
    }

    /**
     * 把联系方式入参转换为领域对象；排序号缺省为 0。
     *
     * <p>脱敏口径：读端 {@code contactValue} 为掩码，管理端无法回填明文——条目携带 id 且
     * 值留空时保留既有值（清空请整行删除），避免掩码覆盖明文。</p>
     *
     * @param items 联系方式入参，允许为空
     * @param existing 既有联系方式列表（按 id 回填保留值）
     * @return 联系方式领域对象列表
     */
    private List<UserProfileContact> toContacts(List<SaveUserProfileCommand.ContactItem> items,
                                                List<UserProfileContact> existing) {
        return items == null ? List.of() : items.stream()
                .map(i -> {
                    if (i.id() == null && (i.contactValue() == null || i.contactValue().isBlank())) {
                        // 新增行必须给明文；带 id 的行留空才表示「保留原值」（DTO 层已放宽 @NotBlank）
                        throw new BizException(ErrorCode.PARAM_ERROR, "auth.user.contact-value-required", "联系方式值不能为空");
                    }
                    String value = resolveSensitive(i.id(), i.contactValue(), existing,
                            UserProfileContact::getId, UserProfileContact::getContactValue);
                    return UserProfileContact.create(i.contactType(), value,
                            i.primary(), i.verified(), i.sortNo() == null ? 0 : i.sortNo());
                })
                .toList();
    }

    /**
     * 把证件入参转换为领域对象，证件号在落库前加密。
     *
     * <p>脱敏口径：条目携带 id 且证件号留空时保留既有密文（清空请整行删除）。</p>
     *
     * @param items 证件入参，允许为空
     * @param existing 既有证件列表（按 id 回填保留值）
     * @return 证件领域对象列表
     */
    private List<UserCredential> toCredentials(List<SaveUserProfileCommand.CredentialItem> items,
                                               List<UserCredential> existing) {
        return items == null ? List.of() : items.stream()
                .map(i -> {
                    if (i.id() == null && (i.credentialNo() == null || i.credentialNo().isBlank())) {
                        // 新增行必须给明文；带 id 的行留空才表示「保留既有密文」
                        throw new BizException(ErrorCode.PARAM_ERROR, "auth.user.id-number-required", "证件号不能为空");
                    }
                    String encryptedNo = resolveSensitive(i.id(), i.credentialNo(), existing,
                            UserCredential::getId, UserCredential::getCredentialNoEncrypted,
                            sensitiveFieldCipher::encrypt);
                    return UserCredential.create(i.credentialType(),
                            encryptedNo, i.holderName(), i.primary(), i.issueDate(), i.expiryDate());
                })
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
     * <p>脱敏口径：条目携带 id 且电话留空时保留既有电话（清空请整行删除）。</p>
     *
     * @param items 紧急联系人入参，允许为空
     * @param existing 既有紧急联系人列表（按 id 回填保留值）
     * @return 紧急联系人领域对象列表
     */
    private List<UserEmergencyContact> toEmergencyContacts(List<SaveUserProfileCommand.EmergencyContactItem> items,
                                                           List<UserEmergencyContact> existing) {
        return items == null ? List.of() : items.stream()
                .map(i -> {
                    String phone = resolveSensitive(i.id(), i.phone(), existing,
                            UserEmergencyContact::getId, UserEmergencyContact::phone);
                    return UserEmergencyContact.create(i.contactName(), i.relationship(), phone, i.sortNo());
                })
                .toList();
    }

    /**
     * 敏感字段保留值解析：条目携带 id 且明文留空时回填既有值，否则用新值。
     *
     * @param id 条目 id（null 表示新增行，直接用新值）
     * @param newValue 新明文值（可空）
     * @param existing 既有条目列表
     * @param idGetter 既有条目 id 访问器
     * @param valueGetter 既有条目值访问器
     * @param <E> 既有条目类型
     * @return 新值非空用新值；新值留空且命中既有条目用既有值；否则原样返回（null）
     */
    private <E> String resolveSensitive(Long id, String newValue, List<E> existing,
                                        java.util.function.Function<E, Long> idGetter,
                                        java.util.function.Function<E, String> valueGetter) {
        return resolveSensitive(id, newValue, existing, idGetter, valueGetter, String::toString);
    }

    /**
     * 敏感字段保留值解析（带新值变换）：同上，但新值先经 {@code encryptor} 变换（如加密）。
     */
    private <E> String resolveSensitive(Long id, String newValue, List<E> existing,
                                        java.util.function.Function<E, Long> idGetter,
                                        java.util.function.Function<E, String> valueGetter,
                                        java.util.function.Function<String, String> encryptor) {
        if (newValue != null && !newValue.isBlank()) {
            return encryptor.apply(newValue);
        }
        if (id == null) {
            return newValue;
        }
        return existing.stream()
                .filter(e -> id.equals(idGetter.apply(e)))
                .findFirst()
                .map(valueGetter)
                .orElse(newValue);
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
