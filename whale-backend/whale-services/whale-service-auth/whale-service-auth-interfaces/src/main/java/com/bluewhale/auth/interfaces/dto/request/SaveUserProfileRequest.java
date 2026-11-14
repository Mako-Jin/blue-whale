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
    @Size(max = 16, message = "性别长度不能超过 16")
    private String gender;

    /** 出生日期（可空） */
    private LocalDate birthDate;

    /** 籍贯（可空） */
    @Size(max = 128, message = "籍贯长度不能超过 128")
    private String birthPlace;

    /** 家庭住址（可空） */
    @Size(max = 512, message = "家庭住址长度不能超过 512")
    private String homeAddress;

    /** 民族（可空） */
    @Size(max = 32, message = "民族长度不能超过 32")
    private String nation;

    /** 政治面貌（可空） */
    @Size(max = 32, message = "政治面貌长度不能超过 32")
    private String politicalStatus;

    /** 婚姻状况（可空） */
    @Size(max = 32, message = "婚姻状况长度不能超过 32")
    private String maritalStatus;

    /** 备注（可空） */
    @Size(max = 1024, message = "备注长度不能超过 1024")
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
        @NotBlank(message = "联系方式类型不能为空")
        @Size(max = 32, message = "联系方式类型长度不能超过 32")
        private String contactType;

        /** 值（带 id 且留空时表示「保留原值」；新增行必填由应用服务校验） */
        @Size(max = 255, message = "联系方式值长度不能超过 255")
        private String contactValue;

        /** 是否主联系方式 */
        private boolean primary;

        /** 是否已验证 */
        private boolean verified;

        /** 排序号 */
        @Min(value = 0, message = "排序号不得为负数")
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
        @NotBlank(message = "证件类型不能为空")
        @Size(max = 32, message = "证件类型长度不能超过 32")
        private String credentialType;

        /** 证件号明文（带 id 且留空时表示「保留原值」；新增行必填由应用服务校验） */
        @Size(max = 64, message = "证件号长度不能超过 64")
        private String credentialNo;

        /** 持有人姓名（可空） */
        @Size(max = 64, message = "持有人姓名长度不能超过 64")
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
        @NotBlank(message = "学校名称不能为空")
        @Size(max = 128, message = "学校名称长度不能超过 128")
        private String school;

        /** 专业（可空） */
        @Size(max = 128, message = "专业长度不能超过 128")
        private String major;

        /** 学历编码（可空） */
        @Size(max = 32, message = "学历编码长度不能超过 32")
        private String degree;

        /** 入学时间（可空） */
        private LocalDate startDate;

        /** 毕业时间（可空） */
        private LocalDate endDate;

        /** 排序号 */
        @Min(value = 0, message = "排序号不得为负数")
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
        @NotBlank(message = "公司名称不能为空")
        @Size(max = 128, message = "公司名称长度不能超过 128")
        private String company;

        /** 部门（可空） */
        @Size(max = 128, message = "部门长度不能超过 128")
        private String department;

        /** 职位（可空） */
        @Size(max = 128, message = "职位长度不能超过 128")
        private String position;

        /** 用工性质（可空） */
        @Size(max = 32, message = "用工性质长度不能超过 32")
        private String employmentType;

        /** 入职时间（可空） */
        private LocalDate startDate;

        /** 离职时间（可空） */
        private LocalDate endDate;

        /** 排序号 */
        @Min(value = 0, message = "排序号不得为负数")
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
        @NotBlank(message = "紧急联系人姓名不能为空")
        @Size(max = 64, message = "紧急联系人姓名长度不能超过 64")
        private String contactName;

        /** 关系（可空） */
        @Size(max = 32, message = "关系长度不能超过 32")
        private String relationship;

        /** 电话（可空） */
        @Size(max = 32, message = "电话长度不能超过 32")
        private String phone;

        /** 排序号 */
        @Min(value = 0, message = "排序号不得为负数")
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
        @NotBlank(message = "密保问题不能为空")
        @Size(max = 255, message = "密保问题长度不能超过 255")
        private String question;

        /** 答案明文 */
        @NotBlank(message = "密保答案不能为空")
        @Size(max = 255, message = "密保答案长度不能超过 255")
        private String answer;

        /** 排序号 */
        @Min(value = 0, message = "排序号不得为负数")
        private Integer sortNo;
    }
}
