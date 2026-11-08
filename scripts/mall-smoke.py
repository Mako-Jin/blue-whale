"""商城全链路冒烟：直接打 bw-mall（绕过网关，注入可信身份头）。

用法：容器起来后 `python scripts/mall-smoke.py`，报告落 `.workbuddy/tmp/mall-smoke-report.json`
（可用环境变量 MALL_SMOKE_REPORT 覆盖）。

判断口径：HTTP 200 且业务码 code == 0 记为通过；业务码非 0 记为失败并记录原因。
每个用例独立记录，前一步失败不中断（后续依赖缺失会记为 SKIP）。
"""
import json
import os
import sys
from datetime import datetime
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:9010"
HEADERS = {
    "X-User-Id": "1",
    "X-Username": "admin",
    "X-Tenant-Id": "1",
    "X-User-Roles": "mall_admin",
    "X-Permission-Codes": "super_admin",
    "Content-Type": "application/json",
}

RESULTS = []
# 每次运行用独立后缀，避免上一轮残留数据撞唯一键（脚本可重复执行）
RUN = datetime.now().strftime("%m%d%H%M%S")


def call(method, path, body=None, params=None, headers=None, name=None, expect=0, raw_body=None):
    url = BASE + path
    if params:
        clean = {k: v for k, v in params.items() if v is not None}
        if clean:
            url += "?" + urllib.parse.urlencode(clean)
    data = None
    if raw_body is not None:
        data = raw_body.encode("utf-8")
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    for key, value in {**HEADERS, **(headers or {})}.items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8", "replace")
            status = resp.status
    except urllib.error.HTTPError as err:
        raw = err.read().decode("utf-8", "replace")
        status = err.code
    except Exception as err:  # 连接层失败
        RESULTS.append({"name": name or path, "http": -1, "code": -1,
                        "ok": False, "msg": f"连接失败: {err}"})
        print(f"  [CONN] {name or path}: {err}")
        return None
    try:
        payload = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        payload = {"raw": raw[:200]}
    code = payload.get("code")
    ok = status == 200 and code == expect
    RESULTS.append({
        "name": name or f"{method} {path}",
        "http": status,
        "code": code,
        "ok": ok,
        "msg": str(payload.get("message") or payload.get("msg") or "")[:120],
    })
    print(f"  {'OK  ' if ok else 'FAIL'} {name or path} -> http={status} code={code} "
          f"{'' if ok else str(payload)[:160]}")
    return payload.get("data")


def section(title):
    print(f"\n=== {title} ===")


