package com.bluewhale.oa.hr.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.TripItineraryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 出差行程 Mapper。
 *
 * @author BlueWhale
 */
public interface TripItineraryMapper extends BaseMapper<TripItineraryEntity> {

    /**
     * 物理删除指定出差单的全部行程（行程为随单快照，不保留历史）。
     *
     * @param tripId 出差申请ID
     * @return 删除行数
     */
    @Delete("DELETE FROM oa_trip_itinerary WHERE trip_id = #{tripId}")
    int deleteByTripId(@Param("tripId") Long tripId);
}
