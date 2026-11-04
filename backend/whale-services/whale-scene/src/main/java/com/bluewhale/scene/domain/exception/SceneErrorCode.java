package com.bluewhale.scene.domain.exception;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 场景服务业务错误码（HTTP 状态由全局异常处理器映射）。
 *
 * <p>段位约定：{@code 400xx} 业务校验失败，{@code 403xx} 数据权限不足，
 * {@code 404xx} 资源不存在（跨租户一律按不存在处理），{@code 409xx} 状态冲突。</p>
 *
 * @author BlueWhale
 */
public final class SceneErrorCode {

    /** 场景标识非法 */
    public static final int SCENE_KEY_INVALID = 40001;

    /** 场景名称为空或超长 */
    public static final int SCENE_NAME_INVALID = 40002;

    /** 节点字段非法（标识 / 类型 / 坐标 / 深度） */
    public static final int NODE_INVALID = 40003;

    /** 节点树存在环 */
    public static final int NODE_CYCLE = 40004;

    /** 节点树深度超限 */
    public static final int NODE_DEPTH_EXCEEDED = 40005;

    /** 节点数超限 */
    public static final int NODE_COUNT_EXCEEDED = 40006;

    /** 发布前校验失败：缺少可跳转入口 */
    public static final int BINDING_REQUIRED = 40007;

    /** 入口地址非法（协议 / 内网 / 开放重定向） */
    public static final int BINDING_URL_INVALID = 40008;

    /** 入口类型非法 */
    public static final int BINDING_TYPE_INVALID = 40009;

    /** 可见性规则非法 */
    public static final int VISIBILITY_INVALID = 40010;

    /** 节点存在子节点，禁止删除 */
    public static final int NODE_HAS_CHILDREN = 40011;

    /** 场景不存在（含跨租户） */
    public static final int SCENE_NOT_FOUND = 40401;

    /** 节点不存在 */
    public static final int NODE_NOT_FOUND = 40402;

    /** 节点标识在场景内重复 */
    public static final int NODE_KEY_CONFLICT = 40901;

    /** 场景标识在租户内重复 */
    public static final int SCENE_KEY_CONFLICT = 40902;

    /** 场景当前状态不允许该操作 */
    public static final int SCENE_STATE_CONFLICT = 40903;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(SCENE_KEY_INVALID, "场景标识非法（小写字母开头，可含数字与连字符，长度 2-64）"),
            Map.entry(SCENE_NAME_INVALID, "场景名称为空或超过 128 个字符"),
            Map.entry(NODE_INVALID, "节点标识、类型或坐标非法"),
            Map.entry(NODE_CYCLE, "节点树存在环，请检查父节点引用"),
            Map.entry(NODE_DEPTH_EXCEEDED, "节点树深度超过上限"),
            Map.entry(NODE_COUNT_EXCEEDED, "场景节点数超过上限"),
            Map.entry(BINDING_REQUIRED, "发布前至少需要一个已启用的入口节点"),
            Map.entry(BINDING_URL_INVALID, "入口地址非法：仅允许平台内路径或 http/https 外链，且禁止内网地址"),
            Map.entry(BINDING_TYPE_INVALID, "入口类型非法"),
            Map.entry(VISIBILITY_INVALID, "可见性规则非法"),
            Map.entry(NODE_HAS_CHILDREN, "节点存在子节点，禁止删除"),
            Map.entry(SCENE_NOT_FOUND, "场景不存在或无权访问"),
            Map.entry(NODE_NOT_FOUND, "节点不存在或无权访问"),
            Map.entry(NODE_KEY_CONFLICT, "节点标识在场景内已存在"),
            Map.entry(SCENE_KEY_CONFLICT, "场景标识在租户内已存在"),
            Map.entry(SCENE_STATE_CONFLICT, "场景当前状态不允许该操作"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private SceneErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "场景操作失败");
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }
}
