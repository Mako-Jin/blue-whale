package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户身份档案响应（权限中心扩展 M3，<b>脱敏口径</b>）。
 *
 * <p>证件号 / 电话 / 联系方式为掩码值；密保答案永不回传；证件号明文须另持专用权限调用
 * {@code GET /user-profile/v1/{userId}/credentials/{credentialId}}。</p>
 *
 * @author BlueWhale
 */
@Data
public class UserProfileResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private String gender;
    private LocalDate birthDate;
    private String birthPlace;
    private String homeAddress;
    private String nation;
    private String politicalStatus;
    private String maritalStatus;
    private String remarks;
    private List<ContactItem> contacts;
    private List<CredentialItem> credentials;
    private List<EducationItem> educations;
    private List<EmploymentItem> employments;
    private List<EmergencyContactItem> emergencyContacts;
    private List<SecretQuestionItem> secretQuestions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 联系方式条目（值脱敏）
     *
     * @author BlueWhale
     */
    @Data
    public static class ContactItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String contactType;
        private String contactValue;
        private boolean primary;
        private boolean verified;
        private Integer sortNo;
    }

    /**
     * 证件条目（证件号脱敏）
     *
     * @author BlueWhale
     */
    @Data
    public static class CredentialItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String credentialType;
        private String credentialNoMasked;
        private String holderName;
        private boolean primary;
        private LocalDate issueDate;
        private LocalDate expiryDate;
    }

    /**
     * 教育经历条目
     *
     * @author BlueWhale
     */
    @Data
    public static class EducationItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String school;
        private String major;
        private String degree;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer sortNo;
    }

    /**
     * 工作经历条目
     *
     * @author BlueWhale
     */
    @Data
    public static class EmploymentItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String company;
        private String department;
        private String position;
        private String employmentType;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer sortNo;
    }

    /**
     * 紧急联系人条目（电话脱敏）
     *
     * @author BlueWhale
     */
    @Data
    public static class EmergencyContactItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String contactName;
        private String relationship;
        private String phoneMasked;
        private Integer sortNo;
    }

    /**
     * 密保问题条目（不含答案）
     *
     * @author BlueWhale
     */
    @Data
    public static class SecretQuestionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String question;
        private Integer sortNo;
    }
}
