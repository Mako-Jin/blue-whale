package com.bluewhale.common.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 值对象数组比较辅助方法测试。
 */
class BaseValueObjectTest {

    @Test
    void equalWhenSameReference() {
        Object[] array = {"a", 1};
        assertTrue(new Tags(array).sameArray(array, array));
    }

    @Test
    void equalWhenBothNull() {
        assertTrue(new Tags(new Object[0]).sameArray(null, null));
    }

    @Test
    void notEqualWhenOnlyOneSideNull() {
        assertFalse(new Tags(new Object[0]).sameArray(null, new Object[]{1}));
        assertFalse(new Tags(new Object[0]).sameArray(new Object[]{1}, null));
    }

    @Test
    void notEqualWhenLengthOrElementDiffers() {
        assertFalse(new Tags(new Object[0]).sameArray(new Object[]{1}, new Object[]{1, 2}));
        assertFalse(new Tags(new Object[0]).sameArray(new Object[]{"a"}, new Object[]{"b"}));
    }

    @Test
    void equalWhenElementsMatch() {
        assertTrue(new Tags(new Object[0]).sameArray(new Object[]{"a", 1}, new Object[]{"a", 1}));
    }

    /**
     * 值对象测试替身：暴露数组比较辅助方法。
     */
    private static final class Tags extends BaseValueObject {

        /** 序列化版本号 */
        private static final long serialVersionUID = 1L;

        /**
         * 构造器。
         *
         * @param ignored 占位入参
         */
        private Tags(Object[] ignored) {
        }

        /**
         * 暴露受保护的数组比较方法。
         *
         * @param array1 数组 1
         * @param array2 数组 2
         * @return 比较结果
         */
        private boolean sameArray(Object[] array1, Object[] array2) {
            return super.arraysEqual(array1, array2);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "Tags";
        }
    }
}
