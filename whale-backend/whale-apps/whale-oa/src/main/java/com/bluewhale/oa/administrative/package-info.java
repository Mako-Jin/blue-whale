/**
 * 行政事务上下文（M2 落地）。
 *
 * <p>负责车辆/司机/用车预约、用章申请与用印登记、采购申请。
 * 印章主数据与电子签执行归 Contract，采购订单执行归 ERP。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.administrative;
