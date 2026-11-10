package com.bluewhale.common.domain.valueobject;

import java.io.Serializable;

/**
 * 值对象标记接口：不可变，无独立身份，以属性值判定相等。
 *
 * <p>实现方可直接使用 Java {@code record}（自带值语义与不可变性），也可继承
 * {@link BaseValueObject} 自行实现 {@code equals}/{@code hashCode}/{@code toString}。</p>
 *
 * @author BlueWhale
 */
public interface ValueObject extends Serializable {
}
