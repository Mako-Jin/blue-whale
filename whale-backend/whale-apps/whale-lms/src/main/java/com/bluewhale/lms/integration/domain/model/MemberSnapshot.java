package com.bluewhale.lms.integration.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工只读快照。
 *
 * <p>ERP 员工主数据在 LMS 侧的单向副本。同步只写不读回：LMS 的任何业务动作
 * （报名、签到、发证）都不修改快照，快照只在同步时整体覆盖。</p>
 *
 * <p>离职员工<b>保留</b>快照而不是删除：学习记录、证书、学分都要能追溯
 * 「当时是谁」，删掉就成了一堆无主的 user_id。是否需要触达由
 * {@link #isUrgeable()} 判断，与能否追溯是两件事。</p>
 *
 * @author BlueWhale
 */
@Data
public class MemberSnapshot {

    /** 主键 */
    private Long id;

    /** 员工编号（ERP 主键口径） */
    private String employeeNo;

    /** 平台账号 ID（未开通为 null） */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 在职状态 */
    private MemberStatus status = MemberStatus.ACTIVE;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否可被催办：在职且已开通平台账号。
     *
     * <p>没账号的员工发出去也没人看（消息服务按 userId 投递），属于无效触达，
     * 应当计入「跳过」而不是「已发」。</p>
     *
     * @return 可催办返回 {@code true}
     */
    public boolean isUrgeable() {
        return status != null && status.isActive() && userId != null;
    }

    /**
     * 校验同步来源字段。
     *
     * @param syncedAt 同步时刻
     */
    public void validateForSync(LocalDateTime syncedAt) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.MEMBER_INVALID, "员工编号不能为空");
        }
        if (syncedAt == null) {
            throw LmsErrorCode.exception(LmsErrorCode.MEMBER_INVALID, "同步时间不能为空");
        }
        if (status == null) {
            status = MemberStatus.ACTIVE;
        }
        if (leaveDate != null && hireDate != null && leaveDate.isBefore(hireDate)) {
            // 源头数据错乱时按离职处理并纠正日期，不让一条脏数据卡住整批同步
            leaveDate = hireDate;
        }
    }
}
