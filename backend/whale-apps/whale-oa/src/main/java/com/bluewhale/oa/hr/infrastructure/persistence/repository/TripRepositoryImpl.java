package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.model.TripAdvance;
import com.bluewhale.oa.hr.domain.model.TripItinerary;
import com.bluewhale.oa.hr.domain.model.TripQuery;
import com.bluewhale.oa.hr.domain.model.TripReportStatus;
import com.bluewhale.oa.hr.domain.model.TripStatus;
import com.bluewhale.oa.hr.domain.model.TripTransportType;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.TripAdvanceEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.TripEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.TripItineraryEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.TripAdvanceMapper;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.TripItineraryMapper;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 出差申请仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TripRepositoryImpl implements TripRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final TripMapper tripMapper;

    private final TripItineraryMapper tripItineraryMapper;

    private final TripAdvanceMapper tripAdvanceMapper;

    @Override
    public List<Trip> page(TripQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<TripEntity> page = tripMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(TripQuery query) {
        Long total = tripMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Trip> findById(Long id) {
        return Optional.ofNullable(tripMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<TripEntity> wrapper = new LambdaQueryWrapper<TripEntity>()
                .eq(TripEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(TripEntity::getId, excludeId);
        }
        Long total = tripMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public List<TripItinerary> listItineraries(Long tripId) {
        return tripItineraryMapper.selectList(new LambdaQueryWrapper<TripItineraryEntity>()
                        .eq(TripItineraryEntity::getTripId, tripId)
                        .orderByAsc(TripItineraryEntity::getDayNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void replaceItineraries(Long tripId, List<TripItinerary> itineraries) {
        tripItineraryMapper.deleteByTripId(tripId);
        if (itineraries == null || itineraries.isEmpty()) {
            return;
        }
        for (TripItinerary itinerary : itineraries) {
            TripItineraryEntity entity = new TripItineraryEntity();
            entity.setTripId(tripId);
            entity.setDayNo(itinerary.getDayNo());
            entity.setTripDate(itinerary.getTripDate());
            entity.setFromPlace(itinerary.getFromPlace());
            entity.setToPlace(itinerary.getToPlace());
            entity.setTransport(itinerary.getTransport() == null
                    ? TripTransportType.TRAIN.name() : itinerary.getTransport().name());
            entity.setRemark(itinerary.getRemark());
            tripItineraryMapper.insert(entity);
        }
    }

    @Override
    public Optional<TripAdvance> findAdvanceByTripId(Long tripId) {
        return Optional.ofNullable(tripAdvanceMapper.selectOne(
                        new LambdaQueryWrapper<TripAdvanceEntity>()
                                .eq(TripAdvanceEntity::getTripId, tripId).last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public void insertAdvance(TripAdvance advance) {
        TripAdvanceEntity entity = new TripAdvanceEntity();
        entity.setTripId(advance.getTripId());
        entity.setApplicantId(advance.getApplicantId());
        entity.setAmount(advance.getAmount());
        entity.setStatus(advance.getStatus());
        tripAdvanceMapper.insert(entity);
        advance.setId(entity.getId());
        advance.setCreateTime(entity.getCreateTime());
        advance.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void insert(Trip trip) {
        TripEntity entity = new TripEntity();
        fill(entity, trip);
        tripMapper.insert(entity);
        trip.setId(entity.getId());
        trip.setCreateTime(entity.getCreateTime());
        trip.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Trip trip) {
        tripMapper.update(null, new LambdaUpdateWrapper<TripEntity>()
                .eq(TripEntity::getId, trip.getId())
                .set(TripEntity::getCode, trip.getCode())
                .set(TripEntity::getApplicant, trip.getApplicant())
                .set(TripEntity::getApplicantId, trip.getApplicantId())
                .set(TripEntity::getDestination, trip.getDestination())
                .set(TripEntity::getStartDate, trip.getStartDate())
                .set(TripEntity::getEndDate, trip.getEndDate())
                .set(TripEntity::getDays, trip.getDays())
                .set(TripEntity::getTransportType,
                        trip.getTransportType() == null ? null : trip.getTransportType().name())
                .set(TripEntity::getBudgetAmount, trip.getBudgetAmount())
                .set(TripEntity::getAdvanceAmount, trip.getAdvanceAmount())
                .set(TripEntity::getReportStatus,
                        trip.getReportStatus() == null ? null : trip.getReportStatus().name())
                .set(TripEntity::getReportContent, trip.getReportContent())
                .set(TripEntity::getReportTime, trip.getReportTime())
                .set(TripEntity::getStatus, trip.getStatus() == null ? null : trip.getStatus().name())
                .set(TripEntity::getApprovalInstanceId, trip.getApprovalInstanceId())
                .set(TripEntity::getRemark, trip.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        tripMapper.deleteById(id);
    }

    @Override
    public List<Trip> listReportPendingEndedBefore(LocalDate deadline, int limit) {
        int size = Math.max(limit, 1);
        return tripMapper.selectList(new LambdaQueryWrapper<TripEntity>()
                        .eq(TripEntity::getStatus, TripStatus.APPROVED.name())
                        .eq(TripEntity::getReportStatus, TripReportStatus.PENDING.name())
                        .le(TripEntity::getEndDate, deadline)
                        .orderByAsc(TripEntity::getEndDate)
                        .last("LIMIT " + size))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Trip> listApprovedOverlapping(java.util.Collection<Long> applicantIds, LocalDate from,
                                              LocalDate to, int limit) {
        if (applicantIds == null || applicantIds.isEmpty()) {
            return List.of();
        }
        int size = Math.max(limit, 1);
        return tripMapper.selectList(new LambdaQueryWrapper<TripEntity>()
                        .in(TripEntity::getApplicantId, applicantIds)
                        .eq(TripEntity::getStatus, TripStatus.APPROVED.name())
                        .le(TripEntity::getStartDate, to)
                        .ge(TripEntity::getEndDate, from)
                        .last("LIMIT " + size))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TripEntity> filter(TripQuery query) {
        return new LambdaQueryWrapper<TripEntity>()
                .like(StringUtils.hasText(query.getCode()), TripEntity::getCode, query.getCode())
                .eq(StringUtils.hasText(query.getStatus()), TripEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getApplicant()),
                        TripEntity::getApplicant, query.getApplicant())
                .like(StringUtils.hasText(query.getDestination()),
                        TripEntity::getDestination, query.getDestination())
                .eq(StringUtils.hasText(query.getReportStatus()),
                        TripEntity::getReportStatus, query.getReportStatus())
                .ge(query.getDateFrom() != null, TripEntity::getStartDate, query.getDateFrom())
                .le(query.getDateTo() != null, TripEntity::getStartDate, query.getDateTo())
                .orderByDesc(TripEntity::getStartDate)
                .orderByDesc(TripEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Trip toDomain(TripEntity entity) {
        Trip trip = new Trip();
        trip.setId(entity.getId());
        trip.setCode(entity.getCode());
        trip.setApplicant(entity.getApplicant());
        trip.setApplicantId(entity.getApplicantId());
        trip.setDestination(entity.getDestination());
        trip.setStartDate(entity.getStartDate());
        trip.setEndDate(entity.getEndDate());
        trip.setDays(entity.getDays());
        trip.setTransportType(entity.getTransportType() == null
                ? null : TripTransportType.of(entity.getTransportType()));
        trip.setBudgetAmount(entity.getBudgetAmount() == null ? BigDecimal.ZERO : entity.getBudgetAmount());
        trip.setAdvanceAmount(entity.getAdvanceAmount() == null ? BigDecimal.ZERO : entity.getAdvanceAmount());
        trip.setReportStatus(entity.getReportStatus() == null
                ? TripReportStatus.PENDING : TripReportStatus.of(entity.getReportStatus()));
        trip.setReportContent(entity.getReportContent());
        trip.setReportTime(entity.getReportTime());
        trip.setStatus(entity.getStatus() == null ? TripStatus.PENDING : TripStatus.of(entity.getStatus()));
        trip.setApprovalInstanceId(entity.getApprovalInstanceId());
        trip.setRemark(entity.getRemark());
        trip.setCreateTime(entity.getCreateTime());
        trip.setUpdateTime(entity.getUpdateTime());
        return trip;
    }

    /**
     * 行程实体转领域模型。
     *
     * @param entity 实体
     * @return 行程
     */
    private TripItinerary toDomain(TripItineraryEntity entity) {
        TripItinerary itinerary = new TripItinerary();
        itinerary.setId(entity.getId());
        itinerary.setTripId(entity.getTripId());
        itinerary.setDayNo(entity.getDayNo());
        itinerary.setTripDate(entity.getTripDate());
        itinerary.setFromPlace(entity.getFromPlace());
        itinerary.setToPlace(entity.getToPlace());
        itinerary.setTransport(entity.getTransport() == null
                ? null : TripTransportType.of(entity.getTransport()));
        itinerary.setRemark(entity.getRemark());
        itinerary.setCreateTime(entity.getCreateTime());
        itinerary.setUpdateTime(entity.getUpdateTime());
        return itinerary;
    }

    /**
     * 预支实体转领域模型。
     *
     * @param entity 实体
     * @return 预支记录
     */
    private TripAdvance toDomain(TripAdvanceEntity entity) {
        TripAdvance advance = new TripAdvance();
        advance.setId(entity.getId());
        advance.setTripId(entity.getTripId());
        advance.setApplicantId(entity.getApplicantId());
        advance.setAmount(entity.getAmount());
        advance.setStatus(entity.getStatus());
        advance.setCreateTime(entity.getCreateTime());
        advance.setUpdateTime(entity.getUpdateTime());
        return advance;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param trip 领域模型
     */
    private void fill(TripEntity entity, Trip trip) {
        entity.setCode(trip.getCode());
        entity.setApplicant(trip.getApplicant());
        entity.setApplicantId(trip.getApplicantId());
        entity.setDestination(trip.getDestination());
        entity.setStartDate(trip.getStartDate());
        entity.setEndDate(trip.getEndDate());
        entity.setDays(trip.getDays());
        entity.setTransportType(trip.getTransportType() == null
                ? TripTransportType.TRAIN.name() : trip.getTransportType().name());
        entity.setBudgetAmount(trip.getBudgetAmount() == null ? BigDecimal.ZERO : trip.getBudgetAmount());
        entity.setAdvanceAmount(trip.getAdvanceAmount() == null ? BigDecimal.ZERO : trip.getAdvanceAmount());
        entity.setReportStatus(trip.getReportStatus() == null
                ? TripReportStatus.PENDING.name() : trip.getReportStatus().name());
        entity.setStatus(trip.getStatus() == null ? TripStatus.PENDING.name() : trip.getStatus().name());
        entity.setApprovalInstanceId(trip.getApprovalInstanceId());
        entity.setRemark(trip.getRemark());
    }
}
