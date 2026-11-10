package com.bluewhale.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.map.entity.MapQuotaUsage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 供应商配额用量 Mapper。
 *
 * @author BlueWhale
 */
public interface MapQuotaUsageMapper extends BaseMapper<MapQuotaUsage> {

    /**
     * 原子占用一次配额
     *
     * <p>{@code used_count < limit_count} 写在 WHERE 里，靠数据库的原子更新做准入判定：
     * 先查后改在并发下会一起通过检查，最后超出的那几次正是「以为还有额度」的时刻。
     * {@code limit_count = 0} 表示不限，单独放行。</p>
     *
     * @param tenantId  租户ID
     * @param provider  供应商名
     * @param usageDate 统计日期
     * @return 占位成功返回 1，超配额返回 0
     */
    @Update("UPDATE map_quota_usage SET used_count = used_count + 1, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND provider = #{provider} AND usage_date = #{usageDate} "
            + "AND (limit_count = 0 OR used_count < limit_count)")
    int tryConsume(@Param("tenantId") Long tenantId, @Param("provider") String provider,
                   @Param("usageDate") LocalDate usageDate);
}
