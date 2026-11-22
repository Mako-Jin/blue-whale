package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.vo.MailSearchStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件全文检索接口。
 *
 * <p>检索本身走 {@code /mail/message/page} 的 keyword 参数（分页与排序都在那里），
 * 这里只暴露引擎状态与索引重建——两者都是运维动作，不该混进日常读接口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/search", version = "1")
@RequiredArgsConstructor
public class MailSearchController {

    private final MailSearchService searchService;

    private final MailAccountService accountService;

    private final MailProperties properties;

    /**
     * 查看当前检索引擎状态
     *
     * @return 引擎状态
     */
    @GetMapping("/status")
    @RequirePermission("sys:mail:message:list")
    public Result<MailSearchStatusVO> status() {
        MailSearchStatusVO vo = new MailSearchStatusVO();
        vo.setEngine(searchService.engine());
        vo.setExternalIndex(searchService.externalIndex());
        vo.setMaxHits(properties.getSearch().getMaxHits());
        vo.setLikeFallback(properties.getSearch().isLikeFallback());
        return Result.ok(vo);
    }

    /**
     * 重建指定账号的检索索引
     *
     * <p>Lucene 引擎下，索引目录被清空、切换到新节点、或怀疑索引与库漂移时用它兜底。
     * MySQL 引擎返回 0（索引由数据库维护，无需重建）。</p>
     *
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 重建的文档数
     */
    @PostMapping("/reindex")
    @RequirePermission("sys:mail:sync")
    public Result<Integer> reindex(@RequestParam(required = false) Long accountId,
                                   HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        accountService.requireOwned(resolved, userId);
        return Result.ok(searchService.rebuild(resolved));
    }
}
