package com.bluewhale.iot.service.spec;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.ThingModelItem;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物模型条目静态校验器（数据类型与规格的契约校验）。
 *
 * <p>独立成类以便单测直测（不依赖 Mapper/Spring 上下文）。</p>
 *
 * @author BlueWhale
 */
public final class ThingModelValidator {

    /** 数值型数据类型（规格应含 min/max 之一，未强制） */
    private static final Set<String> NUMERIC_TYPES = Set.of("int", "double");

    /** 工具类，禁止实例化 */
    private ThingModelValidator() {
    }

    /**
     * 校验条目集：属性必须带数据类型；数值属性规格若含 min/max 必须数值型且 min ≤ max。
     *
     * @param items 条目列表（不可为 null）
     */
    public static void validate(List<ThingModelItem> items) {
        for (ThingModelItem item : items) {
            validateOne(item);
        }
    }

    /**
     * 校验单个条目。
     *
     * @param item 条目
     */
    private static void validateOne(ThingModelItem item) {
        if ("property".equals(item.getKind()) && (item.getDataType() == null || item.getDataType().isBlank())) {
            throw new BizException("属性 " + item.getIdentifier() + " 缺少数据类型（dataType 必填）");
        }
        if (item.getSpec() instanceof Map<?, ?> spec) {
            validateNumericSpec(item, spec);
        }
    }

    /**
     * 数值属性规格校验：min/max 必须为数值且 min ≤ max；step 必须为正数。
     *
     * @param item 条目
     * @param spec 规格映射
     */
    private static void validateNumericSpec(ThingModelItem item, Map<?, ?> spec) {
        Object min = spec.get("min");
        Object max = spec.get("max");
        Object step = spec.get("step");
        boolean numeric = item.getDataType() != null && NUMERIC_TYPES.contains(item.getDataType());
        if (min instanceof Number minNum && max instanceof Number maxNum) {
            if (!numeric) {
                throw new BizException("属性 " + item.getIdentifier() + " 为非数值型，不允许配置 min/max");
            }
            if (minNum.doubleValue() > maxNum.doubleValue()) {
                throw new BizException("属性 " + item.getIdentifier() + " 的 min 不能大于 max");
            }
        }
        if (step instanceof Number stepNum && stepNum.doubleValue() <= 0) {
            throw new BizException("属性 " + item.getIdentifier() + " 的 step 必须为正数");
        }
    }
}
