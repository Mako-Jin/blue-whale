package com.bluewhale.monitor.vo;

/**
 * 总控详情里的一条指标（屏风页详情表的一行）。
 *
 * @param no 序号，从 1 开始
 * @param label 指标名
 * @param value 已排版的数值文本
 * @author BlueWhale
 */
public record HubLineVO(int no, String label, String value) {
}
