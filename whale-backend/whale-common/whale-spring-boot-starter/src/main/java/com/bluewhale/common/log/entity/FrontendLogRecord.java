package com.bluewhale.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前端开发者日志实体，对应 {@code frontend_log} 表（REQ-SEC-006）。
 *
 * <p>由前端 {@code whale-log-sdk} 劫持 {@code console} 后经 {@code POST /audit/log/frontend/batch}
 * 批量上报，whale-audit 落库。</p>
 * <p><b>不继承 {@code BaseEntity}</b>：append-only（无 {@code update_time} / {@code deleted}）。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("frontend_log")
public class FrontendLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 上报用户ID（token 存在时由网关回填） */
    private Long userId;

    /** 租户ID（网关可信注入，缺失为 NULL） */
    private Long tenantId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 页面URL（query 已掩码） */
    private String pageUrl;

    /** 日志级别：DEBUG/INFO/WARN/ERROR */
    private String logLevel;

    /** 日志内容（超限截断，敏感键掩码） */
    private String message;

    /** 错误堆栈（ERROR 时） */
    private String stack;

    /** 浏览器/UA 摘要 */
    private String browser;

    /** 创建时间（前端产生时间约为上报时间） */
    private LocalDateTime createTime;
}
