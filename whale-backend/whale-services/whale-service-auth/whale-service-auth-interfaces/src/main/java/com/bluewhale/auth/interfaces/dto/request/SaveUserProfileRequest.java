package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户身份档案保存请求（权限中心扩展 M3，整体替换口径）。
 *
 * <p>子集合传入即为目标状态（空列表表示清空该类子表）；证件号与密保答案为<b>明文</b>入参，
 * 加密在 application 层完成，接口层不感知密文。</p>
 *
 * @author BlueWhale
 */
@Data
public class SaveUserProfileRequest {

    /** 性别（可空） */
    @Size(max = 16, message = "{validation.auth.user.profile.gender-size}")
    private String gender;

    /** 出生日期（可空） */
    private LocalDate birthDate;

    /** 籍贯（可空） */
    @Size(max = 128, message = "{validation.auth.user.profile.native-place-size}")
    private String birthPlace;

    /** 家庭住址（可空） */
    @Size(max = 512, message = "{validation.auth.user.profile.address-size}")
    private String homeAddress;

    /** 民族（可空） */
    @Size(max = 32, message = "{validation.auth.user.profile.ethnicity-size}")
    private String nation;

    /** 政治面貌（可空） */
    @Size(max = 32, message = "{validation.auth.user.profile.political-status-size}")
    private String politicalStatus;

    /** 婚姻状况（可空） */
    @Size(max = 32, message = "{validation.auth.user.profile.marital-status-size}")
    private String maritalStatus;

    /** 备注（可空） */
    @Size(max = 1024, message = "{validation.auth.user.profile.remark-size}")
    private String remarks;

    /** 联系方式列表（可空列表） */
    private List<@Valid ContactItem> contacts;

    /** 证件列表（可空列表，证件号明文） */
    private List<@Valid CredentialItem> credentials;

    /** 教育经历列表（可空列表） */
    private List<@Valid EducationItem> educations;

    /** 工作经历列表（可空列表） */
    private List<@Valid EmploymentItem> employments;

    /** 紧急联系人列表（可空列表） */
    private List<@Valid EmergencyContactItem> emergencyContacts;

    /** 密保问题列表（可空列表，答案明文） */
    private List<@Valid SecretQuestionItem> secretQuestions;

    /**
     * 联系方式条目
     *
     * @author BlueWhale
     */
    @Data
    public static class ContactItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 类型（MOBILE / EMAIL / WECHAT / DINGTALK / OTHER） */
        @NotBlank(message = "{validation.auth.user.profile.contact-type-required}")
        @Size(max = 32, message = "{validation.auth.user.profile.contact-type-size}")
        private String contactType;

        /** 值（带 id 且留空时表示「保留原值」；新增行必填由应用服务校验） */
        @Size(max = 255, message = "{validation.auth.user.profile.contact-value-size}")
        private String contactValue;

        /** 是否主联系方式 */
        private boolean primary;

        /** 是否已验证 */
        private boolean verified;

        /** 排序号 */
        @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
        private Integer sortNo;
    }

    /**
     * 证件条目（证件号明文，应用服务加密）
     *
     * @author BlueWhale
     */
    @Data
    public static class CredentialItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 类型（ID_CARD / PASSPORT / HK_MO_PASS / OTHER） */
        @NotBlank(message = "{validation.auth.user.profile.id-type-required}")
        @Size(max = 32, message = "{validation.auth.user.profile.id-type-size}")
        private String credentialType;

        /** 证件号明文（带 id 且留空时表示「保留原值」；新增行必填由应用服务校验） */
        @Size(max = 64, message = "{validation.auth.user.profile.id-number-size}")
        private String credentialNo;

        /** 持有人姓名（可空） */
        @Size(max = 64, message = "{validation.auth.user.profile.holder-name-size}")
        private String holderName;

        /** 是否主证件 */
        private boolean primary;

        /** 签发日期（可空） */
        private LocalDate issueDate;

        /** 有效期至（可空） */
        private LocalDate expiryDate;
    }

    /**
     * 教育经历条目
     *
     * @author BlueWhale
     */
    @Data
    public static class EducationItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 学校名称 */
        @NotBlank(message = "{validation.auth.user.profile.school-required}")
        @Size(max = 128, message = "{validation.auth.user.profile.school-size}")
        private String school;

        /** 专业（可空） */
        @Size(max = 128, message = "{validation.auth.user.profile.major-size}")
        private String major;

        /** 学历编码（可空） */
        @Size(max = 32, message = "{validation.auth.user.profile.education-size}")
        private String degree;

        /** 入学时间（可空） */
        private LocalDate startDate;

        /** 毕业时间（可空） */
        private LocalDate endDate;

        /** 排序号 */
        @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
        private Integer sortNo;
    }

    /**
     * 工作经历条目
     *
     * @author BlueWhale
     */
    @Data
    public static class EmploymentItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 公司名称 */
        @NotBlank(message = "{validation.auth.user.profile.company-required}")
        @Size(max = 128, message = "{validation.auth.user.profile.company-size}")
        private String company;

        /** 部门（可空） */
        @Size(max = 128, message = "{validation.auth.user.profile.department-size}")
        private String department;

        /** 职位（可空） */
        @Size(max = 128, message = "{validation.auth.user.profile.position-size}")
        private String position;

        /** 用工性质（可空） */
        @Size(max = 32, message = "{validation.auth.user.profile.employment-type-size}")
        private String employmentType;

        /** 入职时间（可空） */
        private LocalDate startDate;

        /** 离职时间（可空） */
        private LocalDate endDate;

        /** 排序号 */
        @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
        private Integer sortNo;
    }

    /**
     * 紧急联系人条目
     *
     * @author BlueWhale
     */
    @Data
    public static class EmergencyContactItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 姓名 */
        @NotBlank(message = "{validation.auth.user.profile.emergency-contact-required}")
        @Size(max = 64, message = "{validation.auth.user.profile.emergency-contact-size}")
        private String contactName;

        /** 关系（可空） */
        @Size(max = 32, message = "{validation.auth.user.profile.emergency-relation-size}")
        private String relationship;

        /** 电话（可空） */
        @Size(max = 32, message = "{validation.auth.user.profile.emergency-phone-size}")
        private String phone;

        /** 排序号 */
        @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
        private Integer sortNo;
    }

    /**
     * 密保问题条目（答案明文，应用服务加密）
     *
     * @author BlueWhale
     */
    @Data
    public static class SecretQuestionItem {

        /** 主键（仅更新场景回传） */
        private Long id;

        /** 问题 */
        @NotBlank(message = "{validation.auth.user.profile.security-question-required}")
        @Size(max = 255, message = "{validation.auth.user.profile.security-question-size}")
        private String question;

        /** 答案明文 */
        @NotBlank(message = "{validation.auth.user.profile.security-answer-required}")
        @Size(max = 255, message = "{validation.auth.user.profile.security-answer-size}")
        private String answer;

        /** 排序号 */
        @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
        private Integer sortNo;
    }
}
