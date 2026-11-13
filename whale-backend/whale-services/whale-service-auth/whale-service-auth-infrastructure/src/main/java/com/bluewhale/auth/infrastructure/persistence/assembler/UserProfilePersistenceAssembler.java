package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.UserProfileAggregate;
import com.bluewhale.auth.domain.model.entity.UserCredential;
import com.bluewhale.auth.domain.model.entity.UserEducation;
import com.bluewhale.auth.domain.model.entity.UserEmergencyContact;
import com.bluewhale.auth.domain.model.entity.UserEmployment;
import com.bluewhale.auth.domain.model.entity.UserProfileContact;
import com.bluewhale.auth.domain.model.entity.UserSecretQuestion;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserContactPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserCredentialPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEducationPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEmploymentPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserEmergencyContactPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserProfilePo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSecretQuestionPo;

/**
 * 用户身份档案聚合与持久化对象装配器（权限中心扩展 M3）。
 *
 * <p>主表与 6 张子表的双向转换：子表 PO 的 {@code tenantId}/{@code userId} 以聚合根为准回填，
 * 保证子表归属与聚合一致；boolean 字段与 Integer 列按 0/1 映射。</p>
 *
 * @author BlueWhale
 */
public final class UserProfilePersistenceAssembler {

    /**
     * 工具类禁止实例化
     */
    private UserProfilePersistenceAssembler() {
    }

