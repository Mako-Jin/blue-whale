/**
 * 公文与档案上下文（M4 落地）。
 *
 * <p>负责公文模板、文号、发文/收文流程与归档映射。档案检索、保管与销毁归 Archive，
 * 产品技术文档归 PDM。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.document;
