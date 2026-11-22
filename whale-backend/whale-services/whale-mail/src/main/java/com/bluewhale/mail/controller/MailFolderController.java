package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.vo.MailFolderSyncVO;
import com.bluewhale.mail.vo.MailFolderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮箱文件夹接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/folder", version = "1")
@RequiredArgsConstructor
public class MailFolderController {

    private final MailFolderService folderService;

    private final MailAccountService accountService;

    /**
     * 列出文件夹（含未读数）
     *
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 文件夹列表
     */
    @GetMapping("/list")
    @RequirePermission("sys:mail:message:list")
    public Result<List<MailFolderVO>> list(@RequestParam(required = false) Long accountId,
                                           HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        return Result.ok(folderService.list(resolved, userId));
    }

    /**
     * 立即探测服务器文件夹并与本地对齐
     *
     * <p>用户在 Webmail 新建文件夹后，不必等下一轮定时同步：前端点一次刷新即可看到。
     * 探测是一次 IMAP LIST，成本远低于全量同步。</p>
     *
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 对齐结果统计
     */
    @PostMapping("/refresh")
    @RequirePermission("sys:mail:sync")
    public Result<MailFolderSyncVO> refresh(@RequestParam(required = false) Long accountId,
                                            HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        return Result.ok(folderService.syncFromServer(accountService.requireOwned(resolved, userId)));
    }
}