    /**
     * 聚合根 → 主表持久化对象
     *
     * @param aggregate 档案聚合
     * @return 主表持久化对象
     */
    public static SysUserProfilePo toPo(UserProfileAggregate aggregate) {
        SysUserProfilePo po = new SysUserProfilePo();
        po.setId(aggregate.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setGender(aggregate.getGender());
        po.setBirthDate(aggregate.getBirthDate());
        po.setBirthPlace(aggregate.getBirthPlace());
        po.setHomeAddress(aggregate.getHomeAddress());
        po.setNation(aggregate.getNation());
        po.setPoliticalStatus(aggregate.getPoliticalStatus());
        po.setMaritalStatus(aggregate.getMaritalStatus());
        po.setRemarks(aggregate.getRemarks());
        po.setCreateTime(aggregate.getCreateTime());
        po.setUpdateTime(aggregate.getUpdateTime());
        return po;
    }

    /**
     * 主表持久化对象 → 聚合根（子集合由仓储另行装配回填）
     *
     * @param po 主表持久化对象
     * @return 档案聚合
     */
    public static UserProfileAggregate toDomain(SysUserProfilePo po) {
        return UserProfileAggregate.reconstitute(po.getId(), po.getTenantId(), po.getUserId(),
                po.getGender(), po.getBirthDate(), po.getBirthPlace(), po.getHomeAddress(),
                po.getNation(), po.getPoliticalStatus(), po.getMaritalStatus(), po.getRemarks(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 联系方式实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 联系方式实体
     * @return 联系方式持久化对象
     */
    public static SysUserContactPo toPo(UserProfileAggregate aggregate, UserProfileContact entity) {
        SysUserContactPo po = new SysUserContactPo();
        po.setId(entity.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setContactType(entity.getContactType());
        po.setContactValue(entity.getContactValue());
        po.setIsPrimary(entity.isPrimary() ? 1 : 0);
        po.setVerified(entity.isVerified() ? 1 : 0);
        po.setSortNo(entity.getSortNo());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 联系方式实体
     *
     * @param po 联系方式持久化对象
     * @return 联系方式实体
     */
    public static UserProfileContact toContact(SysUserContactPo po) {
        return UserProfileContact.reconstitute(po.getId(), po.getContactType(), po.getContactValue(),
                po.getIsPrimary() != null && po.getIsPrimary() == 1,
                po.getVerified() != null && po.getVerified() == 1,
                po.getSortNo(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 证件实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 证件实体
     * @return 证件持久化对象
     */
    public static SysUserCredentialPo toPo(UserProfileAggregate aggregate, UserCredential entity) {
        SysUserCredentialPo po = new SysUserCredentialPo();
        po.setId(entity.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setCredentialType(entity.getCredentialType());
        po.setCredentialNo(entity.getCredentialNoEncrypted());
        po.setHolderName(entity.getHolderName());
        po.setIsPrimary(entity.isPrimary() ? 1 : 0);
        po.setIssueDate(entity.getIssueDate());
        po.setExpiryDate(entity.getExpiryDate());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 证件实体
     *
     * @param po 证件持久化对象
     * @return 证件实体
     */
    public static UserCredential toCredential(SysUserCredentialPo po) {
        return UserCredential.reconstitute(po.getId(), po.getCredentialType(), po.getCredentialNo(),
                po.getHolderName(), po.getIsPrimary() != null && po.getIsPrimary() == 1,
                po.getIssueDate(), po.getExpiryDate(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 教育经历实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 教育经历实体
     * @return 教育经历持久化对象
     */
    public static SysUserEducationPo toPo(UserProfileAggregate aggregate, UserEducation entity) {
        SysUserEducationPo po = new SysUserEducationPo();
        po.setId(entity.id());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setSchool(entity.school());
        po.setMajor(entity.major());
        po.setDegree(entity.degree());
        po.setStartDate(entity.startDate());
        po.setEndDate(entity.endDate());
        po.setSortNo(entity.sortNo());
        po.setCreateTime(entity.createTime());
        po.setUpdateTime(entity.updateTime());
        return po;
    }

    /**
     * 持久化对象 → 教育经历实体
     *
     * @param po 教育经历持久化对象
     * @return 教育经历实体
     */
    public static UserEducation toEducation(SysUserEducationPo po) {
        return new UserEducation(po.getId(), po.getSchool(), po.getMajor(), po.getDegree(),
                po.getStartDate(), po.getEndDate(), po.getSortNo(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 工作经历实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 工作经历实体
     * @return 工作经历持久化对象
     */
    public static SysUserEmploymentPo toPo(UserProfileAggregate aggregate, UserEmployment entity) {
        SysUserEmploymentPo po = new SysUserEmploymentPo();
        po.setId(entity.id());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setCompany(entity.company());
        po.setDepartment(entity.department());
        po.setPosition(entity.position());
        po.setEmploymentType(entity.employmentType());
        po.setStartDate(entity.startDate());
        po.setEndDate(entity.endDate());
        po.setSortNo(entity.sortNo());
        po.setCreateTime(entity.createTime());
        po.setUpdateTime(entity.updateTime());
        return po;
    }

    /**
     * 持久化对象 → 工作经历实体
     *
     * @param po 工作经历持久化对象
     * @return 工作经历实体
     */
    public static UserEmployment toEmployment(SysUserEmploymentPo po) {
        return new UserEmployment(po.getId(), po.getCompany(), po.getDepartment(), po.getPosition(),
                po.getEmploymentType(), po.getStartDate(), po.getEndDate(),
                po.getSortNo(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 紧急联系人实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 紧急联系人实体
     * @return 紧急联系人持久化对象
     */
    public static SysUserEmergencyContactPo toPo(UserProfileAggregate aggregate, UserEmergencyContact entity) {
        SysUserEmergencyContactPo po = new SysUserEmergencyContactPo();
        po.setId(entity.id());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setContactName(entity.contactName());
        po.setRelationship(entity.relationship());
        po.setPhone(entity.phone());
        po.setSortNo(entity.sortNo());
        po.setCreateTime(entity.createTime());
        po.setUpdateTime(entity.updateTime());
        return po;
    }

    /**
     * 持久化对象 → 紧急联系人实体
     *
     * @param po 紧急联系人持久化对象
     * @return 紧急联系人实体
     */
    public static UserEmergencyContact toEmergencyContact(SysUserEmergencyContactPo po) {
        return new UserEmergencyContact(po.getId(), po.getContactName(), po.getRelationship(),
                po.getPhone(), po.getSortNo(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 密保问题实体 → 持久化对象（租户/用户以聚合根为准）
     *
     * @param aggregate 所属档案聚合
     * @param entity 密保问题实体
     * @return 密保问题持久化对象
     */
    public static SysUserSecretQuestionPo toPo(UserProfileAggregate aggregate, UserSecretQuestion entity) {
        SysUserSecretQuestionPo po = new SysUserSecretQuestionPo();
        po.setId(entity.id());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setQuestion(entity.question());
        po.setAnswerEncrypted(entity.answerEncrypted());
        po.setSortNo(entity.sortNo());
        po.setCreateTime(entity.createTime());
        po.setUpdateTime(entity.updateTime());
        return po;
    }

    /**
     * 持久化对象 → 密保问题实体
     *
     * @param po 密保问题持久化对象
     * @return 密保问题实体
     */
    public static UserSecretQuestion toSecretQuestion(SysUserSecretQuestionPo po) {
        return new UserSecretQuestion(po.getId(), po.getQuestion(), po.getAnswerEncrypted(),
                po.getSortNo(), po.getCreateTime(), po.getUpdateTime());
    }
}
