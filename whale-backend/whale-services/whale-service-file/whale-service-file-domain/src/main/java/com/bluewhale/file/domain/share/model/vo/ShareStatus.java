package com.bluewhale.file.domain.share.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

/**
 * 分享状态值对象：与 {@code file_share.status} 列取值一一对应。
 *
 * <p>撤销只改状态、不删行，便于保留分享审计痕迹；已撤销的令牌不再可访问。</p>
 *
 * @author BlueWhale
 */
public enum ShareStatus implements ValueObject {

    /** 已撤销：令牌立即失效 */
    REVOKED(0, "已撤销"),

    /** 有效：在有效期与次数限制内可访问 */
    ACTIVE(1, "有效");

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
    ShareStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 由落库状态码还原状态值对象
     *
     * @param code 落库状态码，可为 {@code null}
     * @return 状态值对象；空值按 {@link #ACTIVE} 处理
     * @throws DomainException 状态码未定义时抛出 {@code SYSTEM_ERROR}
     */
    public static ShareStatus fromCode(Integer code) {
        if (code == null) {
            return ACTIVE;
        }
        for (ShareStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new DomainException(ErrorCode.SYSTEM_ERROR, "未知分享状态码: " + code);
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
