package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 慰问帮扶类型。
 *
 * <p>类型不是自由文本：上限按「同人同年同类型」计算，若允许随手填写
 * 「困难 / 困难帮扶 / 困难补助」，同一笔钱会被拆成多个类型而绕开次数上限。</p>
 *
 * @author BlueWhale
 */
public enum ReliefType {

    /** 困难帮扶 */
    DIFFICULTY("困难帮扶"),

    /** 丧病慰问 */
    CONDOLENCE("丧病慰问"),

    /** 医疗救助 */
    MEDICAL("医疗救助"),

    /** 子女教育 */
    EDUCATION("子女教育"),

    /** 其他 */
    OTHER("其他");

    /** 中文标签 */
    private final String label;

    /**
     * 构造类型。
     *
     * @param label 中文标签
     */
    ReliefType(String label) {
        this.label = label;
    }

    /**
     * 查询中文标签。
     *
     * @return 中文标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 解析类型；空值与未知值一律拒绝，避免上限统计落进兜底类型。
     *
     * @param value 类型文本
     * @return 类型枚举
     */
    public static ReliefType parse(String value) {
        if (value == null || value.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "慰问类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "慰问类型不合法：" + value);
        }
    }
}
