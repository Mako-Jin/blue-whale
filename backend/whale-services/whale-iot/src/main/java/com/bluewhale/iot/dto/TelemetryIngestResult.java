package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 遥测入库结果（internal 桥接接口回执）。
 *
 * <p>返回给 EMQX 的响应恒为 200，用 {@code accepted}/{@code rejected} 表达脏数据处理情况，
 * 避免 Broker 因单条脏数据反复重投。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryIngestResult {

    /** 受理条数 */
    private int accepted;

    /** 丢弃条数（未知标识符 / 类型不符 / 超出批次上限） */
    private int rejected;

    /** 说明（设备未找到等场景，可空） */
    private String reason;
}
