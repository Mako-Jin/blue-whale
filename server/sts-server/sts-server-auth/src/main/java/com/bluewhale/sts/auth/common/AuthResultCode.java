package com.bluewhale.sts.auth.common;

import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;

/**
 * 权限域错误码。
 *
 * <p><b>编码规则沿用全仓统一约定（{@code docs/decisions/0005} 第 7 条）：</b>
 * {@code 5 位 = HTTP 状态码(3 位) + 业务细分(2 位)}。
 * 公共枚举 {@link ResultCode} 只占用每类别的 {@code 00} 号（如 {@code 40400 资源不存在}），
 * 因此**域内细分一律从 {@code 01} 起编号** —— 这与公共码天然错开，
 * 从规则上消除了"两个枚举用了同一个码值"的可能。
 *
 * <p><b>为什么不像参照实现那样另起一套码段：</b>
 * 参考工程 {@code SystemTerminator} 的 {@code AuthErrorCodeEnums} 把认证错误放在
 * {@code 004xxx} 段，其中 {@code 004000} 与公共 {@code ResultEnums.PARAM_ERROR} 撞了码 ——
 * 同一串数字在两层含义之间来回摇摆，排查时无法从码值判断归属。
 * 沿用 {@code HTTP3+Biz2} 后，"用户不存在"落在 {@code 404} 类、"用户名重复"落在
 * {@code 409} 类，**看到码就知道该归咎于调用方还是服务端**，不必回查文档。
 *
 * <p>各段位的分配：
 * <ul>
 *   <li>{@code 400xx} 请求参数 / 数据形态问题</li>
 *   <li>{@code 401xx} 认证（凭证、令牌、会话、验证码、多因子）</li>
 *   <li>{@code 403xx} 已认证但无权限</li>
 *   <li>{@code 404xx} 目标实体不存在</li>
 *   <li>{@code 409xx} 唯一性 / 状态冲突</li>
 * </ul>
 */
public enum AuthResultCode {

    // ===== 400xx 参数 =====
    /** 入参语义不成立（不是校验注解能表达的，例如"分页条件互斥"） */
    PARAM_INVALID(40001, "请求参数不合法"),
    /** 必填关联数据不存在（如引用了不存在的父级） */
    PARAM_REFERENCE_MISSING(40002, "引用的关联数据不存在"),
    /**
     * 口令不满足强度策略。
     *
     * <p>归入 400 而非 401：它说的是"这次请求带的口令不合规"，
     * 属调用方要改的东西，与"凭证对不对"（401）是两码事。
     * 具体缺哪几项由 {@code PasswordPolicy.describe()} 动态拼进文案 —— 见 {@code UserService}。
     */
    PASSWORD_POLICY_VIOLATION(40003, "口令强度不足"),

    // ===== 401xx 认证 =====
    /** 用户名或密码错误 —— **刻意不区分"用户不存在"与"密码错误"**，避免账号枚举 */
    BAD_CREDENTIALS(40101, "用户名或密码错误"),
    /** 账号被锁定（连续失败触发，或管理员锁定） */
    ACCOUNT_LOCKED(40102, "账号已被锁定"),
    /** 账号已停用 */
    ACCOUNT_DISABLED(40103, "账号已停用"),
    /** 令牌无效（签名不符、格式错误、算法不被接受） */
    TOKEN_INVALID(40104, "令牌无效"),
    /** 令牌已过期 */
    TOKEN_EXPIRED(40105, "令牌已过期"),
    /** 会话/刷新令牌已失效（已登出、已撤销、被轮换后重放） */
    SESSION_INVALID(40106, "会话已失效，请重新登录"),
    /** 需要验证码 */
    CAPTCHA_REQUIRED(40107, "需要填写验证码"),
    /** 验证码错误 */
    CAPTCHA_INVALID(40108, "验证码错误"),
    /** 需要多因子认证 */
    MFA_REQUIRED(40109, "需要完成多因子认证"),
    /** 多因子校验码错误 */
    MFA_CODE_INVALID(40110, "多因子校验码错误"),
    /** 密码已过期，需改密后继续 */
    PASSWORD_EXPIRED(40111, "密码已过期，请修改密码"),

