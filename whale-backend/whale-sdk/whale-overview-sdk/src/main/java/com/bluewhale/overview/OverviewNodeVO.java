package com.bluewhale.overview;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 概览节点清单项（运维/前端发现用）。
 *
 * @param service 服务前缀
 * @param nodeKey 节点 key
 * @param title 标题
 * @param permission 该节点要求的权限码，可空
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewNodeVO(String service, String nodeKey, String title, String permission) {
}
