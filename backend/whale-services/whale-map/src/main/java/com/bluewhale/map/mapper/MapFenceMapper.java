package com.bluewhale.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.map.entity.MapFence;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 围栏 Mapper。
 *
 * @author BlueWhale
 */
public interface MapFenceMapper extends BaseMapper<MapFence> {

    /**
     * 按外接矩形粗筛候选围栏。
     *
     * <p><b>为什么先 bbox 再几何判定</b>：精确判定要做投影与逐边计算，几百个围栏全算一遍
     * 在单次打卡请求里是纯浪费；bbox 是一条索引范围内的 SQL，能把候选压到个位数。</p>
     *
     * <p><b>为什么业务过滤要放三个 OR</b>：围栏有三种归属——通用（{@code biz_type} 为空）、
     * 绑类型（只绑 {@code biz_type}）、绑对象（{@code biz_type} + {@code biz_id}）。
     * 只写等值过滤会让通用围栏永远命不中，只写「为空放行」又会让别的考勤组的围栏混进来。</p>
     *
     * <p><b>跨 180 度经线的围栏</b>在写入时就把 bbox 放成全球经度（见 {@code MapFenceServiceImpl}），
     * 所以这里不需要再处理 {@code min_lng > max_lng} 的情况。</p>
     *
     * @param tenantId 租户ID
     * @param bizType  业务类型（可空，空则不限）
     * @param bizId    业务对象ID（可空，空则不限）
     * @param lng      查询点经度（存储坐标系）
     * @param lat      查询点纬度（存储坐标系）
     * @return 候选围栏
     */
    @Select("""
            SELECT * FROM map_fence
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
              AND enabled = 1
              AND min_lat <= #{lat} AND max_lat >= #{lat}
              AND min_lng <= #{lng} AND max_lng >= #{lng}
              AND (#{bizType} IS NULL OR biz_type IS NULL OR biz_type = #{bizType})
              AND (#{bizId} IS NULL OR biz_id IS NULL OR biz_id = #{bizId})
            ORDER BY id
            """)
    List<MapFence> selectCandidates(@Param("tenantId") Long tenantId,
                                    @Param("bizType") String bizType,
                                    @Param("bizId") String bizId,
                                    @Param("lng") BigDecimal lng,
                                    @Param("lat") BigDecimal lat);
}
