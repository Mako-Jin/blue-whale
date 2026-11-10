package com.bluewhale.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.map.entity.MapGeocodeCache;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 解析缓存 Mapper。
 *
 * @author BlueWhale
 */
public interface MapGeocodeCacheMapper extends BaseMapper<MapGeocodeCache> {

    /**
     * 命中计数 +1
     *
     * <p>单独一条 UPDATE 而不是「查出来再改回去」：命中计数是纯统计量，
     * 并发下丢几个数无所谓，但为此加锁或重试会拖慢每一次读缓存。</p>
     *
     * @param id 缓存主键
     * @return 影响行数
     */
    @Update("UPDATE map_geocode_cache SET hit_count = hit_count + 1 WHERE id = #{id}")
    int increaseHit(@Param("id") Long id);
}