    // ===== 403xx 授权 =====
    /** 无该操作权限（已认证） */
    NO_PERMISSION(40301, "没有该操作权限"),

    // ===== 404xx 不存在 =====
    USER_NOT_FOUND(40401, "用户不存在"),
    TENANT_NOT_FOUND(40402, "租户不存在"),
    ROLE_NOT_FOUND(40403, "角色不存在"),
    ORGANIZATION_NOT_FOUND(40404, "组织不存在"),
    USER_GROUP_NOT_FOUND(40405, "用户组不存在"),
    RESOURCE_NOT_FOUND(40406, "资源不存在"),
    /** 该角色并未持有这个资源 —— 撤销一个不存在的授权 */
    ROLE_GRANT_NOT_FOUND(40407, "该角色未持有此资源"),
    /**
     * 该角色并未授给这个成员 —— 撤销一段不存在的成员关系。
     *
     * <p>与 {@link #ROLE_GRANT_NOT_FOUND} 是同一条规则用在另一条边上，分开编号而不是复用：
     * 两者说的是**两个不同的聚合**（角色的授权集合 / 角色的成员集合），
     * 复用同一个码值会让前端只能提示一句含糊的"未持有"，而它需要知道该刷新哪个列表。
     */
    ROLE_MEMBER_GRANT_NOT_FOUND(40408, "该角色未授给此成员"),
    /**
     * 该用户组并未包含这个成员 —— 移除一段不存在的组成员关系。
     *
     * <p>与 {@link #ROLE_MEMBER_GRANT_NOT_FOUND} 是同一条规则的第三处应用
     * （前两处是角色的资源授权、角色的成员关系），同样**不复用码值**：
     * 三者破坏的是三个不同的聚合，前端据此决定该刷新哪个列表 ——
     * 一句含糊的"未持有"会让它刷错对象。
     */
    USER_GROUP_MEMBER_NOT_FOUND(40409, "该用户组未包含此成员"),

