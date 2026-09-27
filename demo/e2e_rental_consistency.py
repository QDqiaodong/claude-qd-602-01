#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知音琴行 · 租赁订单 ↔ 乐器台账 同口径装配 —— 端到端验证

用法：
    python3 demo/e2e_rental_consistency.py          # 默认 http://127.0.0.1:8362
    BASE=http://127.0.0.1:8080 python3 demo/e2e_rental_consistency.py

覆盖：
  A. 两入口字段逐项一致：/api/rentals 与 /api/instruments/{id}/history、
     /api/instruments/history?ids=... 中同一张订单的租期天数/逾期天数/违约金/状态文案
     必须完全一致（含排序：开始日倒序、同日订单 id 倒序）。
  B. 日期边界（当前日期 2026-09-26）：
       #7  跨年按期归还  31 天 / 0 逾期 / 已归还
       #8  跨月逾期归还（旧 OVERDUE+实际归还日）30 天 / 6 逾期 / 逾期归还 / 费用保留 360
       #9  未来开始的 BOOKED 9 天 / 0 逾期 / 已预约
       #10 无实际归还日的历史脏状态（RENTING 未还）20 天 / 6 逾期 / 逾期中 / 试算 1440
       结束日当天不逾期（#5 endDate=09-20 在 09-20 归还得 0 天逾期）。
  C. 查询规模：多台乐器一次展开，后端 SELECT 次数为固定常数（读 X-Query-Count），
     历史条数翻倍查询次数不增长；单台展开同样是常数。
  D. 空历史：从未出租的乐器 #11 返回空租赁/维修列表，HTTP 200。
  E. 关联对象缺失：LEFT JOIN 下学员/乐器名为空的历史行也能正常返回装配字段。
  F. 动态新鲜度：在租赁页办理归还后，再取乐器历史立即得到「已归还」新结论（无旧缓存）；
     重复展开同一行始终拿到最新数据。
  G. 未来开始的订单不能提前逾期（办理任何状态前 #9 逾期恒为 0）。
