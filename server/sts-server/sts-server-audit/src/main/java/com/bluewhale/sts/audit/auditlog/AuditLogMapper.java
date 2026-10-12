package com.bluewhale.sts.audit.auditlog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审计记录仓储。
 *
 * <p><b>只有"写一条"和"查"两类操作，没有 update、没有 delete。</b>
 * 这不是"这期先不做"，而是这张表的定义：审计记录一旦写下就是历史，
 * 可修改的历史不是历史。因此本接口刻意**不继承**任何提供更新/删除能力的父接口
 * （{@code BaseMapper} 自带的 {@code updateById} / {@code deleteById} 在 Service 层
 * 从未被调用，也不会被调用 —— 一旦有人需要它们，先要回答的是"为什么审计要改"）。
 *
 * <p>{@code @Mapper} 必须显式标注：本模块的 Mapper 扫描配了
 * {@code annotationClass = Mapper.class}，不标注就不会被注册 ——
 * 这是有意为之，避免包内任何接口都被当成 Mapper。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    /**
     * 写入一条审计记录。
     *
     * <p><b>为什么手写 INSERT 而不用 {@code insert(entity)}：</b>
     * 那样需要先把实体交给 MyBatis-Plus 的字段填充链，而本模块刻意没有
     * {@code MetaObjectHandler}（审计表的字段全部由调用方一次填好，没有"当前时间"
     * 这类需要自动补的默认值）。写死列名还有一个好处：**列名与实体的对应关系
     * 在这里一眼可查**，不必去猜 MyBatis-Plus 这次会生成哪些列。
     *
     * <p>{@code useGeneratedKeys} 只在需要回填主键时才有意义 —— 审计写完之后
     * 没有人关心它自增到了几号，所以不要求回填。
     */
    @Insert("""
            INSERT INTO bw_audit_log
                (action, resource, target_id, operator_id, operator_name, tenant_id,
                 result, error_code, error_message, http_method, request_uri, ip,
                 user_agent, trace_id, duration_ms, server_time)
            VALUES
                (#{action}, #{resource}, #{targetId}, #{operatorId}, #{operatorName}, #{tenantId},
                 #{result}, #{errorCode}, #{errorMessage}, #{httpMethod}, #{requestUri}, #{ip},
                 #{userAgent}, #{traceId}, #{durationMs}, #{serverTime})
            """)
    int insertRecord(AuditLogEntity entity);

    /**
     * 按动作聚合计数。
     *
     * <p><b>为什么写 SQL 而不用 QueryWrapper 的 selectMaps：</b>
     * 后者的返回是 {@code Map<String, Object>}，键名由数据库决定 ——
     * H2 返回大写的 {@code ACTION_COUNT}，MySQL 返回小写的 {@code action_count}，
     * 于是 {@code map.get("actionCount")} 在一套环境下能跑、换一套就返回 null，而且不报错。
     * 映射到强类型对象可以让这个问题在编译期就不存在（与 {@code ClientEventMapper} 同一取舍）。
     *
     * <p>失败数用 {@code COUNT(CASE WHEN ... THEN 1 END)} 而不是
     * {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)}：后者在部分数据库上返回数值类型，
     * MyBatis 映射到 {@code Long} 时要靠隐式转换；而 {@code COUNT(expr)} 只统计非空，
     * 返回的本来就是行数，三套库一致。
     *
     * <p>列别名**不加引号**：加引号会让标识符大小写敏感（H2 变小写、MySQL 需反引号），
     * 不加引号则两边都按大小写不敏感处理，配合 MyBatis 的下划线转驼峰即可正确映射。
     *
     * <p>不像日志域那样补齐"没有数据的项"：动作名是开放集合（各域可自由新增），
     * 没有"全集"可补，硬补只会补出一个过时的清单。
     */
    @Select("""
            <script>
            SELECT action AS action,
                   COUNT(*) AS action_count,
                   COUNT(CASE WHEN result = 'FAILURE' THEN 1 END) AS failure_count
            FROM bw_audit_log
            WHERE server_time &gt;= #{startTime} AND server_time &lt;= #{endTime}
            <if test="resource != null and resource != ''">
                AND resource = #{resource}
            </if>
            <if test="operatorId != null">
                AND operator_id = #{operatorId}
            </if>
            GROUP BY action
            ORDER BY action
            </script>
            """)
    List<AuditActionCount> countByAction(@Param("resource") String resource,
                                         @Param("operatorId") Long operatorId,
                                         @Param("startTime") long startTime,
                                         @Param("endTime") long endTime);

}
