package com.bluewhale.sts.track.clientevent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户端行为事件仓储。
 *
 * <p>{@code @Mapper} 是**必须显式标注**的：本模块的 Mapper 扫描配了
 * {@code annotationClass = Mapper.class}，不标注就不会被注册 ——
 * 这是有意为之，避免包内任何接口都被当成 Mapper。
 */
@Mapper
public interface ClientEventMapper extends BaseMapper<ClientEventEntity> {

    /**
     * 按事件名聚合计数，用于事件分布面板。
     *
     * <p><b>为什么写 SQL 而不用 QueryWrapper 的 selectMaps：</b>
     * 后者的返回是 {@code Map<String, Object>}，键名由数据库决定 ——
     * H2 返回大写的 {@code EVENT_NAME_COUNT}，MySQL 返回小写的 {@code event_name_count}，
     * 于是 {@code map.get("eventNameCount")} 在一套环境下能跑、换一套就返回 null，
     * 而且不报错。映射到强类型对象可以让这个问题在编译期就不存在。
     *
     * <p>列别名**不加引号**：加引号会让标识符大小写敏感（H2 变小写、MySQL 需用反引号），
     * 不加引号则两边都按大小写不敏感处理，配合 MyBatis 的下划线转驼峰即可正确映射。
     *
     * <p>这里**不像日志域那样补齐"没有数据的项"**：日志的级别是封闭枚举，可以穷举后补零；
     * 事件名是开放集合（客户端可自由新增），没有"全集"可补，硬补只会补出一个过时的清单。
     *
     * <p><b>{@code tenantId} 是必传参数，且条件写在最前面 —— 写成可选（{@code <if>}）会留下
     * 一个"忘记传租户就统计全库"的口子，而返回值只是一个计数，几乎不会被察觉。</b>
     * 与 {@code AuditLogMapper#countByAction}、{@code ClientLogMapper#countByLevel} 同一个坑。
     */
    @Select("""
            <script>
            SELECT event_name AS event_name, COUNT(*) AS event_name_count
            FROM bw_client_event
            WHERE tenant_id = #{tenantId}
              AND server_time &gt;= #{startTime} AND server_time &lt;= #{endTime}
            <if test="app != null and app != ''">
                AND app = #{app}
            </if>
            GROUP BY event_name
            ORDER BY event_name
            </script>
            """)
    List<ClientEventNameCount> countByEventName(@Param("tenantId") long tenantId,
                                                @Param("app") String app,
                                                @Param("startTime") long startTime,
                                                @Param("endTime") long endTime);

    /**
     * 批量插入。
     *
     * <p><b>为什么不用循环调用 {@code insert}：</b>那会产生 N 条 SQL 与 N 次网络往返。
     * 一次上报最多 500 条，逐条插入在开发机上无感，到真实网络里就是 500 倍的延迟 ——
     * 而事件上报恰恰是"最不该拖慢客户端"的通道。
     *
     * <p>返回受影响行数而非回填主键：事件写完之后没人关心它自增到了几号，
     * 为了拿回主键而放弃批量，是拿确定的性能换不确定的便利。
     *
     * <p><b>调用方必须保证集合非空</b>——{@code <foreach>} 在空集合上会生成
     * {@code INSERT ... VALUES} 这样的残句，直接 SQL 语法错误。
     */
    @Insert("""
            <script>
            INSERT INTO bw_client_event
                (tenant_id, event_id, event_name, app, user_id, device_id, session_id, route,
                 app_release, props_json, props_version, client_time, server_time, ip)
            VALUES
            <foreach collection="list" item="it" separator=",">
                (#{it.tenantId}, #{it.eventId}, #{it.eventName}, #{it.app}, #{it.userId},
                 #{it.deviceId}, #{it.sessionId}, #{it.route}, #{it.appRelease}, #{it.propsJson},
                 #{it.propsVersion}, #{it.clientTime}, #{it.serverTime}, #{it.ip})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<ClientEventEntity> list);

}
