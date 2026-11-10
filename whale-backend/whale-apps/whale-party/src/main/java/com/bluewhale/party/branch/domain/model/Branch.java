package com.bluewhale.party.branch.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 党支部。
 *
 * <p>{@link #orgCode} 与 ERP 组织编码是<b>弱关联</b>：只用于展示与筛选，
 * 组织架构调整不会自动改写支部归属。支部是党组织单元，不是行政部门的镜像——
 * 一个部门可以拆成两个支部，两个部门也可以合建一个支部。</p>
 *
 * <p>{@link #branchLevel} 由应用层按上级链回填，不用递归 SQL 现算：
 * 层级要参与「深度上限」判定，放数据库里算等于把校验规则埋进 SQL，
 * 换数据库方言就得重写一遍。</p>
 *
 * @author BlueWhale
 */
@Data
public class Branch {

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 32;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 一级支部的上级 ID */
    public static final long ROOT_PARENT_ID = 0L;

    /** 主键 */
    private Long id;

    /** 支部编码（租户内唯一） */
    private String branchCode;

    /** 支部名称 */
    private String branchName;

    /** 上级支部 ID，0 为一级支部 */
    private Long parentId;

    /** 支部层级（1 起） */
    private Integer branchLevel;

    /** 关联 ERP 组织编码（弱关联，可空） */
    private String orgCode;

    /** 支部书记姓名 */
    private String secretaryName;

    /** 支部书记用户 ID（书记未开通账号时为空） */
    private Long secretaryUserId;

    /** 成立日期 */
    private LocalDate establishedDate;

    /** 状态 */
    private BranchStatus status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验支部自身的一致性与字段边界。
     *
     * <p>只管「这一条记录自己讲不讲得通」：编码与名称非空、上级不是自己、
     * 层级在合法区间、备注不超长。「上级是否存在、是否成环、深度是否超限」
     * 需要查库，放在应用服务里做。</p>
     */
    public void validate() {
        if (branchCode == null || branchCode.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INVALID, "支部编码不能为空");
        }
        if (branchCode.trim().length() > CODE_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INVALID,
                    "支部编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (branchName == null || branchName.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INVALID, "支部名称不能为空");
        }
        if (branchName.trim().length() > NAME_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INVALID,
                    "支部名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (parentId == null) {
            parentId = ROOT_PARENT_ID;
        }
        if (parentId < ROOT_PARENT_ID) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE, "上级支部 ID 不合法");
        }
        if (id != null && id.equals(parentId)) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE, "上级支部不能是自身");
        }
        if (branchLevel == null || branchLevel < 1) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE, "支部层级不合法");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        branchCode = branchCode.trim().toUpperCase(java.util.Locale.ROOT);
        branchName = branchName.trim();
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
     * 停用支部。
     *
     * <p>重复停用按幂等处理：状态已是停用时不报错，避免前端连点两次就弹错。
     * 「还有党员或下级支部时不能停用」是跨聚合的约束，由应用服务判定。</p>
     */
    public void suspend() {
        this.status = BranchStatus.SUSPENDED;
    }

    /**
     * 恢复在册。
     */
    public void activate() {
        this.status = BranchStatus.ACTIVE;
    }

    /**
     * 迁移到新的上级支部并重设层级。
     *
     * @param newParentId 新上级支部 ID，0 为一级支部
     * @param newLevel 新层级
     */
    public void moveTo(Long newParentId, int newLevel) {
        this.parentId = newParentId == null ? ROOT_PARENT_ID : newParentId;
        this.branchLevel = newLevel;
    }
}
