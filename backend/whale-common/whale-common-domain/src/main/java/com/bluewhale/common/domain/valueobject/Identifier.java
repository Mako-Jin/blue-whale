package com.bluewhale.common.domain.valueobject;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.Objects;

/**
 * 标识抽象类：把裸主键包装为有类型的值对象，构造即校验非空。
 *
 * <p>适用于需要区分「用户 ID」「租户 ID」等语义、避免裸 {@code Long}/{@code String} 混用的场景；
 * 若聚合直接以 {@code Long} 表达主键，可不引入本类。</p>
 *
 * @param <T> 底层标识值类型
 * @author BlueWhale
 */
public abstract class Identifier<T> implements ValueObject {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 底层标识值，构造后不可变 */
    private final T value;

    /**
     * 构造器：校验标识值非空
     *
     * @param value 底层标识值
     */
    protected Identifier(T value) {
        if (value == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "标识值不能为空");
        }
        this.value = value;
    }

    /**
     * 获取底层标识值
     *
     * @return 底层标识值
     */
    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Identifier<?> that = (Identifier<?>) obj;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + value + ")";
    }
}
