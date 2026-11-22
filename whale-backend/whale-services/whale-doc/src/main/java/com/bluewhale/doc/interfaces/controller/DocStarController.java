package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocStarService;
import com.bluewhale.doc.interfaces.vo.DocStarItemVO;
import com.bluewhale.doc.interfaces.vo.DocStarToggleVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面收藏接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocStarController {

    private final DocStarService starService;

    /**
     * 切换页面收藏状态。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 切换后的收藏状态
     */
    @PostMapping("/page/{id}/star")
    @RequirePermission("sys:doc:page:read")
    public Result<DocStarToggleVO> toggle(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(starService.toggle(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 分页查询我的收藏。
     *
     * @param current     当前页码
     * @param size        每页条数
     * @param httpRequest 当前请求
     * @return 收藏分页
     */
    @GetMapping("/page/star/page")
    @RequirePermission("sys:doc:page:read")
    public Result<PageResult<DocStarItemVO>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  HttpServletRequest httpRequest) {
        return Result.ok(starService.page(current, size, UserContext.requireUserId(httpRequest)));
    }
}
