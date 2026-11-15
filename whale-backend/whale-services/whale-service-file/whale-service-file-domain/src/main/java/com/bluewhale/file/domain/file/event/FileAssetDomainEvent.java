package com.bluewhale.file.domain.file.event;

import com.bluewhale.common.domain.event.DomainEvent;

/**
 * 文件资产领域事件。
 *
 * <p>描述「已经发生」的文件生命周期事实（锁定 / 解锁 / 删除 / 恢复），只承载标识与必要上下文，
 * 不携带聚合对象与框架类型。事件由应用层在业务操作成功后构造并发布，同一操作只发一条。</p>
 *
 * @param fileId 文件主键
 * @param changeType 变更类型
 * @param tenantId 发布时的租户上下文；无租户态时为 {@code null}
 * @param bucket 桶名称
 * @param objectKey 对象键
 * @param uploaderId 文件上传者
 * @param operatorId 本次操作人；内部调用无操作人时为 {@code null}
 * @author BlueWhale
 */
public record FileAssetDomainEvent(Long fileId, ChangeType changeType, Long tenantId, String bucket,
                                   String objectKey, Long uploaderId, Long operatorId)
        implements DomainEvent {

    /**
     * 规范化事件载荷：变更类型不可为空，其余字段允许为空以适配内部调用
     *
     * @throws IllegalArgumentException 变更类型为 {@code null} 时抛出
     */
    public FileAssetDomainEvent {
        if (changeType == null) {
            throw new IllegalArgumentException("文件事件变更类型不能为空");
        }
    }

    /**
     * 变更类型
     *
     * @author BlueWhale
     */
    public enum ChangeType {

        /** 文件被归档锁定 */
        LOCKED,

        /** 文件解除锁定 */
        UNLOCKED,

        /** 文件被逻辑删除（进入回收站） */
        DELETED,

        /** 文件从回收站恢复 */
        RESTORED
    }
}
