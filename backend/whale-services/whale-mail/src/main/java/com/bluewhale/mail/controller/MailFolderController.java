package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.vo.MailFolderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/mail/folder")
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
}
