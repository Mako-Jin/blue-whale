package com.bluewhale.mes.dashboard.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dashboard.dto.MesDashboardStreamRequest;
import com.bluewhale.mes.dashboard.service.MesDashboardService;
import com.bluewhale.mes.dashboard.service.MesDashboardStreamService;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MES 综合看板接口
 *
 * <p>聚合生产 / 质量 / 设备 / 人员四域指标；SSE 推送补齐 M4 遗留的看板推送通道；
 * 导出受独立权限码 {@code sys:mes:dashboard:export} 控制（REQ-FUNC-063 / REQ-SEC-031）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/dashboard")
@RequiredArgsConstructor
public class MesDashboardController {

    /** 导出文件名时间格式 */
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MesDashboardService dashboardService;
    private final MesDashboardStreamService streamService;

    /**
     * 综合看板快照
     *
     * @param lineId    产线 ID（可选）
     * @param teamId    班组 ID（可选）
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @return 四域聚合视图
     */
    @GetMapping
    @RequirePermission("sys:mes:dashboard:view")
    public Result<MesDashboardVO> dashboard(@RequestParam(required = false) Long lineId,
                                            @RequestParam(required = false) Long teamId,
                                            @RequestParam(required = false) String boardDate) {
        return Result.ok(dashboardService.dashboard(lineId, teamId, boardDate));
    }

    /**
     * SSE 推送订阅（POST + text/event-stream，Bearer 经 Authorization 头鉴权）
     *
     * <p>事件名 {@code dashboard}，数据为综合看板 JSON 快照；
     * 推送间隔缺省 10 秒（D14），入参钳制 5–60 秒。</p>
     *
     * @param request 订阅筛选条件与推送间隔
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("sys:mes:dashboard:view")
    public SseEmitter stream(@RequestBody(required = false) MesDashboardStreamRequest request) {
        return streamService.subscribe(request == null ? new MesDashboardStreamRequest() : request);
    }

    /**
     * 综合看板 CSV 导出（独立权限码控制）
     *
     * @param lineId    产线 ID（可选）
     * @param teamId    班组 ID（可选）
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @return CSV 附件（UTF-8 带 BOM，便于 Excel 直接打开）
     */
    @GetMapping("/export")
    @RequirePermission("sys:mes:dashboard:export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long lineId,
                                         @RequestParam(required = false) Long teamId,
                                         @RequestParam(required = false) String boardDate) {
        String csv = dashboardService.exportCsv(lineId, teamId, boardDate);
        byte[] body = ('\uFEFF' + csv).getBytes(StandardCharsets.UTF_8);
        String filename = "mes-dashboard-" + EXPORT_TIME_FORMAT.format(LocalDateTime.now()) + ".csv";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
