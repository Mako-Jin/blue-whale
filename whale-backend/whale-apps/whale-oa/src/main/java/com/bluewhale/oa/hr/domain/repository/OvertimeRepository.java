package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.Overtime;
import com.bluewhale.oa.hr.domain.model.OvertimeQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 加班申请仓储。
 *
 * @author BlueWhale
 */
public interface OvertimeRepository {

    /**
     * 分页查询加班申请。
     *
     * @param query 查询条件
     * @return 申请列表
     */
    List<Overtime> page(OvertimeQuery query);

    /**
     * 统计满足条件的申请数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(OvertimeQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 申请
     */
    Optional<Overtime> findById(Long id);

    /**
     * 判断单号是否已存在。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID
     * @return 存在返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 查询同一申请人同一天的申请（排除已驳回/已撤销）。
     *
     * @param applicant 申请人账号
     * @param overtimeDate 加班日期
     * @param excludeId 更新时排除自身ID
     * @return 申请列表
     */
    List<Overtime> listByApplicantAndDate(String applicant, LocalDate overtimeDate, Long excludeId);

    /**
     * 汇总同一申请人在指定日期区间内的加班时长（排除已驳回/已撤销）。
     *
     * @param applicant 申请人账号
     * @param dateFrom 起始日期（含）
     * @param dateTo 结束日期（含）
     * @param excludeId 更新时排除自身ID
     * @return 加班时长合计
     */
    BigDecimal sumHours(String applicant, LocalDate dateFrom, LocalDate dateTo, Long excludeId);

    /**
     * 新增申请。
     *
     * @param overtime 申请
     */
    void insert(Overtime overtime);

    /**
     * 更新申请。
     *
     * @param overtime 申请
     */
    void update(Overtime overtime);

    /**
     * 逻辑删除申请。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
