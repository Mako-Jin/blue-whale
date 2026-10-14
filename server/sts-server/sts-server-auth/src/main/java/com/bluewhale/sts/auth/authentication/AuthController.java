package com.bluewhale.sts.auth.authentication;

import com.bluewhale.sts.auth.common.AuthAuditActions;
import com.bluewhale.sts.auth.common.AuthResources;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.common.basic.model.R;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>本组接口是**公开**的（不标 {@code @RequiresPermission}），
 * 否则会形成"要登录先得有令牌"的死锁。
 * 它们的安全性由服务层保证（口令校验、失败锁定、令牌轮换）。
 *
 * <p>三个例外，都要求已认证但**不需要权限码** ——
 * 任何登录用户都应能查自己的信息、改自己的口令、踢掉自己的会话，
 * 而"它们需要某个权限码"会让一个没被授权的用户永远处理不了自己的账号：
 * <ul>
 *   <li>{@code /api/auth/me} —— 在 {@code AuthService#currentUser} 里通过
 *       {@code AuthContextHolder.require()} 表达；</li>
 *   <li>{@code /api/auth/password} —— 同上，另外**必须验旧口令**
 *       （它是这里唯一能证明操作者是本人的手段，见 {@code AuthService#changePassword}）；</li>
 *   <li>{@code /api/auth/logout-all} —— 它撤的是自己名下的会话，
 *       作用域由上下文定死、不接受任何参数（见 {@code AuthService#logoutAll}）。</li>
 * </ul>
 *
 * <p><b>登出有两条路径，因为它们是两件事：</b>{@code /logout} 撤请求体里指明的
 * 那**一个**会话（因此不需要身份），{@code /logout-all} 撤**当前用户**的全部会话。
 * 此前它们共用一个方法、靠"请求体在不在"区分 —— 那让"全部"成了省略参数的默认值
 * （见 {@code docs/decisions/0047}）。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录、登出、刷新令牌、当前用户")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "以租户编码 + 登录名 + 口令登录，返回访问令牌与刷新令牌")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, resolveIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "用刷新令牌换取新的令牌对；旧刷新令牌随之作废")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.refresh(request, resolveIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出", description = "撤销请求体里那一个刷新令牌；要撤销全部请用 /api/auth/logout-all")
    public R<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return R.ok();
    }

    /**
     * 退出所有设备。
     *
     * <p><b>为什么是独立一条路径而不是 {@code /logout} 的一个可选行为：</b>
     * 撤销一个会话与撤销全部会话是两个不同的动作，而"是不是全部"此前由
     * **请求体在不在**决定 —— 那等于把一个能一次踢掉所有设备的动作做成
     * 省略参数的默认值。路径分开之后，"全撤"这件事有了名字，
     * 也才有可能在审计、日志与界面上被单独谈论（见 {@code docs/decisions/0047}）。
     *
     * <p>与 {@code /logout} 一样是 POST —— 它是状态变更，不是幂等的"查询下线状态"；
     * 与登出的差别只在于作用域（这一个 / 全部），因此**不需要权限码**：
     * 能登录就该能踢掉自己的会话（理由写在 {@code AuthService#logoutAll}）。
     */
    @PostMapping("/logout-all")
    @Operation(summary = "退出所有设备",
            description = "撤销当前用户全部刷新令牌（含当前这个）；所有设备都将在下一次刷新时被送回登录页")
    public R<Void> logoutAll() {
        authService.logoutAll();
        return R.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户", description = "返回当前用户身份、角色与权限")
    public R<MeVO> me() {
        return R.ok(authService.currentUser());
    }

    /**
     * 自助改密。
     *
     * <p>用 {@code PUT} 而不是 {@code POST}：口令是当前用户这一"资源"的一个属性，
     * 换掉它是幂等的整体替换（重复提交同一个新口令，第二次会被防重用挡下 40914，
     * 而不是又改一次）。与 {@code /api/auth/users/{id}/password} 的 {@code POST} 不同 ——
     * 那条是"发起一次重置"，每次尝试都是一次新操作。
     *
     * <p>**要审计**，与登录 / 登出 / 刷新不同。理由是频率与权重而非"哪个接口更重要"：
     * 登录是高频动作，逐条记会把审计表灌满；凭据变更是低频、高重的事件 ——
     * "这个口令什么时候被换掉的"一条记录就能定住整条安全时间线。
     */
    @PutMapping("/password")
    @Audited(action = AuthAuditActions.USER_CHANGE_PASSWORD, resource = AuthResources.USER)
    @Operation(summary = "自助改密",
            description = "本人修改自己的口令。需提供当前口令；成功后撤销该用户全部刷新令牌（需重新登录）")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        // 改自己的口令时"操作人"与"对象"是同一个人，target 看似冗余 ——
        // 但填上它才能让"查某个账号的凭据变更历史"用同一个过滤条件
        // 同时覆盖管理员重置与本人改密（见 AuthAuditActions.USER_CHANGE_PASSWORD）。
        // 口令本身绝不进审计，与重置路径同源：审计表的保留期远长于任何口令的寿命。
        AuditContext.target(AuthContextHolder.currentUserId());
        return R.ok();
    }

    /**
     * 解析客户端 IP。
     *
     * <p>优先取 {@code X-Forwarded-For} 的**第一段**：经代理时 {@code getRemoteAddr()}
     * 拿到的是最后一跳代理地址，记下来没有排查价值。
     *
     * <p>注意该头可被伪造，因此只用于排查参考，**不作为鉴权或风控依据** ——
     * 与 {@code ClientLogController} 的处理保持一致。
     */
    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

}
