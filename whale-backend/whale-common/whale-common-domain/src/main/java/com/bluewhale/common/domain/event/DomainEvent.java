package com.bluewhale.common.domain.event;

import java.io.Serializable;

/**
 * 领域事件标记接口：描述聚合中「已经发生」的业务事实。
 *
 * <p>事件仅承载业务语义与必要标识，不携带框架类型。事件元数据（事件 ID、发生时间、事件类型）
 * 可由 {@link AbstractDomainEvent} 统一提供；若以 {@code record} 直接表达事件载荷，实现本接口即可。</p>
 *
 * @author BlueWhale
 */
public interface DomainEvent extends Serializable {
}
