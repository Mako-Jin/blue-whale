package com.bluewhale.audit.mapper.stat;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 起居殿概览统计 Mapper（S5 / T5.9）。
 *
 * <p>为什么不复用公共模块的 {@code AuditLogMapper}：那是 {@code BaseMapper<AuditLogRecord>}，
 * 只提供单表 CRUD，加自定义聚合 SQL 会污染所有引用它的服务。这里单独建一个只读统计接口，
 * 用 {@code @Select} 直出聚合，与其他殿（message/form/job）的统计口径一致。</p>
 *
 * <p>所有查询显式带 {@code tenant_id}：概览 provider 在 SDK 独立线程池里跑，拿不到请求线程的
 * {@code TenantContext}，租户行级插件会忽略本表——只能靠 SQL 自己带租户，否则把别家的审计
 * 算进这一屏。时间条件写成 {@code column >= ? AND column < ?}，函数只用在分组键上，保住
 * {@code idx_tenant_time}。</p>
 *
 * <p>不用标 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 扫描。</p>
 *
 * @author BlueWhale
 */
public interface AuditOverviewStatMapper {

    /**
     * 时间窗内的审计记录条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 审计条数；无数据时为 0
     */
    @Select("SELECT COUNT(*) FROM audit_log "
            + "WHERE tenant_id = #{tenantId} AND create_time >= #{from} AND create_time < #{to}")
    long countBetween(@Param("tenantId") Long tenantId,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    /**
     * 时间窗内的失败操作数（biz_status = 0）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 失败条数；无数据时为 0
     */
    @Select("SELECT COUNT(*) FROM audit_log "
            + "WHERE tenant_id = #{tenantId} AND biz_status = 0 "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countFailedBetween(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /**
     * 本租户最近一条审计记录的写入时刻
     *
     * <p>这是「留痕断流」判定的基准：审计服务是异步汇聚写入，进程还活着但摄入链路断了时
     * 没有任何报错，只有这个时间停住不动。取 MAX 走 idx_tenant_time，不会全表扫描。</p>
     *
     * @param tenantId 租户 ID
     * @return 最近写入时刻；从未有记录时为 null
     */
    @Select("SELECT MAX(create_time) FROM audit_log WHERE tenant_id = #{tenantId}")
    LocalDateTime lastAuditTime(@Param("tenantId") Long tenantId);

    /**
     * 按天聚合的审计条数（近 N 日趋势）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺日不补，由调用方补 0）
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS audits FROM audit_log "
            + "WHERE tenant_id = #{tenantId} AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY DATE(create_time) ORDER BY day")
    List<AuditDayCountPo> countByDay(@Param("tenantId") Long tenantId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * 按操作类型聚合的条数 TopN
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按条数降序；操作类型为空时归 OTHER
     */
    @Select("SELECT COALESCE(NULLIF(operate_type, ''), 'OTHER') AS operateType, COUNT(*) AS audits "
            + "FROM audit_log WHERE tenant_id = #{tenantId} "
            + "AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY COALESCE(NULLIF(operate_type, ''), 'OTHER') "
            + "ORDER BY audits DESC LIMIT #{limit}")
    List<AuditOperateTypePo> topOperateTypes(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("limit") int limit);
}
