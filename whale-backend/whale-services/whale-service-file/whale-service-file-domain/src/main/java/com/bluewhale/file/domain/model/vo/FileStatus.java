package com.bluewhale.file.domain.model.vo;

import com.bluewhale.common.domain.valueobject.ValueObject;

/**
 * 文件状态值对象：与 {@code file_info.status} 列取值一一对应。
 *
 * <p>取值：{@code 0 禁用}、{@code 1 正常}、{@code 2 归档锁定}。归档锁定文件内容不可变更且禁止删除。</p>
 *
 * @author BlueWhale
 */
public enum FileStatus implements ValueObject {

    /** 禁用：不可锁定、不可正常使用 */
    DISABLED(0, "禁用"),

    /** 正常：可下载、可锁定、可删除 */
    NORMAL(1, "正常"),

    /** 归档锁定：不可删除、内容不可变更 */
    LOCKED(2, "归档锁定");

    /** 状态码（落库值） */
    private final int code;

    /** 状态描述 */
    private final String description;

    /**
     * 以状态码与描述构造状态值对象
     *
     * @param code 状态码（落库值）
     * @param description 状态描述
     */
    FileStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 由落库状态码还原状态值对象
     *
     * <p>缺失或未知状态码按 {@link #NORMAL} 处理，兼容存量数据中未显式写状态的行。</p>
     *
     * @param code 落库状态码，可为 {@code null}
     * @return 状态值对象
     */
    public static FileStatus fromCode(Integer code) {
        if (code == null) {
            return NORMAL;
        }
        for (FileStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return NORMAL;
    }

    /**
     * 获取状态码
     *
     * @return 状态码（落库值）
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
}
