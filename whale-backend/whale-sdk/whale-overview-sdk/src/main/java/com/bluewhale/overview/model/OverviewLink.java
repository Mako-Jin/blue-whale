package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 一个跳转入口（厅内屏风页的入口登记）。
 *
 * <p>S4 只登记不消费：先把"这殿还能去哪儿"的形状定下来，
 * 屏风页在 T4.5 落地时直接读 {@code path}，不用再改一次契约。</p>
 *
 * @param key 唯一键
 * @param label 入口文字
 * @param path 屏风页路径
 * @param permission 该入口额外要求的权限码；无权限的入口由 provider 裁掉，不下发
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewLink(String key, String label, String path, String permission) {
}
