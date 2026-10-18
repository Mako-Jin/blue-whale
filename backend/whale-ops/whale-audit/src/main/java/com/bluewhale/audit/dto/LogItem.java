package com.bluewhale.audit.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 前端上报日志条目（前端 console 日志与用户行为埋点共用，REQ-SEC-006）。
 *
 * <p>字段按 spec §3.1 的 {@code frontend_log} / {@code behavior_log} 表列设计，
 * 两类通道各取所需子集；全部字段可空，由上报方按需填写。</p>
 *
 * @author BlueWhale
 */
@Data
public class LogItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志级别：DEBUG/INFO/WARN/ERROR（前端 console 通道使用） */
    private String logLevel;

    /** 日志内容（前端 console 通道使用，超限截断，敏感键掩码） */
    private String message;

    /** 错误堆栈（ERROR 时上报，前端 console 通道使用） */
    private String stack;

    /** 浏览器/UA 摘要（前端 console 通道使用） */
    private String browser;

    /** 事件类型：PAGE_VIEW/CLICK/SUBMIT/CUSTOM（行为埋点通道使用） */
    private String eventType;

    /** 事件名称，如 用户管理页访问（行为埋点通道使用） */
    private String eventName;

    /** 触发目标（元素标识，如按钮 name/id，行为埋点通道使用） */
    private String target;

    /** 事件载荷（JSON，敏感字段已掩码，行为埋点通道使用） */
    private String payload;

    /** 前端产生时间（epoch 毫秒）；为空时由数据库默认时间兜底 */
    private Long timestamp;
}