    // ===== 409xx 冲突 =====
    USERNAME_EXISTS(40901, "用户名已存在"),
    USER_EMAIL_EXISTS(40902, "邮箱已被占用"),
    USER_PHONE_EXISTS(40903, "手机号已被占用"),
    TENANT_CODE_EXISTS(40904, "租户编码已存在"),
    TENANT_NAME_EXISTS(40905, "租户名称已存在"),
    ROLE_CODE_EXISTS(40906, "角色编码已存在"),
    ROLE_NAME_EXISTS(40907, "角色名称已存在"),
    ORG_CODE_EXISTS(40908, "组织编码已存在"),
    ORG_NAME_EXISTS(40909, "组织名称已存在"),
    USER_GROUP_CODE_EXISTS(40910, "用户组编码已存在"),
    USER_GROUP_NAME_EXISTS(40911, "用户组名称已存在"),
    RESOURCE_CODE_EXISTS(40912, "资源标识已存在"),
    /** 父子关系成环（把节点挂到自己的后代下） */
    HIERARCHY_CYCLE(40913, "层级关系不合法"),
    /**
     * 新口令与"当前口令"或"最近若干个历史口令"重复。
     *
     * <p>归入 409（冲突）而不是 400：口令本身完全合法，只是与已用过的撞了。
     * 这是"状态冲突"，不是"形态错误"—— 前端据此提示"请换一个没用过的口令"，
     * 而不是"请检查口令格式"。
     */
    PASSWORD_REUSED(40914, "该口令已使用过，请更换"),
    /**
     * 资源已被角色引用，拒绝删除。
     *
     * <p>归入 409 而不是 400：请求本身完全合法，是**当前状态**不允许这么做。
     *
     * <p><b>为什么不级联删掉授权、也不静默软删：</b>
     * 资源是逻辑删除，删掉之后 {@code AuthorizationMapper} 的 JOIN 会自动把它排除 ——
     * 也就是说，一次 delete 会让若干角色**静默地**少掉一项权限。这种"改了配置却没人
     * 知道改了哪些授权"的效果，正是权限系统最该避免的。让调用方先显式撤销授权
     * （看清受影响的是谁），再删资源，代价是两步操作，换来的是每一步都可解释。
     * 与租户"存在子租户时拒绝删除"是同一条原则：**不提供藏起来的级联**。
     */
    RESOURCE_IN_USE(40915, "资源已被角色引用，请先撤销授权"),
    /**
     * 角色仍被成员（用户或用户组）持有，拒绝删除。
     *
     * <p>与 {@link #RESOURCE_IN_USE} 同一条原则，但方向相反，值得说清为什么也拒绝：
     * 删角色会让**持有它的那些人**立刻少掉一批权限（{@code AuthorizationMapper} 的 JOIN
     * 要求 {@code ro.deleted = 0}）。管理员的本意可能只是"清理一个不再用的角色"，
     * 实际效果却是"某些人突然点不动某个按钮了"，而且没有任何界面会提示这件事发生过。
     * 要求先解除成员关系（那时能看到受影响的是哪些人），再删角色。
     *
     * <p><b>角色自身持有的授权不构成拒绝理由</b>，与 {@link #RESOURCE_IN_USE} 不同：
     * 授权关系是**角色的组成部分**，角色没了，它的授权集合也就没有意义了。
     * 删除时会一并逻辑删除这些授权行 —— 这不是"藏起来的级联"，而是删掉一个聚合的
     * 内部边；被拒绝的那一条针对的是**别的聚合**（用户的权限）。
     */
    ROLE_IN_USE(40916, "角色仍被成员持有，请先解除成员关系"),
    /**
     * 系统内置角色不可删除 / 不可停用。
     *
     * <p>{@code category = SYSTEM} 的角色由种子数据建立（如 {@code SUPER_ADMIN}），
     * 是"把系统管理起来"的起点。删掉或停用它，会让**所有人**同时失去管理能力，
     * 而恢复手段只剩直接改库 —— 一次点击造成的后果需要连数据库才能撤销，
     * 这种操作不该由一个 HTTP 接口提供。与"部署级开关不放进业务运行期配置"同源。
     */
    SYSTEM_ROLE_PROTECTED(40917, "系统内置角色不可删除或停用"),
    /**
     * 用户组仍包含成员，拒绝删除。
     *
     * <p>与 {@link #ROLE_IN_USE} 同一条原则、**方向相反**，值得说清为什么这里也拒绝：
     * 用户组是逻辑删除，删掉之后判定 SQL 里那段
     * {@code gm.user_group_id IN (...)} 会立刻取不到它 —— 于是
     * "这个组从某个角色那里得到的权限"对**组里的每一个人**同时消失，
     * 而删组的本意可能只是"这个部门撤了"。没有任何界面会提示这件事发生过。
     * 要求先移除成员（那时能看到受影响的是哪些人），再删组。
     *
     * <p><b>用户组自己持有的角色不构成拒绝理由</b>，会在删除时一并撤销 ——
     * 与 {@link #ROLE_IN_USE} 里"角色自身的授权一并删除"完全同构：
     * 成员既然已经清空，那些授权就不再影响任何人，而它们属于**这个组自己的配置**。
     *
     * <p>注意这条守卫只在**正常路径**上成立（接口先拒绝才轮到级联）。
     * 真正的兜底在 {@code UserGroupService.create} 的复活分支：它无条件清掉
     * 残留的成员与授权，让"复活 = 空组"不依赖"别的代码都写对了"。
     */
    USER_GROUP_IN_USE(40918, "用户组仍包含成员，请先移除成员");

    private final int code;
    private final String message;

    AuthResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 便捷抛出：{@code throw AuthResultCode.USER_NOT_FOUND.businessException();}
     *
     * <p>它只是 {@link BusinessException} 的一层语法糖，不改变异常语义 ——
     * 最终仍由 {@code GlobalExceptionHandler} 统一翻译为响应体，
     * 业务代码里**不要**再自己 try/catch 成 {@code R.fail}。
     */
    public BusinessException businessException() {
        return new BusinessException(code, message);
    }

    /** 用更具体的文案覆盖默认文案，码值不变 */
    public BusinessException businessException(String messageOverride) {
        return new BusinessException(code, messageOverride);
    }

}
