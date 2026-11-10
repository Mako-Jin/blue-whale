/**
 * 考勤上下文（M2 落地）。
 *
 * <p>负责考勤规则、班次、打卡地点/设备、打卡记录、日汇总、异常与补卡。
 * 员工身份归 Auth，审批状态归 Workflow；本上下文只存考勤业务数据。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.attendance;
