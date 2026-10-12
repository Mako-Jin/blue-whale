package com.bluewhale.sts.log.clientlog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户端日志实体，对应表 {@code bw_client_log}。
 *
 * <p>刻意**不继承任何 BaseEntity**：日志是 append-only 的，没有"更新人/更新时间"，
 * 也没有逻辑删除 —— 硬塞一个含这些字段的基类，只会让表上多出四个永远落不了值的列，
 * 还会误导后来者以为日志可以被"删除"。
 */
@Getter
@Setter
@TableName("bw_client_log")
public class ClientLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键。
     *
     * <p><b>可空，且 NULL 有确切含义：上报时未登录，这条日志不属于任何租户。</b>
     * 上报接口是公开的（登录页出错也要能发出来），因此服务端拿不到身份时只能记空。
     *
     * <p><b>归属一律取服务端会话，不取上报体里的任何字段。</b>
     * 报告体里的 {@code app} / {@code sessionId} / 上下文对象全部由客户端提供、可伪造；
     * 用它们判断归属，等于让上报方自己决定这条数据算谁的。
     *
     * <p>字段名与列名同构（{@code tenantId → tenant_id}），刻意不加 {@code @TableField} ——
     * 理由与 {@link #appRelease} 一致。
     */
    private Long tenantId;

    private String app;

    private String level;

    private String namespace;

    private String message;

    /** JSON 字符串（数组） */
    private String dataJson;

    /** JSON 字符串（对象） */
    private String contextJson;

    private String traceId;

    private String sessionId;

    private String route;

    /**
     * 发布版本号。对外（JSON / VO）字段名是 {@code release}，数据库列是 {@code app_release}。
     *
     * <p><b>为什么不叫 release：</b>{@code RELEASE} 是 MySQL 8 的保留字
     * （见 {@code RELEASE SAVEPOINT}），拿它当列名会直接报 ERROR 1064；
     * 而 H2 不认这个保留字。也就是说用 {@code release} 做列名会表现为
     * "H2 上一切正常、切到 MySQL 就崩"，是只在其中一个库上暴露的隐雷。
     *
     * <p><b>为什么属性名也要改：</b>这里踩过一个更深的坑 ——
     * 最初保留 JSON 名 {@code release} 做属性、只用 {@code @TableField("app_release")}
     * 指定列名，结果 MyBatis-Plus 发现"列名与属性名不一致"，**自动补了别名**：
     * {@code app_release AS release}。别名又把保留字带回来了，MySQL 依然 1064。
     * 对照同一句 SQL 里的 {@code trace_id}、{@code data_json} 都没有别名 ——
     * 因为它们的下划线形式与属性名天然对应，MP 无需补别名。
     *
     * <p>所以正确做法是让属性名与列名走**同一套推导**：属性叫 {@code appRelease}，
     * 由全局的 {@code map-underscore-to-camel-case} 推导出 {@code app_release}，
     * 与 {@code traceId → trace_id} 完全同构，不产生别名。
     * 加 {@code @TableField} 反而会重新触发别名逻辑，因此**刻意不加**。
     *
     * <p>对外字段名不受影响：VO 与契约里的 {@code release} 保持不变，
     * 映射在 {@code ClientLogService#toVO} 里显式完成。
     */
    private String appRelease;

    /** 客户端时间戳，仅供展示 */
    private Long clientTime;

    /** 服务端接收时间戳，排序与过滤的唯一依据 */
    private Long serverTime;

    private String ip;

}
