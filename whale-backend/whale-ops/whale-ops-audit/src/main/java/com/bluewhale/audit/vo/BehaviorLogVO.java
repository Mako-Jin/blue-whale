package com.bluewhale.audit.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前端用户行为埋点视图对象（只读，字段与 {@code behavior_log} 表一致，REQ-SEC-006）
 *
 * @author BlueWhale
 */
@Data
public class BehaviorLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 上报用户ID（token 存在时由网关回填） */
    private Long userId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 事件类型：PAGE_VIEW/CLICK/SUBMIT/CUSTOM */
    private String eventType;

    /** 事件名称，如 用户管理页访问 */
    private String eventName;

    /** 页面URL（query 已掩码） */
    private String pageUrl;

    /** 触发目标（元素标识，如按钮 name/id） */
    private String target;

    /** 事件载荷（JSON，敏感字段已掩码） */
    private String payload;

    /** 创建时间 */
    private LocalDateTime createTime;
}
