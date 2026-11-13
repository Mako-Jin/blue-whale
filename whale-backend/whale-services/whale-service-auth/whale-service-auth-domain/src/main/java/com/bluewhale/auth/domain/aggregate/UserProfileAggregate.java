package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.entity.UserCredential;
import com.bluewhale.auth.domain.model.entity.UserEducation;
import com.bluewhale.auth.domain.model.entity.UserEmergencyContact;
import com.bluewhale.auth.domain.model.entity.UserEmployment;
import com.bluewhale.auth.domain.model.entity.UserProfileContact;
import com.bluewhale.auth.domain.model.entity.UserSecretQuestion;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户身份档案聚合根（对应 {@code sys_user_profile} 及 6 张子表，权限中心扩展 M3）。
 *
 * <p><b>不变量</b>：① {@code userId} 全局唯一（由仓储前置检查 + DB 唯一约束兜底）；
 * ② 证件号、密保答案必须以密文传入（应用层 {@code SensitiveFieldCipher} 加密，实体校验非空）；
 * ③ 主联系方式 / 主证件在同一类型下唯一；④ 教育、工作、紧急联系人支持多条有序；
 * ⑤ 子表只允许通过聚合根整体替换，不提供单条子表 CRUD 绕过不变量。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class UserProfileAggregate implements Entity<Long> {

    /** 单类子实体数量上限（防止误传超大集合导致关联表爆炸） */
    public static final int MAX_CHILDREN_PER_TYPE = 50;

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private String gender;
    private LocalDate birthDate;
    private String birthPlace;
    private String homeAddress;
    private String nation;
    private String politicalStatus;
    private String maritalStatus;
    private String remarks;
    private List<UserProfileContact> contacts = new ArrayList<>();
    private List<UserCredential> credentials = new ArrayList<>();
    private List<UserEducation> educations = new ArrayList<>();
    private List<UserEmployment> employments = new ArrayList<>();
    private List<UserEmergencyContact> emergencyContacts = new ArrayList<>();
    private List<UserSecretQuestion> secretQuestions = new ArrayList<>();
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param userId 用户 ID（全局唯一）
     * @param gender 性别（可空）
     * @param birthDate 出生日期（可空）
     * @param birthPlace 籍贯（可空）
     * @param homeAddress 家庭住址（可空）
     * @param nation 民族（可空）
     * @param politicalStatus 政治面貌（可空）
     * @param maritalStatus 婚姻状况（可空）
     * @param remarks 备注（可空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private UserProfileAggregate(Long id, Long tenantId, Long userId, String gender, LocalDate birthDate,
                                 String birthPlace, String homeAddress, String nation, String politicalStatus,
                                 String maritalStatus, String remarks, LocalDateTime createTime,
                                 LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "档案必须归属租户");
        }
        if (userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "档案必须归属用户");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.gender = gender;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.homeAddress = homeAddress;
        this.nation = nation;
        this.politicalStatus = politicalStatus;
        this.maritalStatus = maritalStatus;
        this.remarks = remarks;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建档案
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 新建档案聚合
     */
    public static UserProfileAggregate create(Long tenantId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return new UserProfileAggregate(null, tenantId, userId, null, null, null, null, null, null, null,
                null, now, now);
    }

    /**
     * 由持久化数据重建聚合（子集合由仓储装配后回填）
     *
     * @param id 主键
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
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 档案聚合
     */
    public static UserProfileAggregate reconstitute(Long id, Long tenantId, Long userId, String gender,
                                                    LocalDate birthDate, String birthPlace, String homeAddress,
                                                    String nation, String politicalStatus, String maritalStatus,
                                                    String remarks, LocalDateTime createTime,
                                                    LocalDateTime updateTime) {
        return new UserProfileAggregate(id, tenantId, userId, gender, birthDate, birthPlace, homeAddress,
                nation, politicalStatus, maritalStatus, remarks, createTime, updateTime);
    }

    /**
     * 行为：更新基本信息
     *
     * @param gender 性别（可空）
     * @param birthDate 出生日期（可空）
     * @param birthPlace 籍贯（可空）
     * @param homeAddress 家庭住址（可空）
     * @param nation 民族（可空）
     * @param politicalStatus 政治面貌（可空）
     * @param maritalStatus 婚姻状况（可空）
     * @param remarks 备注（可空）
     */
    public void updateBasic(String gender, LocalDate birthDate, String birthPlace, String homeAddress,
                            String nation, String politicalStatus, String maritalStatus, String remarks) {
        this.gender = gender;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.homeAddress = homeAddress;
        this.nation = nation;
        this.politicalStatus = politicalStatus;
        this.maritalStatus = maritalStatus;
        this.remarks = remarks;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：整体替换联系方式列表（不变量：同一联系方式类型下主联系方式唯一）
     *
     * @param contacts 目标联系方式列表（允许空列表表示清空）
     * @return 归一化后的联系方式列表（作为仓储整体替换的落库入参）
     * @throws DomainException 列表为 {@code null}、超量或同类型主联系方式重复时抛出
     */
    public List<UserProfileContact> replaceContacts(List<UserProfileContact> contacts) {
        requireNotNull(contacts, "联系方式列表");
        requireSizeLimit(contacts, "联系方式");
        requirePrimaryUnique(contacts.stream()
                .filter(UserProfileContact::isPrimary)
                .map(UserProfileContact::getContactType)
                .toList(), "主联系方式");
        this.contacts = new ArrayList<>(contacts);
        this.updateTime = LocalDateTime.now();
        return this.contacts;
    }

    /**
     * 行为：整体替换证件列表（不变量：同一证件类型下主证件唯一）
     *
     * @param credentials 目标证件列表（允许空列表表示清空）
     * @return 归一化后的证件列表（作为仓储整体替换的落库入参）
     * @throws DomainException 列表为 {@code null}、超量或同类型主证件重复时抛出
     */
    public List<UserCredential> replaceCredentials(List<UserCredential> credentials) {
        requireNotNull(credentials, "证件列表");
        requireSizeLimit(credentials, "证件");
        requirePrimaryUnique(credentials.stream()
                .filter(UserCredential::isPrimary)
                .map(UserCredential::getCredentialType)
                .toList(), "主证件");
        this.credentials = new ArrayList<>(credentials);
        this.updateTime = LocalDateTime.now();
        return this.credentials;
    }

    /**
     * 行为：整体替换教育经历列表
     *
     * @param educations 目标教育经历列表（允许空列表表示清空）
     * @return 归一化后的教育经历列表
     * @throws DomainException 列表为 {@code null} 或超量时抛出
     */
    public List<UserEducation> replaceEducations(List<UserEducation> educations) {
        requireNotNull(educations, "教育经历列表");
        requireSizeLimit(educations, "教育经历");
        this.educations = new ArrayList<>(educations);
        this.updateTime = LocalDateTime.now();
        return this.educations;
    }

    /**
     * 行为：整体替换工作经历列表
     *
     * @param employments 目标工作经历列表（允许空列表表示清空）
     * @return 归一化后的工作经历列表
     * @throws DomainException 列表为 {@code null} 或超量时抛出
     */
    public List<UserEmployment> replaceEmployments(List<UserEmployment> employments) {
        requireNotNull(employments, "工作经历列表");
        requireSizeLimit(employments, "工作经历");
        this.employments = new ArrayList<>(employments);
        this.updateTime = LocalDateTime.now();
        return this.employments;
    }

    /**
     * 行为：整体替换紧急联系人列表
     *
     * @param emergencyContacts 目标紧急联系人列表（允许空列表表示清空）
     * @return 归一化后的紧急联系人列表
     * @throws DomainException 列表为 {@code null} 或超量时抛出
     */
    public List<UserEmergencyContact> replaceEmergencyContacts(List<UserEmergencyContact> emergencyContacts) {
        requireNotNull(emergencyContacts, "紧急联系人列表");
        requireSizeLimit(emergencyContacts, "紧急联系人");
        this.emergencyContacts = new ArrayList<>(emergencyContacts);
        this.updateTime = LocalDateTime.now();
        return this.emergencyContacts;
    }

    /**
     * 行为：整体替换密保问题列表
     *
     * @param secretQuestions 目标密保问题列表（允许空列表表示清空）
     * @return 归一化后的密保问题列表
     * @throws DomainException 列表为 {@code null} 或超量时抛出
     */
    public List<UserSecretQuestion> replaceSecretQuestions(List<UserSecretQuestion> secretQuestions) {
        requireNotNull(secretQuestions, "密保问题列表");
        requireSizeLimit(secretQuestions, "密保问题");
        this.secretQuestions = new ArrayList<>(secretQuestions);
        this.updateTime = LocalDateTime.now();
        return this.secretQuestions;
    }

    /**
     * 行为：标记联系方式为已验证
     *
     * @param contactId 联系方式主键
     * @throws DomainException 联系方式不存在时抛出
     */
    public void markContactVerified(Long contactId) {
        UserProfileContact target = contacts.stream()
                .filter(c -> contactId != null && contactId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.PARAM_ERROR, "联系方式不存在"));
        target.markVerified();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 校验列表非空
     *
     * @param list 待校验列表
     * @param name 列表业务名称（用于异常文案）
     */
    private void requireNotNull(List<?> list, String name) {
        if (list == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, name + "不能为 null");
        }
    }

    /**
     * 校验列表不超过单类数量上限
     *
     * @param list 待校验列表
     * @param name 列表业务名称（用于异常文案）
     */
    private void requireSizeLimit(List<?> list, String name) {
        if (list.size() > MAX_CHILDREN_PER_TYPE) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    name + "最多 " + MAX_CHILDREN_PER_TYPE + " 条");
        }
    }

    /**
     * 校验「主」标记在同类型下唯一
     *
     * @param primaryTypes 打了主标记的子项类型列表
     * @param name 业务名称（用于异常文案）
     */
    private void requirePrimaryUnique(List<String> primaryTypes, String name) {
        Set<String> seen = new HashSet<>();
        for (String type : primaryTypes) {
            if (!seen.add(type)) {
                throw new DomainException(ErrorCode.PARAM_ERROR, name + "同类型只能设置一条");
            }
        }
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取性别
     *
     * @return 性别编码（可空）
     */
    public String getGender() {
        return gender;
    }

    /**
     * 获取出生日期
     *
     * @return 出生日期（可空）
     */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /**
     * 获取籍贯
     *
     * @return 籍贯（可空）
     */
    public String getBirthPlace() {
        return birthPlace;
    }

    /**
     * 获取家庭住址
     *
     * @return 家庭住址（可空）
     */
    public String getHomeAddress() {
        return homeAddress;
    }

    /**
     * 获取民族
     *
     * @return 民族（可空）
     */
    public String getNation() {
        return nation;
    }

    /**
     * 获取政治面貌
     *
     * @return 政治面貌（可空）
     */
    public String getPoliticalStatus() {
        return politicalStatus;
    }

    /**
     * 获取婚姻状况
     *
     * @return 婚姻状况编码（可空）
     */
    public String getMaritalStatus() {
        return maritalStatus;
    }

    /**
     * 获取备注
     *
     * @return 备注（可空）
     */
    public String getRemarks() {
        return remarks;
    }

    /**
     * 获取联系方式列表
     *
     * @return 联系方式列表（只读视图）
     */
    public List<UserProfileContact> getContacts() {
        return List.copyOf(contacts);
    }

    /**
     * 获取证件列表
     *
     * @return 证件列表（只读视图）
     */
    public List<UserCredential> getCredentials() {
        return List.copyOf(credentials);
    }

    /**
     * 获取教育经历列表
     *
     * @return 教育经历列表（只读视图）
     */
    public List<UserEducation> getEducations() {
        return List.copyOf(educations);
    }

    /**
     * 获取工作经历列表
     *
     * @return 工作经历列表（只读视图）
     */
    public List<UserEmployment> getEmployments() {
        return List.copyOf(employments);
    }

    /**
     * 获取紧急联系人列表
     *
     * @return 紧急联系人列表（只读视图）
     */
    public List<UserEmergencyContact> getEmergencyContacts() {
        return List.copyOf(emergencyContacts);
    }

    /**
     * 获取密保问题列表
     *
     * @return 密保问题列表（只读视图）
     */
    public List<UserSecretQuestion> getSecretQuestions() {
        return List.copyOf(secretQuestions);
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 判断是否属于指定用户
     *
     * @param targetUserId 目标用户 ID
     * @return 属于返回 {@code true}
     */
    public boolean belongsTo(Long targetUserId) {
        return userId != null && userId.equals(targetUserId);
    }

    /**
     * 判断与另一档案是否同一聚合（用户维度）
     *
     * @param other 另一档案聚合
     * @return 同一用户返回 {@code true}
     */
    public boolean sameUser(UserProfileAggregate other) {
        return other != null && Objects.equals(userId, other.getUserId());
    }
}
