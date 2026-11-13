package com.bluewhale.auth.application.model;

/**
 * 人员生命周期动作枚举：OA 人事异动审批通过后同步 Auth 账号的动作类型。
 *
 * <p>取代散落在 {@code UserLifecycleAppServiceImpl} 中的 {@code ACTION_*} 字符串常量，
 * 非法动作在映射处统一 400，避免裸字符串 switch 的拼写漂移。</p>
 *
 * @author BlueWhale
 */
public enum UserLifecycleAction {

    /** 入职：未提供用户 ID 时建号并生成一次性临时口令；已提供时启用并同步部门 */
    ONBOARD,

    /** 转正：仅确保账号处于启用态（Auth 不维护试用/正式状态） */
    REGULAR,

    /** 调岗：仅变更部门，保留昵称/联系方式/头像 */
    TRANSFER,

    /** 离职：停用账号但不删除，并撤销存量令牌 */
    LEAVE,

    /** 返聘：启用账号，可选同步新部门 */
    REHIRE;

    /**
     * 将 OA 命令中的原始动作字符串映射为枚举（大小写不敏感，前后空白忽略）
     *
     * @param raw 原始动作字符串（如 {@code "onboard"}）
     * @return 对应枚举值
     * @throws IllegalArgumentException 空值或未识别的动作时抛出（由应用服务转 BizException）
     */
    public static UserLifecycleAction fromAction(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("生命周期动作不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的生命周期动作：" + raw);
        }
    }
}