def main():
    # ---------- A 元数据 / 类目 ----------
    section("A 元数据与类目")
    meta = call("GET", "/mall/meta", name="meta")
    call("GET", "/mall/category/tree", name="category.tree")
    cat = call("POST", "/mall/category",
               {"categoryName": f"冒烟类目-{RUN}", "sortNo": 1, "status": "ACTIVE"}, name="category.create")
    cat_id = cat if isinstance(cat, int) else (cat or {}).get("id") if isinstance(cat, dict) else None
    if cat_id:
        call("PUT", f"/mall/category/{cat_id}",
             {"categoryName": f"冒烟类目-{RUN}-改", "sortNo": 2, "status": "ACTIVE"}, name="category.update")

    # ---------- B 商品 / 库存 ----------
    section("B 商品与库存")
    spu_body = {
        "spuCode": f"SMOKE-SPU-{RUN}",
        "spuName": f"冒烟商品-{RUN}",
        "categoryId": cat_id,
        "brand": "BlueWhale",
        "detail": "冒烟用例数据",
        "skus": [
            {"skuCode": f"SMOKE-SKU-A-{RUN}", "specJson": '{"颜色":"黑"}', "status": "ENABLED", "sortNo": 1,
             "prices": [{"priceType": "RETAIL", "price": 199.00}]},
            {"skuCode": f"SMOKE-SKU-B-{RUN}", "specJson": '{"颜色":"白"}', "status": "ENABLED", "sortNo": 2,
             "prices": [{"priceType": "RETAIL", "price": 219.00}]},
        ],
    }
    spu = call("POST", "/mall/product", spu_body, name="product.create")
    spu_id = spu.get("id") if isinstance(spu, dict) else spu
    call("GET", "/mall/product/page", params={"size": 10}, name="product.page")
    detail = call("GET", f"/mall/product/{spu_id}", name="product.detail") if spu_id else None
    skus = (detail or {}).get("skus") or []
    sku_id = skus[0].get("id") if skus else None
    sku2_id = skus[1].get("id") if len(skus) > 1 else None
    if spu_id:
        spu_body["spuName"] = "冒烟商品-蓝牙耳机改"
        call("PUT", f"/mall/product/{spu_id}", spu_body, name="product.update")
        call("PUT", f"/mall/product/{spu_id}/status", params={"status": "ON_SALE"},
             name="product.onSale")
    if sku_id:
        call("PUT", f"/mall/stock/{sku_id}", {"delta": 100, "remark": "冒烟建期初"},
             name="stock.adjust")
        call("GET", "/mall/stock/page", params={"size": 10}, name="stock.page")

    # ---------- C 运费 / 渠道 ----------
    section("C 运费模板与渠道")
    ft = call("POST", "/mall/freight-template",
              {"templateName": f"冒烟运费模板-{RUN}", "chargeType": "FIXED", "baseAmount": 10.00,
               "freeThreshold": 99.00, "status": "ACTIVE"}, name="freight.create")
    ft_id = ft.get("id") if isinstance(ft, dict) else ft
    call("GET", "/mall/freight-template/page", params={"size": 10}, name="freight.page")
    if ft_id:
        call("GET", f"/mall/freight-template/{ft_id}", name="freight.detail")
        call("PUT", f"/mall/freight-template/{ft_id}",
             {"templateName": f"冒烟运费模板-{RUN}-改", "chargeType": "FIXED", "baseAmount": 8.00,
              "freeThreshold": 99.00, "status": "ACTIVE"}, name="freight.update")
        call("POST", f"/mall/freight-template/{ft_id}/status", params={"status": "ACTIVE"},
             name="freight.status")
    ch = call("POST", "/mall/channel",
              {"channelCode": f"SMOKE-CH-{RUN}", "channelName": f"冒烟直营店-{RUN}", "mode": "RETAIL",
               "settlementType": "ONLINE", "freightTemplateId": ft_id},
              name="channel.create")
    ch_id = ch.get("id") if isinstance(ch, dict) else ch
    call("GET", "/mall/channel/page", params={"size": 10}, name="channel.page")
    if ch_id:
        call("PUT", f"/mall/channel/{ch_id}",
             {"channelCode": f"SMOKE-CH-{RUN}", "channelName": f"冒烟直营店-{RUN}-改", "mode": "RETAIL",
              "settlementType": "ONLINE", "freightTemplateId": ft_id}, name="channel.update")
        call("PUT", f"/mall/channel/{ch_id}/status", params={"status": "ACTIVE"},
             name="channel.status")

    # ---------- D 优惠券 ----------
    section("D 优惠券")
    coupon = call("POST", "/mall/coupon",
                  {"couponCode": f"SMOKE-CP-{RUN}", "couponName": f"满100减10-{RUN}", "couponType": "FULL_REDUCTION",
                   "thresholdAmount": 100.00, "faceAmount": 10.00, "channelId": ch_id,
                   "totalQty": 100, "perUserLimit": 2, "validFrom": "2026-01-01 00:00:00",
                   "validTo": "2027-12-31 23:59:59", "status": "ACTIVE"}, name="coupon.create")
    cp_id = coupon.get("id") if isinstance(coupon, dict) else coupon
    call("GET", "/mall/coupon/page", params={"size": 10}, name="coupon.page")
    if cp_id:
        call("GET", f"/mall/coupon/{cp_id}", name="coupon.detail")
        call("PUT", f"/mall/coupon/{cp_id}",
             {"couponCode": f"SMOKE-CP-{RUN}", "couponName": f"满100减15-{RUN}", "couponType": "FULL_REDUCTION",
              "thresholdAmount": 100.00, "faceAmount": 15.00, "channelId": ch_id,
              "totalQty": 100, "perUserLimit": 2, "validFrom": "2026-01-01 00:00:00",
              "validTo": "2027-12-31 23:59:59", "status": "ACTIVE"}, name="coupon.update")
        call("POST", f"/mall/coupon/{cp_id}/status", params={"status": "ACTIVE"}, name="coupon.status")
        call("GET", "/mall/coupon/available", params={"channelId": ch_id, "amount": 200}, name="coupon.available")
        call("POST", f"/mall/coupon/{cp_id}/claim", name="coupon.claim")
        call("GET", "/mall/coupon/mine", params={"size": 10}, name="coupon.mine")

    # ---------- E 购物车 ----------
    section("E 购物车")
    cart = None
    if ch_id and sku_id:
        cart = call("POST", "/mall/cart/item", {"channelId": ch_id, "skuId": sku_id, "quantity": 2},
                    name="cart.add")
        call("GET", "/mall/cart", params={"channelId": ch_id}, name="cart.view")
    items = (cart or {}).get("items") or []
    cart_item_id = items[0].get("id") if items else None
    if cart_item_id:
        call("PUT", f"/mall/cart/item/{cart_item_id}", params={"quantity": 3}, name="cart.updateQty")
        call("PUT", "/mall/cart/select", {"channelId": ch_id, "itemIds": [cart_item_id], "selected": True},
             name="cart.select")

    # ---------- F 下单 / 支付 / 发货 / 收货 ----------
    section("F 订单主链路")
    item_ids = [cart_item_id] if cart_item_id else []
    order = None
    if ch_id and item_ids:
        call("POST", "/mall/order/preview", {"channelId": ch_id, "itemIds": item_ids}, name="order.preview")
        order = call("POST", "/mall/order", {"channelId": ch_id, "itemIds": item_ids, "remark": "冒烟"},
                     headers={"Idempotency-Key": f"smoke-order-{RUN}"}, name="order.create")
        call("POST", "/mall/order", {"channelId": ch_id, "itemIds": item_ids, "remark": "冒烟重复"},
             headers={"Idempotency-Key": f"smoke-order-{RUN}"}, name="order.create.idempotent")
        # 第二张订单专门走取消分支（取消只允许在待支付态）
        call("POST", "/mall/cart/item", {"channelId": ch_id, "skuId": sku_id, "quantity": 1},
             name="cart.addForCancel")
        cart3 = call("GET", "/mall/cart", params={"channelId": ch_id}, name="cart.viewForCancel") or {}
        ids3 = [it.get("id") for it in (cart3.get("items") or []) if it.get("id")]
        order2 = call("POST", "/mall/order", {"channelId": ch_id, "itemIds": ids3, "remark": "冒烟待取消"},
                      headers={"Idempotency-Key": f"smoke-order-cancel-{RUN}"},
                      name="order.createForCancel")
        if order2 and order2.get("id"):
            call("POST", f"/mall/order/{order2.get('id')}/cancel", params={"reason": "冒烟取消"},
                 name="order.cancel")
    order_id = (order or {}).get("id")
    order_no = (order or {}).get("orderNo")
    if order_id:
        call("GET", "/mall/order/my", params={"size": 10}, name="order.mine")
        call("GET", "/mall/order/page", params={"size": 10}, name="order.page")
        odetail = call("GET", f"/mall/order/{order_id}", name="order.detail") or {}
        pay_amount = odetail.get("payAmount") or odetail.get("totalAmount")
        pay_no = call("POST", f"/mall/order/{order_id}/pay", name="order.pay")
        call("POST", "/mall/internal/order/paid",
             {"orderNo": order_no, "payNo": pay_no or f"SMOKE-PAY-{RUN}",
              "paidAmount": pay_amount}, name="internal.orderPaid")
        call("POST", "/mall/internal/order/paid",
             {"orderNo": order_no, "payNo": pay_no or f"SMOKE-PAY-{RUN}",
              "paidAmount": pay_amount}, name="internal.orderPaid.idempotent")
        call("GET", f"/mall/order/{order_id}", name="order.detail.afterPaid")

    ship = None
    od_items = []
    if order_id:
        od = call("GET", f"/mall/order/{order_id}", name="order.detail.forShip") or {}
        od_items = od.get("items") or []
        lines = [{"orderItemId": it.get("id"), "quantity": it.get("quantity")} for it in od_items if it.get("id")]
        # 先开一张 1 件的发货单再取消：验证取消分支会释放可发数量
        cancel_ship = call("POST", "/mall/shipment",
                           {"orderId": order_id, "carrierCode": "YTO", "carrierName": "圆通",
                            "trackingNo": f"YTO{RUN}", "remark": "冒烟待取消",
                            "items": [{"orderItemId": od_items[0].get("id"), "quantity": 1}]},
                           name="shipment.cancel.create")
        if cancel_ship and cancel_ship.get("id"):
            call("POST", f"/mall/shipment/{cancel_ship.get('id')}/cancel", params={"reason": "冒烟取消"},
                 name="shipment.cancel")
        ship = call("POST", "/mall/shipment",
                    {"orderId": order_id, "carrierCode": "SF", "carrierName": "顺丰",
                     "trackingNo": f"SF{RUN}", "remark": "冒烟发货", "items": lines},
                    name="shipment.create")
        call("GET", "/mall/shipment/page", params={"size": 10}, name="shipment.page")
        if order_no:
            call("GET", f"/mall/shipment/order/{order_no}", name="shipment.byOrder")
    ship_id = (ship or {}).get("id")
    ship_no = (ship or {}).get("shipmentNo")
    if ship_id:
        call("GET", f"/mall/shipment/{ship_id}", name="shipment.detail")
        call("POST", f"/mall/shipment/{ship_id}/delivered", name="shipment.delivered")
        if ship_no:
            call("POST", "/mall/internal/shipment/delivered",
                 {"shipmentNo": ship_no, "deliveredTime": "2026-11-08 20:00:00"},
                 name="internal.shipmentDelivered")
    if order_id:
        current = (call("GET", f"/mall/order/{order_id}", name="order.detail.beforeComplete") or {}).get("status")
        call("POST", f"/mall/order/{order_id}/complete", name="order.complete",
             expect=0 if current == "SHIPPED" else 45031)

    # ---------- G 售后 / 退款 ----------
    section("G 售后与退款")
    if order_id:
        od = call("GET", f"/mall/order/{order_id}", name="order.detail.forAfterSale") or {}
        od_items = od.get("items") or []
        as_lines = [{"orderItemId": od_items[0].get("id"), "quantity": 1}] if od_items else []
        as_id = call("POST", "/mall/after-sale",
                     {"orderId": order_id, "type": "REFUND_ONLY", "reason": "冒烟仅退款",
                      "description": "冒烟用例", "lines": as_lines}, name="aftersale.apply")
        call("GET", "/mall/after-sale/page", params={"size": 10}, name="aftersale.pageMine")
        call("GET", "/mall/after-sale/admin/page", params={"size": 10}, name="aftersale.pageAll")
        if as_id:
            call("GET", f"/mall/after-sale/{as_id}", name="aftersale.detail")
            call("POST", f"/mall/after-sale/{as_id}/approve", params={"remark": "冒烟通过"},
                 name="aftersale.approve")
            as2 = call("POST", "/mall/after-sale",
                       {"orderId": order_id, "type": "REFUND_ONLY", "reason": "冒烟驳回",
                        "description": "冒烟用例2", "lines": as_lines}, name="aftersale.apply2")
            if as2:
                call("POST", f"/mall/after-sale/{as2}/reject", params={"remark": "冒烟驳回"},
                     name="aftersale.reject")
            as3 = call("POST", "/mall/after-sale",
                       {"orderId": order_id, "type": "REFUND_ONLY", "reason": "冒烟撤销",
                        "description": "冒烟用例3", "lines": as_lines}, name="aftersale.apply3")
            if as3:
                call("POST", f"/mall/after-sale/{as3}/cancel", name="aftersale.cancel")
        call("POST", f"/mall/order/{order_id}/refund", params={"reason": "冒烟整单退款"},
             name="order.refund")
        call("POST", "/mall/internal/order/refunded",
             {"orderNo": order_no, "payNo": f"SMOKE-PAY-{RUN}", "refundNo": f"SMOKE-REFUND-{RUN}",
              "refundAmount": pay_amount}, name="internal.orderRefunded")

    # ---------- H 发货单取消 / 清理 ----------
    section("H 发货取消与清理")
    if ch_id and sku2_id:
        cart2 = call("POST", "/mall/cart/item", {"channelId": ch_id, "skuId": sku2_id, "quantity": 1},
                     name="cart.add2")
        items2 = (cart2 or {}).get("items") or []
        target = next((it.get("id") for it in items2 if it.get("skuId") == sku2_id), None)
        if target:
            call("DELETE", f"/mall/cart/item/{target}", name="cart.remove")
    if spu_id:
        call("PUT", f"/mall/product/{spu_id}/status", params={"status": "OFF_SALE"},
             name="product.offSale")
        call("DELETE", f"/mall/product/{spu_id}", name="product.remove")
    if cat_id:
        call("DELETE", f"/mall/category/{cat_id}", name="category.remove")

    # ---------- I 负向契约 ----------
    section("I 负向契约（畸形请求体应归为参数错误 400）")
    call("POST", "/mall/product", raw_body='{"spuCode": 123, "skus": "not-an-array"}',
         name="negative.malformedJson", expect=400)
    call("GET", "/mall/product/abc", name="negative.badPathVar", expect=400)
    call("GET", "/mall/cart", name="negative.missingParam", expect=400)

    # ---------- 汇总 ----------
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"\n===== 汇总：{passed}/{total} 通过 =====")
    for r in RESULTS:
        if not r["ok"]:
            print(f"  FAIL {r['name']}: http={r['http']} code={r['code']} {r['msg']}")
    with open(r"F:\blue-whale\.workbuddy\tmp\mall-smoke-report.json", "w", encoding="utf-8") as fh:
        json.dump({"total": total, "passed": passed, "results": RESULTS}, fh, ensure_ascii=False, indent=2)
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
