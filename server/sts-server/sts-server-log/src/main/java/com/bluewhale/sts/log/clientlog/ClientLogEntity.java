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

    private String release;

    /** 客户端时间戳，仅供展示 */
    private Long clientTime;

    /** 服务端接收时间戳，排序与过滤的唯一依据 */
    private Long serverTime;

    private String ip;

}
