package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.model.TripAdvance;
import com.bluewhale.oa.hr.domain.model.TripItinerary;
import com.bluewhale.oa.hr.domain.model.TripQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 出差申请仓储（包含行程与预支记录）。
 *
 * @author BlueWhale
 */
public interface TripRepository {

    /**
     * 分页查询出差申请。
     *
     * @param query 查询条件
     * @return 申请列表
     */
    List<Trip> page(TripQuery query);

    /**
     * 统计满足条件的申请数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(TripQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 申请
     */
    Optional<Trip> findById(Long id);

    /**
     * 判断单号是否已存在。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID
     * @return 存在返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 查询出差行程。
     *
     * @param tripId 出差申请ID
     * @return 行程列表
     */
    List<TripItinerary> listItineraries(Long tripId);

    /**
     * 整体替换出差行程（先删除旧行程再写入）。
     *
     * @param tripId 出差申请ID
     * @param itineraries 行程列表
     */
    void replaceItineraries(Long tripId, List<TripItinerary> itineraries);

    /**
     * 查询出差预支记录。
     *
     * @param tripId 出差申请ID
     * @return 预支记录
     */
    Optional<TripAdvance> findAdvanceByTripId(Long tripId);

    /**
     * 新增预支记录。
     *
     * @param advance 预支记录
     */
    void insertAdvance(TripAdvance advance);

    /**
     * 新增申请。
     *
     * @param trip 申请
     */
    void insert(Trip trip);

    /**
     * 更新申请。
     *
     * @param trip 申请
     */
    void update(Trip trip);

    /**
     * 逻辑删除申请。
     *
     * @param id 主键
     */
    void deleteById(Long id);

    /**
     * 查询已通过审批、报告待提交且出差结束日期不晚于截止日的申请。
     *
     * @param deadline 结束日期上限（含）
     * @param limit 单次最大条数
     * @return 待提醒申请列表
     */
    List<Trip> listReportPendingEndedBefore(LocalDate deadline, int limit);
}
