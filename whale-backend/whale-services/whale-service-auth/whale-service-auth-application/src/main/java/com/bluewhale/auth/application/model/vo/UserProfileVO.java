package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.application.port.SensitiveFieldCipher;
import com.bluewhale.auth.domain.aggregate.UserProfileAggregate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户身份档案视图对象（权限中心扩展 M3，<b>接口脱敏口径</b>）。
 *
 * <p>联系方式全量返回但按需脱敏；证件号仅返回掩码 + 「可查看明文」标记（明文需另持
 * {@code sys:user:profile:credential-view} 权限调用专用端点）；密保答案永不回传，仅返回
 * 问题与条目主键。</p>
 *
 * @param id 档案主键
 * @param tenantId 租户 ID
 * @param userId 用户 ID
 * @param gender 性别
 * @param birthDate 出生日期
 * @param birthPlace 籍贯
 * @param homeAddress 家庭住址
 * @param nation 民族
 * @param politicalStatus 政治面貌
 * @param maritalStatus 婚姻状况
 * @param remarks 备注
 * @param contacts 联系方式列表（值脱敏）
 * @param credentials 证件列表（号脱敏）
 * @param educations 教育经历列表
 * @param employments 工作经历列表
 * @param emergencyContacts 紧急联系人列表（电话脱敏）
 * @param secretQuestions 密保问题列表（不含答案）
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserProfileVO(Long id, Long tenantId, Long userId, String gender, LocalDate birthDate,
                            String birthPlace, String homeAddress, String nation, String politicalStatus,
                            String maritalStatus, String remarks,
                            List<ContactItem> contacts, List<CredentialItem> credentials,
                            List<EducationItem> educations, List<EmploymentItem> employments,
                            List<EmergencyContactItem> emergencyContacts, List<SecretQuestionItem> secretQuestions,
                            LocalDateTime createTime, LocalDateTime updateTime) {

    /**
     * 联系方式条目（值脱敏）
     *
     * @param id 主键
     * @param contactType 类型
     * @param contactValue 脱敏后的值
     * @param primary 是否主联系方式
     * @param verified 是否已验证
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record ContactItem(Long id, String contactType, String contactValue, boolean primary,
                              boolean verified, Integer sortNo) {
    }

    /**
     * 证件条目（证件号脱敏）
     *
     * @param id 主键
     * @param credentialType 类型
     * @param credentialNoMasked 脱敏后的证件号
     * @param holderName 持有人姓名
     * @param primary 是否主证件
     * @param issueDate 签发日期
     * @param expiryDate 有效期至
     * @author BlueWhale
     */
    public record CredentialItem(Long id, String credentialType, String credentialNoMasked,
                                 String holderName, boolean primary, LocalDate issueDate, LocalDate expiryDate) {
    }

    /**
     * 教育经历条目
     *
     * @param id 主键
     * @param school 学校
     * @param major 专业
     * @param degree 学历
     * @param startDate 入学时间
     * @param endDate 毕业时间
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EducationItem(Long id, String school, String major, String degree,
                                LocalDate startDate, LocalDate endDate, Integer sortNo) {
    }

    /**
     * 工作经历条目
     *
     * @param id 主键
     * @param company 公司
     * @param department 部门
     * @param position 职位
     * @param employmentType 用工性质
     * @param startDate 入职时间
     * @param endDate 离职时间
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EmploymentItem(Long id, String company, String department, String position,
                                 String employmentType, LocalDate startDate, LocalDate endDate, Integer sortNo) {
    }

    /**
     * 紧急联系人条目（电话脱敏）
     *
     * @param id 主键
     * @param contactName 姓名
     * @param relationship 关系
     * @param phoneMasked 脱敏后的电话
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EmergencyContactItem(Long id, String contactName, String relationship,
                                       String phoneMasked, Integer sortNo) {
    }

    /**
     * 密保问题条目（不含答案）
     *
     * @param id 主键
     * @param question 问题
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record SecretQuestionItem(Long id, String question, Integer sortNo) {
    }

    /**
     * 由聚合装配视图（脱敏）
     *
     * @param profile 档案聚合（子集合已装配）
     * @param cipher 敏感字段解密端口（证件号解密后掩码展示）
     * @return 脱敏视图对象
     */
    public static UserProfileVO from(UserProfileAggregate profile, SensitiveFieldCipher cipher) {
        List<ContactItem> contacts = profile.getContacts().stream()
                .map(c -> new ContactItem(c.getId(), c.getContactType(), mask(c.getContactValue()),
                        c.isPrimary(), c.isVerified(), c.getSortNo()))
                .toList();
        List<CredentialItem> credentials = profile.getCredentials().stream()
                .map(c -> new CredentialItem(c.getId(), c.getCredentialType(),
                        mask(cipher.decrypt(c.getCredentialNoEncrypted())),
                        c.getHolderName(), c.isPrimary(), c.getIssueDate(), c.getExpiryDate()))
                .toList();
        List<EducationItem> educations = profile.getEducations().stream()
                .map(e -> new EducationItem(e.getId(), e.school(), e.major(), e.degree(),
                        e.startDate(), e.endDate(), e.sortNo()))
                .toList();
        List<EmploymentItem> employments = profile.getEmployments().stream()
                .map(e -> new EmploymentItem(e.getId(), e.company(), e.department(), e.position(),
                        e.employmentType(), e.startDate(), e.endDate(), e.sortNo()))
                .toList();
        List<EmergencyContactItem> emergencyContacts = profile.getEmergencyContacts().stream()
                .map(e -> new EmergencyContactItem(e.getId(), e.contactName(), e.relationship(),
                        mask(e.phone()), e.sortNo()))
                .toList();
        List<SecretQuestionItem> secretQuestions = profile.getSecretQuestions().stream()
                .map(q -> new SecretQuestionItem(q.getId(), q.question(), q.sortNo()))
                .toList();
        return new UserProfileVO(profile.getId(), profile.getTenantId(), profile.getUserId(),
                profile.getGender(), profile.getBirthDate(), profile.getBirthPlace(), profile.getHomeAddress(),
                profile.getNation(), profile.getPoliticalStatus(), profile.getMaritalStatus(), profile.getRemarks(),
                contacts, credentials, educations, employments, emergencyContacts, secretQuestions,
                profile.getCreateTime(), profile.getUpdateTime());
    }

    /**
     * 通用掩码：长度 ≥7 时保留前 3 后 4，其余以 * 覆盖；空值原样返回
     *
     * @param value 原文（可空）
     * @return 掩码结果
     */
    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return "*".repeat(length);
        }
        if (length < 7) {
            return value.charAt(0) + "*".repeat(length - 2) + value.charAt(length - 1);
        }
        return value.substring(0, 3) + "*".repeat(length - 7) + value.substring(length - 4);
    }
}
