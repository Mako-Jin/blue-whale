package com.bluewhale.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.map.entity.MapLocation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 业务打点 Mapper。
 *
 * @author BlueWhale
 */
public interface MapLocationMapper extends BaseMapper<MapLocation> {

    /**
     * 物理删除某业务对象的打点
     *
     * <p>绕开 {@code BaseMapper#deleteById}：本表没有逻辑删除列，但统一用一条显式 SQL
     * 表达「取消打点就是删掉这行」，避免以后有人给实体补上 {@code @TableLogic}
     * 之后语义悄悄变成「留一条看不见的记录，还占着唯一键」。</p>
     *
     * @param id       打点主键
     * @param tenantId 租户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM map_location WHERE id = #{id} AND tenant_id = #{tenantId}")
    int deletePhysical(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
