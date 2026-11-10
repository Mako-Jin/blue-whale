/**
 * OA 报表上下文（M6 落地）。
 *
 * <p>负责考勤、审批效率、人均报销等 OA 报表快照与导出记录。
 * 跨域原始数据通过本服务只读查询组装，不直连其他服务数据库。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.analytics;
