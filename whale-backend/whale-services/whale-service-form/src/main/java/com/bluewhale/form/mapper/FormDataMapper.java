package com.bluewhale.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.stat.FormDaySubmitPo;
import com.bluewhale.form.po.stat.FormTopPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表单数据 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface FormDataMapper extends BaseMapper<FormDataPo> {

    /**
     * 查询存在未逻辑删除表单数据的租户ID（去重）
     *
     * <p>供对账任务遍历租户使用：任务启动时租户上下文为空，租户插件不会追加租户条件。</p>
     *
     * @return 活跃租户ID列表
     */
    @Select("SELECT DISTINCT tenant_id FROM form_data WHERE deleted = 0")
    List<Long> selectActiveTenantIds();

    /**
     * 时间窗内的提交条数（考功殿概览 T5.7）
     *
     * <p>只数 {@code status = 1}（有效）：作废行还留在表里，把作废算进"今天录了几条"
     * 会让考核口径单向偏大——越清理越对不上。</p>
     *
     * <p>条件列不套函数（{@code >= / <}），走 {@code idx_tenant_create}；
     * 只有按天聚合那条才用 {@code DATE(create_time)}，那时窗口已收窄到 7 天。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 提交条数
     */
    @Select("SELECT COUNT(*) FROM form_data "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countSubmittedBetween(@Param("tenantId") Long tenantId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * 指定业务状态的数据条数（考功殿概览 T5.7）
     *
     * <p>不限时间窗：待审批是**累积量**，只数今天提交的会漏掉上周就卡住的那批，
     * 而那批正是需要有人去催的。</p>
     *
     * @param tenantId  租户 ID
     * @param bizStatus 业务状态（1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回）
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM form_data "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 AND biz_status = #{bizStatus}")
    long countByBizStatus(@Param("tenantId") Long tenantId, @Param("bizStatus") int bizStatus);

    /**
     * 指定业务状态且在观察点之前就没再变过的数据条数（考功殿概览 T5.7）
     *
     * <p>用 {@code update_time} 而不是 {@code create_time}：一条数据上周提交、昨天被驳回重提，
     * 它该从昨天重新计时。而审批中的行只会被审批回调改动，所以这里等价于
     * "进入审批后再没人动过"。</p>
     *
     * @param tenantId  租户 ID
     * @param bizStatus 业务状态
     * @param before    观察点（早于它算超时）
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM form_data "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 "
            + "AND biz_status = #{bizStatus} AND update_time < #{before}")
    long countStaleByBizStatus(@Param("tenantId") Long tenantId,
                               @Param("bizStatus") int bizStatus,
                               @Param("before") LocalDateTime before);

    /**
     * 按天聚合的提交条数（考功殿概览 T5.7）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS submits FROM form_data "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 "
            + "AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY DATE(create_time) ORDER BY day")
    List<FormDaySubmitPo> countSubmitsByDay(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /**
     * 时间窗内提交条数最多的表单（考功殿概览 T5.7）
     *
     * <p>按 {@code definition_code} 分组、名称取 {@code MAX(d.name)}：同编码存在多条历史定义
     * （改码前遗留）时，按名称分组会把同一条表单拆成两根柱子，而 {@code COUNT(DISTINCT f.id)}
     * 又要为去重多扫一遍；分组键只留编码最省。</p>
     *
     * <p>名称走 {@code LEFT JOIN} 而不是内连接：定义被删掉后历史数据还在，
     * 内连接会把这几条提交从图上直接抹掉——"提交量对不上"比"名字显示成编码"更难查。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按提交条数降序
     */
    @Select("SELECT f.definition_code AS definitionCode, MAX(d.name) AS formName, COUNT(*) AS submits "
            + "FROM form_data f "
            + "LEFT JOIN form_definition d ON d.tenant_id = f.tenant_id "
            + "AND d.code = f.definition_code AND d.deleted = 0 "
            + "WHERE f.tenant_id = #{tenantId} AND f.deleted = 0 AND f.status = 1 "
            + "AND f.create_time >= #{from} AND f.create_time < #{to} "
            + "GROUP BY f.definition_code ORDER BY submits DESC LIMIT #{limit}")
    List<FormTopPo> topForms(@Param("tenantId") Long tenantId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("limit") int limit);
}
