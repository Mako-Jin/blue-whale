package com.bluewhale.alert.controller;

import com.bluewhale.alert.support.AlertLevels;
import com.bluewhale.alert.support.AlertRecordSupport;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警元数据接口（前端枚举来源）。
 *
 * <p>规则编辑页需要运算符、级别、通道、状态等枚举。集中由后端下发，避免前端硬编码后
 * 与规则校验漂移。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/alert/v1/meta")
public class AlertMetaController {

    /**
     * 查询告警相关枚举
     *
     * @return 枚举字典
     */
    @GetMapping("/enums")
    @RequirePermission("sys:alert:record:list")
    public Result<Map<String, List<String>>> enums() {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        enums.put("levels", List.of(AlertLevels.INFO, AlertLevels.WARN,
                AlertLevels.ERROR, AlertLevels.CRITICAL));
        enums.put("channels", List.of("EMAIL", "SMS", "WEBHOOK", "NONE"));
        enums.put("operators", List.of("GT", "GE", "LT", "LE", "EQ", "NE"));
        enums.put("statuses", List.of(AlertRecordSupport.STATUS_FIRING,
                AlertRecordSupport.STATUS_RECOVERED, AlertRecordSupport.STATUS_SILENCED));
        enums.put("sortFields", List.of("triggerTime", "createTime", "recoverTime"));
        enums.put("sortOrders", List.of("asc", "desc"));
        return Result.ok(enums);
    }
}
