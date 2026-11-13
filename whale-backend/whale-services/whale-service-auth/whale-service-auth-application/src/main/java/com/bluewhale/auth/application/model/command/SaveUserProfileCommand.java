package com.bluewhale.auth.application.model.command;

import java.time.LocalDate;
import java.util.List;

/**
 * 保存用户身份档案命令（权限中心扩展 M3，整体替换口径）。
 *
 * <p>子集合整体替换：传入即为目标状态（空列表表示清空该类子表）；
 * 证件号与密保答案以<b>明文</b>传入，由应用服务经 {@code SensitiveFieldCipher} 加密后进入聚合。</p>
 *
 * @param userId 用户 ID（必填）
 * @param gender 性别（可空）
 * @param birthDate 出生日期（可空）
 * @param birthPlace 籍贯（可空）
 * @param homeAddress 家庭住址（可空）
 * @param nation 民族（可空）
 * @param politicalStatus 政治面貌（可空）
 * @param maritalStatus 婚姻状况（可空）
 * @param remarks 备注（可空）
 * @param contacts 联系方式列表（可空列表）
 * @param credentials 证件列表（可空列表）
 * @param educations 教育经历列表（可空列表）
 * @param employments 工作经历列表（可空列表）
 * @param emergencyContacts 紧急联系人列表（可空列表）
 * @param secretQuestions 密保问题列表（可空列表）
 * @author BlueWhale
 */
public record SaveUserProfileCommand(Long userId, String gender, LocalDate birthDate, String birthPlace,
                                     String homeAddress, String nation, String politicalStatus,
                                     String maritalStatus, String remarks,
                                     List<ContactItem> contacts, List<CredentialItem> credentials,
                                     List<EducationItem> educations, List<EmploymentItem> employments,
                                     List<EmergencyContactItem> emergencyContacts,
                                     List<SecretQuestionItem> secretQuestions) {

    /**
     * 联系方式条目
     *
     * @param id 主键（仅更新场景回传）
     * @param contactType 类型（MOBILE / EMAIL / WECHAT / DINGTALK / OTHER）
     * @param contactValue 值
     * @param primary 是否主联系方式
     * @param verified 是否已验证
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record ContactItem(Long id, String contactType, String contactValue, boolean primary,
                              boolean verified, Integer sortNo) {
    }

    /**
     * 证件条目（证件号明文，应用服务加密）
     *
     * @param id 主键（仅更新场景回传）
     * @param credentialType 类型（ID_CARD / PASSPORT / HK_MO_PASS / OTHER）
     * @param credentialNo 证件号明文
     * @param holderName 持有人姓名（可空）
     * @param primary 是否主证件
     * @param issueDate 签发日期（可空）
     * @param expiryDate 有效期至（可空）
     * @author BlueWhale
     */
    public record CredentialItem(Long id, String credentialType, String credentialNo, String holderName,
                                 boolean primary, LocalDate issueDate, LocalDate expiryDate) {
    }

    /**
     * 教育经历条目
     *
     * @param id 主键（仅更新场景回传）
     * @param school 学校名称
     * @param major 专业（可空）
     * @param degree 学历编码（可空）
     * @param startDate 入学时间（可空）
     * @param endDate 毕业时间（可空）
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EducationItem(Long id, String school, String major, String degree,
                                LocalDate startDate, LocalDate endDate, Integer sortNo) {
    }

    /**
     * 工作经历条目
     *
     * @param id 主键（仅更新场景回传）
     * @param company 公司名称
     * @param department 部门（可空）
     * @param position 职位（可空）
     * @param employmentType 用工性质（可空）
     * @param startDate 入职时间（可空）
     * @param endDate 离职时间（可空）
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EmploymentItem(Long id, String company, String department, String position,
                                 String employmentType, LocalDate startDate, LocalDate endDate,
                                 Integer sortNo) {
    }

    /**
     * 紧急联系人条目
     *
     * @param id 主键（仅更新场景回传）
     * @param contactName 姓名
     * @param relationship 关系（可空）
     * @param phone 电话（可空）
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record EmergencyContactItem(Long id, String contactName, String relationship, String phone,
                                       Integer sortNo) {
    }

    /**
     * 密保问题条目（答案明文，应用服务加密）
     *
     * @param id 主键（仅更新场景回传）
     * @param question 问题
     * @param answer 答案明文
     * @param sortNo 排序号
     * @author BlueWhale
     */
    public record SecretQuestionItem(Long id, String question, String answer, Integer sortNo) {
    }
}
