package com.bluewhale.config.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作人信息（网关注入的 X-User-Id / X-Username 头解析而来，内部操作时为空）
 *
 * @author BlueWhale
 */
@Data
public class OperatorInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人用户名 */
    private String operatorName;

    /**
     * 从网关注入的头构造操作人信息。
     *
     * @param userIdHeader   X-User-Id 头（可为空或非法数字）
     * @param usernameHeader X-Username 头（可为空）
     * @return 操作人信息；无法解析时字段为 {@code null}
     */
    public static OperatorInfo from(String userIdHeader, String usernameHeader) {
        OperatorInfo operator = new OperatorInfo();
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                operator.setOperatorId(Long.parseLong(userIdHeader.trim()));
            } catch (NumberFormatException ignored) {
                // 非法数字头视为匿名操作，不阻断业务
            }
        }
        operator.setOperatorName(usernameHeader == null || usernameHeader.isBlank()
                ? null : usernameHeader.trim());
        return operator;
    }
}
