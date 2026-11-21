package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.stat.MessageChannelFailPo;
import com.bluewhale.message.po.stat.MessageDayDeliveryPo;
import com.bluewhale.message.po.stat.MessageDeliveryStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递记录 Mapper。
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface MessageDeliveryMapper extends BaseMapper<MessageDeliveryPo> {

    /**
     * 时间窗内的投递条数（通政殿概览 T5.8）
     *
     * <p>按 {@code sent_at} 而不是 {@code create_time} 卡窗口：投递行先落库再发送，
     * 尚未发出的那些 {@code sent_at} 为空——把它们算进"今日投递"会让数字比实际送出的大。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 投递条数；无数据时为 0
     */
    @Select("SELECT COUNT(*) FROM msg_delivery "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND sent_at >= #{from} AND sent_at < #{to}")
    long countSentBetween(@Param("tenantId") Long tenantId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /**
     * 时间窗内的投递成功/失败统计（通政殿概览 T5.8）
     *
     * <p>成功口径与 2D 投递看板（{@code MessageDashboardServiceImpl}）逐字一致
     * （{@code DELIVERED + READ} / {@code FAILED + BOUNCED}）：两处口径一旦分叉，
     * 城里说 92%、后台说 88%，谁都不知道该信哪个。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 总数与成功/失败/退信条数；无数据时全为 0
     */
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(CASE WHEN status IN ('DELIVERED', 'READ') THEN 1 ELSE 0 END), 0) AS success, "
            + "COALESCE(SUM(CASE WHEN status IN ('FAILED', 'BOUNCED') THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(CASE WHEN status = 'BOUNCED' THEN 1 ELSE 0 END), 0) AS bounced "
            + "FROM msg_delivery "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND sent_at >= #{from} AND sent_at < #{to}")
    MessageDeliveryStatPo sumDeliveryBetween(@Param("tenantId") Long tenantId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * 按天聚合的投递条数（通政殿概览 T5.8）
     *
     * <p>条件列不套函数（{@code sent_at >= / <}），保住 {@code idx_tenant_sent}；
     * 只有分组键用 {@code DATE(sent_at)}，且窗口已收窄到 7 天。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    @Select("SELECT DATE(sent_at) AS day, COUNT(*) AS deliveries FROM msg_delivery "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND sent_at >= #{from} AND sent_at < #{to} "
            + "GROUP BY DATE(sent_at) ORDER BY day")
    List<MessageDayDeliveryPo> countSentByDay(@Param("tenantId") Long tenantId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * 时间窗内失败条数最多的渠道（通政殿概览 T5.8）
     *
     * <p>按渠道而不是按失败原因分组：屏风柱图的横轴只放得下四五个类目，
     * 而渠道是枚举（INAPP / EMAIL / SMS / IM），永远排得下；失败原因文本长短不一，
     * 换个供应商文案就换一批类目。要看具体原因去 2D 投递看板的 Top10。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按失败条数降序
     */
    @Select("SELECT channel, COUNT(*) AS failures FROM msg_delivery "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN ('FAILED', 'BOUNCED') "
            + "AND sent_at >= #{from} AND sent_at < #{to} "
            + "GROUP BY channel ORDER BY failures DESC LIMIT #{limit}")
    List<MessageChannelFailPo> topFailedChannels(@Param("tenantId") Long tenantId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 @Param("limit") int limit);
}
