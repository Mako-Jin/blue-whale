package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 请假申请仓储。
 *
 * @author BlueWhale
 */
public interface LeaveRepository {

    /**
     * 分页查询请假申请。
     *
     * @param query 查询条件
     * @return 当前页申请
     */
    List<Leave> page(LeaveQuery query);

    /**
     * 统计请假申请数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeaveQuery query);

    /**
     * 按主键查询申请。
     *
     * @param id 主键
     * @return 申请
     */
    Optional<Leave> findById(Long id);

    /**
     * 是否存在同一申请单号。
     *
     * @param code 申请单号
     * @param excludeId 排除的主键（更新时排除自身），新增传 {@code null}
     * @return 存在返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 查询同一申请人同一假期类型下时间重叠且仍占用额度的申请。
     *
     * <p>{@code REJECTED / CANCELLED} 不占用额度，不参与重叠判断。</p>
     *
     * @param applicant 申请人账号
     * @param leaveType 假期类型编码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param excludeId 排除的主键（更新时排除自身），新增传 {@code null}
     * @return 重叠申请列表
     */
    List<Leave> listOverlapping(String applicant, String leaveType, LocalDate startDate,
                                LocalDate endDate, Long excludeId);

    /**
     * 按申请人批量查询已批准且与区间重叠的请假（内网只读契约用）。
     *
     * <p>只取 {@code APPROVED}：待审批的请假还没定，拿它做冲突提示会给出
     * 「可能冲突」的噪声；已驳回/已撤销的也不占用时间。</p>
     *
     * @param applicantIds 申请人用户 ID 集合（空集直接返回空）
     * @param from 区间起（含）
     * @param to 区间止（含）
     * @param limit 返回条数上限
     * @return 请假列表
     */
    List<Leave> listApprovedOverlapping(Collection<Long> applicantIds, LocalDate from, LocalDate to, int limit);

    /**
     * 新增申请。
     *
     * @param leave 申请
     */
    void insert(Leave leave);

    /**
     * 更新申请。
     *
     * @param leave 申请
     */
    void update(Leave leave);

    /**
     * 逻辑删除申请。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
