package com.bluewhale.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前端用户行为埋点实体，对应 {@code behavior_log} 表（REQ-SEC-006）。
 *
 * <p>由前端 {@code whale-log-sdk} 行为埋点经 {@code POST /audit/log/behavior/batch}
 * 批量上报，whale-audit 落库。</p>
 * <p><b>不继承 {@code BaseEntity}</b>：append-only（无 {@code update_time} / {@code deleted}）。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("behavior_log")
public class BehaviorLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
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
