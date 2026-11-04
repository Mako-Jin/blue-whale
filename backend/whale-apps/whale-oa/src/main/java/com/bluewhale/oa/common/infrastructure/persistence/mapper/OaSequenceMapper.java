package com.bluewhale.oa.common.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.common.infrastructure.persistence.entity.OaSequenceEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 业务序列号 Mapper。
 *
 * <p>递增使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}，依赖
 * {@code uk_sequence_biz_period} 唯一键保证并发安全。</p>
 *
 * @author BlueWhale
 */
public interface OaSequenceMapper extends BaseMapper<OaSequenceEntity> {

    /**
     * 存在则递增、不存在则初始化为 1。
     *
     * @param id 雪花主键
     * @param tenantId 租户ID
     * @param bizType 业务类型
     * @param period 期间 yyyyMM
     * @return 受影响行数
     */
    @Insert("INSERT INTO oa_sequence (id, tenant_id, biz_type, period, current_value, version, create_time, update_time, deleted) "
            + "VALUES (#{id}, #{tenantId}, #{bizType}, #{period}, 1, 1, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE current_value = current_value + 1, version = version + 1, update_time = NOW()")
    int upsertIncrement(@Param("id") Long id,
                        @Param("tenantId") Long tenantId,
                        @Param("bizType") String bizType,
                        @Param("period") String period);

    /**
     * 查询当前序号。
     *
     * @param tenantId 租户ID
     * @param bizType 业务类型
     * @param period 期间 yyyyMM
     * @return 当前序号；不存在返回 {@code null}
     */
    @Select("SELECT current_value FROM oa_sequence WHERE tenant_id = #{tenantId} "
            + "AND biz_type = #{bizType} AND period = #{period} AND deleted = 0")
    Long selectCurrentValue(@Param("tenantId") Long tenantId,
                            @Param("bizType") String bizType,
                            @Param("period") String period);
}
