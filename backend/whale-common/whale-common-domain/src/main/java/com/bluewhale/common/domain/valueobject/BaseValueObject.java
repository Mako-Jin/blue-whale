package com.bluewhale.common.domain.valueobject;

import java.util.Objects;

/**
 * 值对象抽象基类：强制实现方显式给出值语义方法，并复用数组比较等通用工具。
 *
 * @author BlueWhale
 */
public abstract class BaseValueObject implements ValueObject {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    /**
     * 辅助方法：按元素逐一比较两个数组
     *
     * @param array1 数组 1
     * @param array2 数组 2
     * @return 两个数组引用相同或长度与元素均相等时返回 true
     */
    protected boolean arraysEqual(Object[] array1, Object[] array2) {
        if (array1 == array2) {
            return true;
        }
        if (array1 == null || array2 == null || array1.length != array2.length) {
            return false;
        }
        for (int i = 0; i < array1.length; i++) {
            if (!Objects.equals(array1[i], array2[i])) {
                return false;
            }
        }
        return true;
    }
}
