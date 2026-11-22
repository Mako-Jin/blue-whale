package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocSearchService;
import com.bluewhale.doc.interfaces.dto.DocSearchQueryRequest;
import com.bluewhale.doc.interfaces.vo.DocSearchItemVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档搜索接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocSearchController {

    private final DocSearchService searchService;

    /**
     * 搜索已发布页面。
     *
     * @param request     搜索条件
     * @param httpRequest 当前请求
     * @return 搜索结果
     */
    @GetMapping("/search")
    @RequirePermission("sys:doc:search")
    public Result<PageResult<DocSearchItemVO>> search(@Valid DocSearchQueryRequest request,
                                                      HttpServletRequest httpRequest) {
        return Result.ok(searchService.search(request, UserContext.requireUserId(httpRequest)));
    }
}
