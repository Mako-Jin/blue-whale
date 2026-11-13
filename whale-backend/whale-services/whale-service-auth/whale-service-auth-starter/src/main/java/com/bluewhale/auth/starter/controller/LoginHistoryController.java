package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.query.LoginHistoryPageQuery;
import com.bluewhale.auth.application.model.vo.SessionVOs.LoginHistoryVO;
import com.bluewhale.auth.application.service.SessionManageAppService;
import com.bluewhale.auth.interfaces.api.LoginHistoryApi;
import com.bluewhale.auth.interfaces.dto.response.LoginHistoryResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.LoginHistoryAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录历史接口实现（Web 适配层）：协议适配 + 查询参数装配 + CSV 导出，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class LoginHistoryController implements LoginHistoryApi {

    /** 导出单页条数：按页循环取数直至取满 total，避免一次性大分页压垮查询 */
    private static final long EXPORT_PAGE_SIZE = 10000;

    /** CSV 列头 */
    private static final String CSV_HEADER = "ID,租户ID,用户ID,用户名,登录类型,结果,失败原因,IP,User-Agent,设备指纹,登录时间";

    /** 登录时间列输出格式 */
    private static final DateTimeFormatter LOGIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SessionManageAppService sessionManageAppService;
    private final LoginHistoryAssembler loginHistoryAssembler;

    /**
     * 分页查询登录历史（按事件时间降序）
     *
     * @param pageQuery 分页参数
     * @param userId 用户 ID
     * @param result 结果过滤
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 登录历史分页结果
     */
    @Override
    @RequirePermission("sys:login:history:list")
    public Result<PageResult<LoginHistoryResponse>> page(PageQuery pageQuery,
                                                         @RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) String result,
                                                         @RequestParam(required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                         @RequestParam(required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(loginHistoryAssembler.toPage(sessionManageAppService.pageLoginHistory(
                new LoginHistoryPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), userId, result,
                        startTime, endTime))));
    }

    /**
     * 导出登录历史为 CSV（UTF-8 带 BOM 便于 Excel 直开；按页取数，行数上限 10000）
     *
     * @param userId 用户 ID
     * @param result 结果过滤
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    @Override
    @RequirePermission("sys:login:history:export")
    @AuditLog(operation = "导出登录历史", type = OperateType.EXPORT, targetType = "sys_login_history",
            recordResult = false)
    public void export(@RequestParam(required = false) Long userId,
                       @RequestParam(required = false) String result,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                       HttpServletResponse response) throws IOException {
        List<LoginHistoryVO> rows = fetchAllForExport(userId, result, startTime, endTime);
        writeCsv(response, rows);
    }

    /**
     * 按页循环取数直至取满 total（应用服务未提供一次性导出方法，复用分页接口、单页上限 10000）
     *
     * @param userId 用户 ID（可空）
     * @param result 结果过滤（可空）
     * @param startTime 起始时间（可空）
     * @param endTime 截止时间（可空）
     * @return 全量导出行
     */
    private List<LoginHistoryVO> fetchAllForExport(Long userId, String result,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        List<LoginHistoryVO> rows = new ArrayList<>();
        long current = 1;
        PageResult<LoginHistoryVO> page;
        do {
            page = sessionManageAppService.pageLoginHistory(new LoginHistoryPageQuery(
                    current, EXPORT_PAGE_SIZE, userId, result, startTime, endTime));
            rows.addAll(page.getRecords());
            current++;
        } while (!page.getRecords().isEmpty() && rows.size() < page.getTotal());
        return rows;
    }

    /**
     * 写出 CSV 字节流：UTF-8 BOM + 全字段引号包裹（内部双引号翻倍转义）
     *
     * @param response HTTP 响应
     * @param rows 导出行
     * @throws IOException 写出失败
     */
    private void writeCsv(HttpServletResponse response, List<LoginHistoryVO> rows) throws IOException {
        String fileName = "login-history-" + FILE_NAME_FORMATTER.format(LocalDateTime.now()) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName
                + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(CSV_HEADER);
            for (LoginHistoryVO row : rows) {
                writer.write('\n');
                writer.write(toCsvLine(row));
            }
        }
    }

    /**
     * 单行记录 → CSV 行（全字段引号包裹，内部双引号翻倍转义）
     *
     * @param row 登录历史记录
     * @return CSV 行文本
     */
    private String toCsvLine(LoginHistoryVO row) {
        return quote(String.valueOf(row.id())) + ',' + quote(String.valueOf(row.tenantId())) + ','
                + quote(String.valueOf(row.userId())) + ',' + quote(row.username()) + ','
                + quote(row.loginType()) + ',' + quote(row.loginResult()) + ',' + quote(row.failureReason())
                + ',' + quote(row.ip()) + ',' + quote(row.userAgent()) + ','
                + quote(row.deviceFingerprint()) + ','
                + quote(row.loginTime() == null ? null : LOGIN_TIME_FORMATTER.format(row.loginTime()));
    }

    /**
     * CSV 字段转义：null 输出空串；整体引号包裹，内部双引号翻倍
     *
     * @param value 原始值（可空）
     * @return 转义后的字段
     */
    private String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
