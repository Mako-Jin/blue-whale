package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台写操作确认记录实体，对应表 {@code agent_operation}。
 *
 * <p>一条记录即一个一次性确认令牌：创建时为 {@code PENDING}，用户确认后原子置为
 * {@code CONFIRMED} 再执行下游写操作，最终落 {@code SUCCESS}/{@code FAILED}；
 * 用户拒绝、超时分别落 {@code REJECTED}/{@code EXPIRED}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_operation")
public class AgentOperation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 确认令牌ID（UUID，全局唯一） */
    private String confirmationId;

    /** 发起用户ID */
    private Long userId;

    /** 来源会话ID（可空） */
    private Long conversationId;

    /** 操作名（见 PlatformOperationCatalog） */
    private String operation;

    /** 参数 JSON */
    private String paramsJson;

    /** 参数 SHA-256 哈希（审计比对，不回显敏感原值） */
    private String paramsHash;

    /** 执行所需权限码 */
    private String permission;

    /** 目标服务（配置键，如 form） */
    private String target;

    /** 状态：PENDING/CONFIRMED/REJECTED/EXPIRED/SUCCESS/FAILED */
    private String status;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 确认人用户ID（令牌绑定发起人，当前与 userId 一致） */
    private Long confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 下游响应 JSON */
    private String resultJson;

    /** 结果码：0 成功 / 500 失败 */
    private Integer resultCode;

    /** 执行耗时（毫秒） */
    private Integer elapsedMs;

    /** 失败原因 */
    private String errorMsg;
}
