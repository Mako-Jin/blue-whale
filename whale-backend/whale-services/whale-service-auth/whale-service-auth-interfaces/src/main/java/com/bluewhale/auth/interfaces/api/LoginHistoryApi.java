package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.LoginHistoryResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 登录历史 API 契约（权限中心扩展 M3）：登录 / 登出 / 失败事件查询与 CSV 导出。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/login-history/v{version}", version = "1")
public interface LoginHistoryApi {

    /**
     * 分页查询登录历史（按事件时间降序）
     *
     * @param pageQuery 分页参数
     * @param userId 用户 ID（可空表示不限）
     * @param result 结果过滤（SUCCESS / FAILURE / LOGOUT，可空表示不限）
     * @param startTime 起始时间（可空，ISO 格式）
     * @param endTime 截止时间（可空，ISO 格式）
     * @return 登录历史分页结果
     */
    @GetMapping("/page")
    Result<PageResult<LoginHistoryResponse>> page(PageQuery pageQuery,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) String result,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime);

    /**
     * 导出登录历史为 CSV（UTF-8 带 BOM，便于 Excel 直开；行数上限 10000）
     *
     * @param userId 用户 ID（可空表示不限）
     * @param result 结果过滤（可空表示不限）
     * @param startTime 起始时间（可空）
     * @param endTime 截止时间（可空）
     * @param response HTTP 响应（CSV 字节流）
     * @throws IOException 写出失败
     */
    @GetMapping("/export")
    void export(@RequestParam(required = false) Long userId,
                @RequestParam(required = false) String result,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                HttpServletResponse response) throws IOException;
}
