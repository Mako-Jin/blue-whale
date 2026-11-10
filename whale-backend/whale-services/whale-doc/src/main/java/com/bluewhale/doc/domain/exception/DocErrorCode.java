package com.bluewhale.doc.domain.exception;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 文档服务业务错误码（M0 冻结，M1 实现按此返回）。
 *
 * <p>错误码段位约定：{@code 4xx01} 表示业务校验失败，{@code 404xx} 表示资源不存在，
 * {@code 403xx} 表示数据权限不足，{@code 409xx} 表示并发/状态冲突。HTTP 状态统一由
 * 全局异常处理器映射，响应体 {@code Result.code} 保留业务码，便于前端分支处理。</p>
 *
 * @author BlueWhale
 */
public final class DocErrorCode {

    /** 乐观锁冲突：保存时 baseRevision 与服务端 revision 不一致 */
    public static final int REVISION_CONFLICT = 40901;

    /** 页面存在活跃协作会话，普通 REST 保存被拒绝（M2） */
    public static final int COLLAB_ACTIVE = 40902;

    /** 实时协作依赖（Redis / WS）暂不可用，前端应回退 M1 自动保存（M2） */
    public static final int COLLAB_UNAVAILABLE = 40903;

    /** 空间数据权限不足 */
    public static final int SPACE_ACCESS_DENIED = 40301;

    /** 页面不存在或不可见 */
    public static final int PAGE_NOT_FOUND = 40401;

    /** 正文超过配置的最大体积 */
    public static final int CONTENT_TOO_LARGE = 40001;

    /** 移动页面形成父子环 */
    public static final int TREE_CYCLE = 40002;

    /** 页面层级超过配置的最大深度 */
    public static final int TREE_DEPTH_EXCEEDED = 40003;

    /** 空间必须保留至少一个 OWNER */
    public static final int LAST_OWNER_REQUIRED = 40004;

    /** 空间内 slug 重复 */
    public static final int SLUG_DUPLICATED = 40005;

    /** 附件超过配置的最大体积 */
    public static final int ATTACHMENT_TOO_LARGE = 40006;

    /** 回收站恢复时原父页面已不可用（M2） */
    public static final int RECYCLE_PARENT_INVALID = 40007;

    /** 回收站操作必须从子树根节点发起（M2） */
    public static final int RECYCLE_NOT_ROOT = 40008;

    /** 协作 ticket 缺失、过期或已被使用（M2） */
    public static final int COLLAB_TICKET_INVALID = 40101;

    /** 协作 WebSocket 帧非法或超出体积上限（M2） */
    public static final int COLLAB_FRAME_INVALID = 40009;

    /** 模板不存在或已停用（M3） */
    public static final int TEMPLATE_NOT_FOUND = 40402;

    /** 分享链接不存在、已撤销或已过期（M3） */
    public static final int SHARE_INVALID = 40403;

    /** 导入文件为空、格式不支持或体积超限（M3） */
    public static final int IMPORT_FILE_INVALID = 40010;

    /** 当前页面状态不允许提交审批（M3） */
    public static final int APPROVAL_STATE_CONFLICT = 40904;

    /** 发布审批未启用或流程定义未配置（M3） */
    public static final int APPROVAL_UNAVAILABLE = 40011;

    /** 外部服务（消息/流程/档案）暂不可用（M3，仅告警降级） */
    public static final int EXTERNAL_SERVICE_UNAVAILABLE = 50201;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(REVISION_CONFLICT, "页面已被他人更新，请刷新后重试"),
            Map.entry(COLLAB_ACTIVE, "页面正在协作编辑中，请通过实时协作保存"),
            Map.entry(COLLAB_UNAVAILABLE, "实时协作暂不可用，已回退自动保存"),
            Map.entry(SPACE_ACCESS_DENIED, "无权访问该文档空间"),
            Map.entry(PAGE_NOT_FOUND, "页面不存在或无权访问"),
            Map.entry(CONTENT_TOO_LARGE, "页面正文超过体积上限"),
            Map.entry(TREE_CYCLE, "不能将页面移动到自身或子页面下"),
            Map.entry(TREE_DEPTH_EXCEEDED, "页面层级超过上限"),
            Map.entry(LAST_OWNER_REQUIRED, "空间必须保留至少一名所有者"),
            Map.entry(SLUG_DUPLICATED, "空间内页面路径已被占用"),
            Map.entry(ATTACHMENT_TOO_LARGE, "附件超过体积上限"),
            Map.entry(RECYCLE_PARENT_INVALID, "原父页面已被删除或不可用，无法恢复"),
            Map.entry(RECYCLE_NOT_ROOT, "请从回收站列表中的根节点执行该操作"),
            Map.entry(COLLAB_TICKET_INVALID, "协作凭证无效或已过期，请重新连接"),
            Map.entry(COLLAB_FRAME_INVALID, "协作数据帧非法或超过体积上限"),
            Map.entry(TEMPLATE_NOT_FOUND, "模板不存在或已停用"),
            Map.entry(SHARE_INVALID, "分享链接无效、已撤销或已过期"),
            Map.entry(IMPORT_FILE_INVALID, "导入文件为空、格式不支持或体积超限"),
            Map.entry(APPROVAL_STATE_CONFLICT, "当前页面状态不允许执行该审批操作"),
            Map.entry(APPROVAL_UNAVAILABLE, "发布审批未启用或流程定义未配置"),
            Map.entry(EXTERNAL_SERVICE_UNAVAILABLE, "外部服务暂不可用，请稍后重试"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private DocErrorCode() {
    }

    /**
     * 查询错误码的默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知错误码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "文档操作失败");
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
