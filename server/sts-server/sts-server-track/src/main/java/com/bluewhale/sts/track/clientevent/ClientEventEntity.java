package com.bluewhale.sts.track.clientevent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户端行为事件实体，对应表 {@code bw_client_event}。
 *
 * <p>刻意**不继承任何 BaseEntity**：行为事件是 append-only 的，没有"更新人/更新时间"，
 * 也没有逻辑删除 —— 硬塞一个含这些字段的基类，只会让表上多出四个永远落不了值的列，
 * 还会误导后来者以为事件可以被"删除"。
 *
 * <p>与客户端日志（{@code bw_client_log}）的关系：两者都是"尽力而为"的上报通道，
 * 但**语义完全不同**。日志回答"系统当时在干什么"，是一次请求的技术切片，量极大、可丢弃；
 * 事件回答"用户做了什么"，是可被产品/增长侧长期消费的**事实**，因此多出
 * {@code eventId}（幂等）、{@code userId}/{@code deviceId}（身份缝合）、
 * {@code props}（业务属性）与 {@code propsVersion}（属性结构版本）。
 */
@Getter
@Setter
@TableName("bw_client_event")
public class ClientEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键。
     *
     * <p><b>可空，且 NULL 有确切含义：上报时未登录，这条事件不属于任何租户。</b>
     * 上报接口是公开的，因此服务端拿不到身份时只能记空。
     *
     * <p><b>归属一律取服务端会话，不取上报体里的任何字段</b> —— 尤其**不是** {@link #userId}：
     * 那一列由客户端填写、可伪造。拿它当归属依据，同一个 {@code userId} 就能读到别的租户的
     * 行为记录，而正常数据上完全看不出区别。详见建表脚本 V7 的文件头。
     */
    private Long tenantId;

    /** 客户端生成的事件唯一 id，幂等键（列非唯一，见建表脚本注释） */
    private String eventId;

    /** 事件名，形如 order.submit */
    private String eventName;

    private String app;

    /**
     * 登录用户标识，登录前为空。
     *
     * <p><b>这是上报方自己填的值，只用于展示与排查，不可用于鉴权或归属判定</b> ——
     * 归属看 {@link #tenantId}。本仓的后端从未把它当作"当前用户"。
     */
    private String userId;

    /** 匿名设备标识，登录前靠它、登录后靠它做身份缝合 */
    private String deviceId;

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
     * 对照同一句 SQL 里的 {@code event_id}、{@code props_json} 都没有别名 ——
     * 因为它们的下划线形式与属性名天然对应，MP 无需补别名。
     *
     * <p>所以正确做法是让属性名与列名走**同一套推导**：属性叫 {@code appRelease}，
     * 由全局的 {@code map-underscore-to-camel-case} 推导出 {@code app_release}，
     * 与 {@code eventId → event_id} 完全同构，不产生别名。
     * 加 {@code @TableField} 反而会重新触发别名逻辑，因此**刻意不加**。
     *
     * <p>对外字段名不受影响：VO 与契约里的 {@code release} 保持不变，
     * 映射在 {@link ClientEventService} 的 {@code toVO} 里显式完成。
     */
    private String appRelease;

    /** 事件属性的 JSON 字符串 */
    private String propsJson;

    /**
     * 属性结构版本。props 的结构一旦变化（改名、改语义、删字段）必须递增 ——
     * 否则新旧客户端写进同一列的两套结构会被消费方无差别解读，得到静默错误的分析结果。
     */
    private Integer propsVersion;

    /** 客户端时间戳，不可信，仅供展示 */
    private Long clientTime;

    /** 服务端接收时间戳，排序与过滤的唯一依据 */
    private Long serverTime;

    private String ip;

}
