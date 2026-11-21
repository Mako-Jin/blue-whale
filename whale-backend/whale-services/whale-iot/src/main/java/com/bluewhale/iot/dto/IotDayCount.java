package com.bluewhale.iot.dto;

/**
 * 遥测日计数：某一天的上报条数（近 7 日趋势的一根柱）。
 *
 * <p>{@code label} 是已经排好版的横轴文字（{@code MM-DD}），不是日期对象：
 * 概览契约的图表横轴是字符串数组，服务端把格式定死，前端就不必再猜时区与格式。</p>
 *
 * @param label 横轴文字（{@code MM-DD}）
 * @param count 当天上报条数
 * @author BlueWhale
 */
public record IotDayCount(String label, long count) {
}
