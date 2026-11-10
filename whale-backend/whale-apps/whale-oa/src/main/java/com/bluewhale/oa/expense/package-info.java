/**
 * 报销上下文（M2 落地）。
 *
 * <p>负责报销单、明细、发票引用、费用标准与审批通过后的 ERP 应付联动。
 * OA 只管审批过程与报销单据，付款与做账归 ERP。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.expense;
