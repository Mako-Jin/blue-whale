package com.bluewhale.party.member.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 党员档案（一人一档）。
 *
 * <p>{@link #employeeNo} 存 ERP 员工编号的字符串快照，不建外键、不复制人事表：
 * 党政工会只需要「这个人是哪个单位的员工」这一条事实，工号对应的岗位、薪酬、
 * 合同都在 ERP，复制过来只会产生两份互相打架的人事数据。</p>
 *
 * <p>{@link #duesBase} 是党费计算基数（月收入口径）。调整基数<b>不影响已生成的账单</b>：
 * 账单在生成时把基数与比例都写成快照，否则调薪会把去年已缴清的账单改成欠缴。</p>
 *
 * @author BlueWhale
 */
@Data
public class PartyMember {

    /** 姓名最大长度 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 电话最大长度 */
    public static final int PHONE_MAX_LENGTH = 32;

    /** 党费基数保留小数位（与表列 DECIMAL(12,2) 一致） */
    public static final int DUES_BASE_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 党员编号（租户内唯一） */
    private String memberNo;

    /** 员工编号（引用 ERP，一人一档） */
    private String employeeNo;

    /** 平台用户 ID（未开通账号为空） */
    private Long userId;

    /** 党员姓名 */
    private String memberName;

    /** 所属支部 ID */
    private Long branchId;

    /** 政治面貌 */
    private PoliticalStatus politicalStatus;

    /** 入党日期（党龄起算） */
    private LocalDate joinPartyDate;

    /** 转正日期（预备党员为空） */
    private LocalDate fullMemberDate;

    /** 党费计算基数（月收入口径） */
    private BigDecimal duesBase;

    /** 联系电话 */
    private String phone;

    /** 状态 */
    private MemberStatus status;

    /** 组织关系转出日期 */
    private LocalDate transferOutDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验档案自身的字段边界与日期先后关系。
     *
     * <p>「员工是否已在别处建档」「支部是否存在且在册」需要查库，由应用服务判定。</p>
     */
    public void validate() {
        validateIdentity();
        validatePartyInfo();
        validateDues();
        if (status == null) {
            status = MemberStatus.ACTIVE;
        }
    }

    /**
     * 校验身份字段（编号、工号、姓名、联系方式、备注）。
     */
    private void validateIdentity() {
        if (memberNo == null || memberNo.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "党员编号不能为空");
        }
        if (employeeNo == null || employeeNo.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "员工编号不能为空");
        }
        if (memberName == null || memberName.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "党员姓名不能为空");
        }
        if (memberName.trim().length() > NAME_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID,
                    "党员姓名不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (phone != null && phone.trim().length() > PHONE_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID,
                    "联系电话不得超过 " + PHONE_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        memberNo = memberNo.trim().toUpperCase(java.util.Locale.ROOT);
        employeeNo = employeeNo.trim();
        memberName = memberName.trim();
    }

    /**
     * 校验党内信息（支部、入党与转正日期、政治面貌）。
     */
    private void validatePartyInfo() {
        if (branchId == null || branchId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "党员必须归属于一个支部");
        }
        if (joinPartyDate == null) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "入党日期不能为空");
        }
        if (fullMemberDate != null && fullMemberDate.isBefore(joinPartyDate)) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID, "转正日期不能早于入党日期");
        }
        if (politicalStatus == null) {
            politicalStatus = PoliticalStatus.FULL_MEMBER;
        }
        if (politicalStatus.isProbationary() && fullMemberDate != null) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_INVALID,
                    "预备党员不应有转正日期，请先办理转正");
        }
    }

    /**
     * 校验党费基数（非负，空值按 0 处理）。
     */
    private void validateDues() {
        if (duesBase == null) {
            duesBase = BigDecimal.ZERO;
        }
        if (duesBase.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "党费基数不能为负");
        }
        if (duesBase.scale() > DUES_BASE_SCALE) {
            duesBase = duesBase.setScale(DUES_BASE_SCALE, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * 是否在册。
     *
     * @return 在册返回 true
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }

    /**
     * 是否待转正（预备党员且尚未填转正日期）。
     *
     * @return 待转正返回 true
     */
    public boolean isProbationaryPending() {
        return politicalStatus != null && politicalStatus.isProbationary() && fullMemberDate == null;
    }

    /**
     * 计算党龄（整年）。
     *
     * <p>党龄自<b>入党日期</b>起算，不是转正日期——预备期计入党龄是党内统计的既有口径，
     * 按转正日期算会把所有预备党员都算成 0 年。</p>
     *
     * @param asOf 计算基准日
     * @return 党龄年数；入党日期为空返回 0
     */
    public long partyAgeYears(LocalDate asOf) {
        if (joinPartyDate == null) {
            return 0L;
        }
        LocalDate base = asOf == null ? LocalDate.now() : asOf;
        if (base.isBefore(joinPartyDate)) {
            return 0L;
        }
        return ChronoUnit.YEARS.between(joinPartyDate, base);
    }

    /**
     * 断言档案可写（已转出为只读终态）。
     */
    public void assertWritable() {
        if (status != null && status.isReadOnly()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_STATE_CONFLICT,
                    "组织关系已转出，档案只读");
        }
    }

    /**
     * 办理组织关系转出。
     *
     * <p>转出是终态：转出后档案仍保留用于追溯历史记录，但不可再改可写字段。
     * 若员工再次转入本单位，应<b>新建</b>一份档案而不是复活旧档案——
     * 两段党龄之间的断档在党内统计里是两次不同的组织关系。</p>
     *
     * @param transferDate 转出日期
     */
    public void transferOut(LocalDate transferDate) {
        assertWritable();
        if (!isActive()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_STATE_CONFLICT,
                    "仅在册党员可以办理组织关系转出");
        }
        LocalDate date = transferDate == null ? LocalDate.now() : transferDate;
        if (date.isBefore(joinPartyDate)) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_STATE_CONFLICT, "转出日期不能早于入党日期");
        }
        this.status = MemberStatus.TRANSFERRED;
        this.transferOutDate = date;
    }

    /**
     * 办理预备党员转正。
     *
     * @param fullDate 转正日期
     */
    public void promoteToFull(LocalDate fullDate) {
        assertWritable();
        if (politicalStatus == null || !politicalStatus.isProbationary()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_STATE_CONFLICT, "该党员已是正式党员");
        }
        LocalDate date = fullDate == null ? LocalDate.now() : fullDate;
        if (date.isBefore(joinPartyDate)) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_STATE_CONFLICT, "转正日期不能早于入党日期");
        }
        this.politicalStatus = PoliticalStatus.FULL_MEMBER;
        this.fullMemberDate = date;
    }
}
