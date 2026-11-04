/**
 * 协同办公上下文（M3 落地）。
 *
 * <p>负责会议与会议室、日程、个人任务、公告、个人/部门网盘、通讯录聚合与工作台。
 * 即时通讯归 IM，项目任务归 PM，文件字节归 File，本上下文只存协同业务数据。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.collab;