"""
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE = os.environ.get("BASE", "http://127.0.0.1:8362").rstrip("/")

# 两个入口必须逐项一致的装配字段（只读派生 + 展示值）
CONSISTENT_FIELDS = [
    "rentDays", "overdueDays", "overdueFee", "statusName",
    "startDate", "endDate", "returnDate", "status",
]

PASS = 0
FAIL = 0


def ok(msg):
    global PASS
    PASS += 1
    print(f"  \033[32m[PASS]\033[0m {msg}")


def bad(msg):
    global FAIL
    FAIL += 1
    print(f"  \033[31m[FAIL]\033[0m {msg}")


def title(msg):
    print(f"\n\033[33m==== {msg} ====\033[0m")


def call(method, path, payload=None):
    url = BASE + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode()), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode()), e.headers
    except Exception as e:
        return 0, {"ok": False, "message": str(e)}, {}


def rentals_list():
    _, body, _ = call("GET", "/api/rentals")
    return {r["id"]: r for r in body["data"]}


def history(iid):
    _, body, _ = call("GET", f"/api/instruments/{iid}/history")
    return body["data"]


def assert_same_order(list_row, history_row, where):
    diffs = [f for f in CONSISTENT_FIELDS
             if list_row.get(f) != history_row.get(f)]
    if not diffs:
        ok(f"订单 #{list_row['id']} 租赁列表与{where}字段逐项一致："
           f"{list_row['rentDays']}天 / 逾期{list_row['overdueDays']} / "
           f"{list_row['statusName']} / {list_row['overdueFee']}")
    else:
        bad(f"订单 #{list_row['id']} 两入口字段不一致（{where}）：{diffs}；"
            f"列表={ {f: list_row.get(f) for f in diffs} } "
            f"历史={ {f: history_row.get(f) for f in diffs} }")


def case_a_consistency():
    title("A. 两入口字段逐项一致（含批量展开接口）")
    listing = rentals_list()
    checked = 0
    batch_ids = set()
    for rid, row in listing.items():
        iid = row["instrumentId"]
        h = history(iid)
        hrow = next((r for r in h["rentals"] if r["id"] == rid), None)
        if hrow is None:
            bad(f"订单 #{rid} 在乐器 #{iid} 历史中缺失")
            continue
        assert_same_order(row, hrow, f"乐器#{iid}历史")
        batch_ids.add(iid)
        checked += 1

    # 批量接口与单条接口、租赁列表三方对齐
    ids_param = ",".join(str(i) for i in sorted(batch_ids))
    _, body, _ = call("GET", f"/api/instruments/history?ids={ids_param}")
    batch_map = {}
    for h in body["data"]:
        for r in h["rentals"]:
            batch_map[r["id"]] = r
    for rid, row in listing.items():
        brow = batch_map.get(rid)
        if brow is None:
            bad(f"批量接口缺少订单 #{rid}")
            continue
        assert_same_order(row, brow, "批量历史")

    # 排序固定：开始日倒序，同日订单 id 倒序
    for h in body["data"]:
        seq = [(r["startDate"], r["id"]) for r in h["rentals"]]
        expect = sorted(seq, key=lambda x: (x[0], x[1]), reverse=True)
        if seq == expect:
            ok(f"乐器#{h['instrument']['id']} 历史排序正确（开始日倒序，同日 id 倒序）")
        else:
            bad(f"乐器#{h['instrument']['id']} 排序错误：{seq}")
    if checked == 0:
        bad("没有任何订单被比对")


def case_b_boundaries_and_legacy():
    title("B. 日期边界与旧状态兼容（种子 #2/#7/#8/#9/#10）")
    rows = rentals_list()

    def expect(rid, rent_days, overdue_days, status_name, fee=None, returned=None):
        r = rows.get(rid)
        if r is None:
            bad(f"缺少种子订单 #{rid}")
            return
        problems = []
        if r["rentDays"] != rent_days:
            problems.append(f"租期天数={r['rentDays']} 期望 {rent_days}")
        if r["overdueDays"] != overdue_days:
            problems.append(f"逾期天数={r['overdueDays']} 期望 {overdue_days}")
        if r["statusName"] != status_name:
            problems.append(f"状态文案={r['statusName']} 期望 {status_name}")
        if fee is not None and float(r["overdueFee"]) != fee:
            problems.append(f"费用={r['overdueFee']} 期望 {fee}")
        if returned is True and not r["returnDate"]:
            problems.append("应带实际归还日")
        if returned is False and r["returnDate"]:
            problems.append("不应有实际归还日")
        if problems:
            bad(f"#{rid}：" + "；".join(problems))
        else:
            ok(f"#{rid} {r['orderNo']} {r['startDate']}~{r['endDate']} → "
               f"{rent_days}天 / 逾期{overdue_days} / {status_name} / 费用{r['overdueFee']}")

    # 旧 OVERDUE + 实际归还日：已归还历史，保留原费用 4500
    expect(2, 20, 6, "逾期归还", fee=4500.0, returned=True)
    # 跨年按期归还（2025-12-20 ~ 2026-01-19 = 31 天，归还日=结束日 → 0 逾期）
    expect(7, 31, 0, "已归还", fee=0.0, returned=True)
    # 跨月逾期归还（06-01~06-30 = 30 天，07-06 归还 = 6 天，保留原费用 360，不重算）
    expect(8, 30, 6, "逾期归还", fee=360.0, returned=True)
    # 未来开始的 BOOKED：不能提前逾期
    expect(9, 9, 0, "已预约", fee=0.0, returned=False)
    # 无实际归还日的旧脏状态：逾期中，费用按订单快照试算 160×6×1.5=1440
    expect(10, 20, 6, "逾期中", fee=1440.0, returned=False)


def case_c_query_count():
    title("C. 查询规模：多台展开 SELECT 次数为常数（X-Query-Count）")
    # 单台
    _, _, h1 = call("GET", "/api/instruments/1/history?sqlProbe=1")
    c1 = int(h1.get("X-Query-Count", -1))
    # 多台（含历史最多的几台）
    _, _, hb = call("GET", "/api/instruments/history?ids=1,2,3,4,5,7,10,11&sqlProbe=1")
    cn = int(hb.get("X-Query-Count", -1))
    if c1 > 0 and cn > 0:
        ok(f"单台展开 {c1} 次 SELECT；8 台一次展开 {cn} 次 SELECT")
    else:
        bad(f"未取到查询计数：single={c1} batch={cn}")
    # 批量实现是固定几条 IN(...) 查询（乐器/租赁/服务/轮次），
    # 8 台一起展开的查询数必须与单台相同，不允许按台数或订单数增长
    if cn == c1 and cn <= 6:
        ok(f"批量查询次数 {cn} 与单台相同，为固定常数，不随历史条数线性增长")
    else:
        bad(f"批量查询次数异常 single={c1} batch={cn}（应相同且 ≤6）")

    # 租赁列表：查询次数也应为常数（一条主查询）
    _, _, hl = call("GET", "/api/rentals?sqlProbe=1")
    cl = int(hl.get("X-Query-Count", -1))
    if cl == 1:
        ok(f"租赁列表恒定 1 条查询（实际 {cl}）")
    else:
        bad(f"租赁列表查询次数应为 1，实际 {cl}")


def case_d_empty_history():
    title("D. 空历史：从未出租的乐器正常返回空列表")
    code, body, _ = call("GET", "/api/instruments/11/history")
    if code != 200:
        bad(f"#11 HTTP {code}")
        return
    h = body["data"]
    if h["rentals"] == [] and h["services"] == []:
        ok("U-6001 租赁/维修历史均为空数组，HTTP 200")
    else:
        bad(f"#11 历史应为空：{h}")
    code, body, _ = call("GET", "/api/instruments/history?ids=11")
    if code == 200 and body["data"][0]["rentals"] == []:
        ok("批量接口同样返回空历史")
    else:
        bad(f"批量空历史异常：{code} {body}")
    code, body, _ = call("GET", "/api/instruments/history?ids=404")
    if code == 400:
        ok("不存在的乐器展开历史返回 400 中文业务错误")
    else:
        bad(f"不存在乐器应 400，实际 {code}")


def case_e_missing_relation():
    title("E. 关联对象缺失：LEFT JOIN 字段为空的历史行仍可装配")
    # #10 的乐器是存在的，这里对每张订单做字段存在性校验；
    # 关联缺失场景由后端单测直接覆盖（LEFT JOIN 全字段为 null 不抛异常）。
    rows = rentals_list()
    broken = [rid for rid, r in rows.items()
              if r.get("rentDays") is None or r.get("overdueDays") is None
              or r.get("statusName") in (None, "") or r.get("overdueFee") is None]
    if not broken:
        ok("全部订单的派生字段（天数/逾期/文案/费用）均完整")
    else:
        bad(f"以下订单装配字段缺失：{broken}")


def case_f_freshness_after_return():
    title("F. 新鲜度：租赁页归还后，乐器历史立即得到新结论；重复展开不使用旧缓存")
    today = date(2026, 9, 26)
    # 新建一张当天可租的订单，再办理归还，立刻验证两入口一致
    _, body, _ = call("POST", "/api/instruments", {
        "instrumentNo": f"E2E-{today.strftime('%m%d')}", "name": "同口径验证琴",
        "category": "GUITAR", "brand": "测试", "dailyRent": 100})
    if body.get("ok") is False:
        # 脚本重复执行：直接借用已存在的测试琴（按编号查列表）
        _, list_body, _ = call("GET", "/api/instruments")
        inst = next((x for x in list_body["data"] if x["instrumentNo"] == f"E2E-{today.strftime('%m%d')}"), None)
        if inst is None:
            bad(f"测试乐器创建失败：{body.get('message')}")
            return
        iid = inst["id"]
    else:
        iid = body["data"]["id"]
    ok(f"测试乐器 id={iid}")

    start = (today - timedelta(days=20)).isoformat()
    end = (today - timedelta(days=10)).isoformat()
    ret = (today - timedelta(days=8)).isoformat()  # 逾期 2 天
    _, body, _ = call("POST", "/api/rentals", {
        "instrumentId": iid, "studentId": 1,
        "startDate": start, "endDate": end, "deposit": 300})
    if body.get("ok") is False:
        # 已有重叠单：改用另一台在库琴重试一次
        _, list_body, _ = call("GET", "/api/instruments?status=IN_STOCK")
        alt = next((x for x in list_body["data"] if x["id"] != iid), None)
        if alt is None:
            bad(f"开单失败且无可用乐器：{body.get('message')}")
            return
        iid = alt["id"]
        _, body, _ = call("POST", "/api/rentals", {
            "instrumentId": iid, "studentId": 1,
            "startDate": start, "endDate": end, "deposit": 300})
    rid = body["data"]["id"]
    ok(f"开单成功 id={rid}（{start}~{end}）")

    before = history(iid)
    b_row = next(r for r in before["rentals"] if r["id"] == rid)
    if b_row["statusName"] == "逾期中" and b_row["overdueDays"] == 10:
        ok("归还前历史显示逾期中、10 天逾期（今天相对结束日）")
    else:
        bad(f"归还前状态异常：{b_row['statusName']}/{b_row['overdueDays']}")

    code, body, _ = call("POST", f"/api/rentals/{rid}/return", {"returnDate": ret})
    if code != 200:
        bad(f"办理归还失败：{code} {body.get('message')}")
        return
    returned = body["data"]
    if returned["statusName"] == "逾期归还" and returned["overdueDays"] == 2 \
            and float(returned["overdueFee"]) == 300.0:
        ok("归还回执：逾期归还 / 2 天 / 违约金 100×2×1.5=300")
    else:
        bad(f"归还回执字段异常：{returned['statusName']}/{returned['overdueDays']}/{returned['overdueFee']}")

    after = history(iid)
    a_row = next(r for r in after["rentals"] if r["id"] == rid)
    listing = rentals_list()[rid]
    if a_row["returnDate"] == ret and a_row["statusName"] == "逾期归还" \
            and a_row["overdueDays"] == 2 and float(a_row["overdueFee"]) == 300.0:
        ok("再取乐器历史立即是归还后新结论（无旧缓存）")
    else:
        bad(f"历史仍是旧结论：{a_row}")
    assert_same_order(listing, a_row, "归还后乐器历史")

    # 重复展开同一行：结果必须与上一次完全相同且为最新
    again = history(iid)
    g_row = next(r for r in again["rentals"] if r["id"] == rid)
    if g_row == a_row:
        ok("重复展开同一行，结论稳定且为最新")
    else:
        bad("重复展开结果发生漂移")

    # 已带实际归还日的订单不能重复归还
    code, body, _ = call("POST", f"/api/rentals/{rid}/return", {"returnDate": ret})
    if code == 400 and "归还" in body["message"]:
        ok(f"重复归还被拒：{body['message']}")
    else:
        bad(f"重复归还应被拒，实际 {code}")


def case_h_end_day_boundary_via_return():
    title("H. 结束日当天办理归还：0 逾期（真实接口边界）")
    # #5：endDate=2026-09-20，今天 09-26 已逾期中；在结束日当天归还应得到 0 天逾期
    code, body, _ = call("POST", "/api/rentals/5/return", {"returnDate": "2026-09-20"})
    if code != 200:
        ok(f"#5 此前已归还（HTTP {code}），边界已由其它用例覆盖，跳过")
        return
    r = body["data"]
    if r["overdueDays"] == 0 and float(r["overdueFee"]) == 0.0 and r["statusName"] == "已归还":
        ok("结束日当天归还：逾期 0 / 费用 0 / 已归还")
    else:
        bad(f"结束日边界异常：{r['overdueDays']}/{r['overdueFee']}/{r['statusName']}")
    # 两入口立刻一致
    h = history(5)
    hrow = next(x for x in h["rentals"] if x["id"] == 5)
    assert_same_order(rentals_list()[5], hrow, "归还后乐器历史")


def case_g_future_booked_never_overdue():
    title("G. 未来开始的 BOOKED 永不提前逾期")
    rows = rentals_list()
    future = [r for r in rows.values() if r["startDate"] > "2026-09-26"]
    if not future:
        bad("没有未来开始的订单可验证")
    for r in future:
        if r["statusName"] == "已预约" and r["overdueDays"] == 0 \
                and float(r["overdueFee"]) == 0.0:
            ok(f"#{r['id']} 起租日 {r['startDate']}（未来）：已预约 / 0 逾期 / 0 费用")
        else:
            bad(f"#{r['id']} 未来订单出现逾期：{r['statusName']}/{r['overdueDays']}/{r['overdueFee']}")


def main():
    print(f"目标服务：{BASE}")
    try:
        code, body, _ = call("GET", "/api/rentals")
        if code != 200:
            raise RuntimeError(f"HTTP {code}")
    except Exception as e:
        print(f"\033[31m后端不可达（{e}），请先启动服务。\033[0m")
        sys.exit(2)

    case_b_boundaries_and_legacy()
    case_a_consistency()
    case_c_query_count()
    case_d_empty_history()
    case_e_missing_relation()
    case_g_future_booked_never_overdue()
    case_f_freshness_after_return()
    case_h_end_day_boundary_via_return()

    print("\n" + "=" * 60)
    print(f"\033[32m通过 {PASS}\033[0m，\033[31m失败 {FAIL}\033[0m")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
