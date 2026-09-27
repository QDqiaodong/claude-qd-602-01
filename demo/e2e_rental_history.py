#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知音琴行 · 租赁订单 ↔ 乐器台账展开历史 口径一致性 —— 端到端验证

用法：
    python3 demo/e2e_rental_history.py            # 默认 http://127.0.0.1:8362
    BASE=http://127.0.0.1:8080 python3 demo/e2e_rental_history.py

覆盖：
  A. 旧状态兼容 + 日期边界（种子数据）：RETURNED / 带归还日的 OVERDUE / 无归还日的 OVERDUE /
     跨年租期 / 结束日当天归还 / 未来 BOOKED 不提前逾期
  B. 两个入口字段逐项一致：/api/rentals 与 /api/instruments/history 同一订单逐字段相同
  C. 排序固定：租期开始日倒序，同日按订单 id 倒序
  D. 查询规模：历史条数 0 vs 多的乐器查询次数相同；批量展开多台一次请求查询数有界
  E. 空历史与缺失对象：无历史乐器正常返回空列表；批量里混入不存在的 id 不影响其它
  F. 办理归还后两个入口同步更新；重复归还（含旧 OVERDUE 单）被拒
  G. 动态日期边界：结束日当天不算逾期；未来开始的 BOOKED 不出现逾期
"""
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from decimal import Decimal

BASE = os.environ.get("BASE", "http://127.0.0.1:8362").rstrip("/")
RUN_STAMP = os.environ.get("RUN_STAMP") or datetime.now().strftime("%H%M%S")
TODAY = date.today()
OVERDUE_UNRETURNED = "OVERDUE_UNRETURNED"

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
    """返回 (http_status, headers, body_json)"""
    url = BASE + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, dict(resp.headers), json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), json.loads(e.read().decode())
    except Exception as e:  # 网络错误
        return 0, {}, {"ok": False, "message": str(e)}


def expect_status(code, expected, msg):
    if code == expected:
        ok(f"{msg} (HTTP {code})")
    else:
        bad(f"{msg}（期望 HTTP {expected}，实际 {code}）")


def d(iso):
    return date.fromisoformat(iso)


def money(value):
    return Decimal(str(value)).quantize(Decimal("0.01"))


def expect_rental(view, *, status, status_name, rent_days, overdue_days, fee, label):
    """对一条订单视图逐项断言（两个入口共用这一套期望）。"""
    problems = []
    if view.get("status") != status:
        problems.append(f"status={view.get('status')}≠{status}")
    if view.get("statusName") != status_name:
        problems.append(f"statusName={view.get('statusName')}≠{status_name}")
    if view.get("rentDays") != rent_days:
        problems.append(f"rentDays={view.get('rentDays')}≠{rent_days}")
    if view.get("overdueDays") != overdue_days:
        problems.append(f"overdueDays={view.get('overdueDays')}≠{overdue_days}")
    if view.get("overdueFee") is None or money(view["overdueFee"]) != money(fee):
        problems.append(f"overdueFee={view.get('overdueFee')}≠{fee}")
    if problems:
        bad(f"{label}：" + "，".join(problems))
    else:
        ok(f"{label}：{status_name} · 租期 {rent_days} 天 · 逾期 {overdue_days} 天 · 费用 {money(fee)}")


# ---------- 数据装载 ----------
def load_rentals():
    code, _, body = call("GET", "/api/rentals")
    if code != 200:
        raise RuntimeError(f"租赁列表不可达 HTTP {code}")
    return body["data"]


def load_batch_history(ids):
    code, headers, body = call("GET", "/api/instruments/history?ids=" + ",".join(str(i) for i in ids))
    if code != 200:
        raise RuntimeError(f"批量历史不可达 HTTP {code}: {body}")
    lowered = {str(k).lower(): v for k, v in headers.items()}
    return body["data"], int(lowered.get("x-query-count", -1))


def rental_by_no(views, order_no):
    for view in views:
        if view["orderNo"] == order_no:
            return view
    raise RuntimeError(f"订单 {order_no} 不在租赁列表里")


# ---------- 用例 ----------
def case_a_seed_boundaries(rentals):
    title("A. 旧状态兼容 + 日期边界（种子数据，两个入口同一结论）")

    # #2 旧库 OVERDUE + 有归还日 → 已归还历史·逾期归还，保留原费用 4500.00
    expect_rental(rental_by_no(rentals, "R-20260801-001"),
                  status="OVERDUE", status_name="逾期归还", rent_days=20,
                  overdue_days=6, fee="4500.00", label="#2 带归还日的 OVERDUE")

    # #6 RETURNED → 已归还，无逾期
    expect_rental(rental_by_no(rentals, "R-20260501-001"),
                  status="RETURNED", status_name="已归还", rent_days=10,
                  overdue_days=0, fee="0.00", label="#6 按期归还")

    # #7 跨年租期 2025-12-28~2026-01-05 共 9 天，2026-01-08 归还 → 跨年逾期 3 天
    expect_rental(rental_by_no(rentals, "R-20251228-001"),
                  status="OVERDUE", status_name="逾期归还", rent_days=9,
                  overdue_days=3, fee="202.50", label="#7 跨年租期逾期归还")

    # #9 结束日当天（09-10）归还 → 结束日当天不算逾期
    expect_rental(rental_by_no(rentals, "R-20260901-002"),
                  status="RETURNED", status_name="已归还", rent_days=10,
                  overdue_days=0, fee="0.00", label="#9 结束日当天归还")

    # #8 旧库 OVERDUE + 无归还日（08-25~09-05 跨月）→ 逾期中，费用按今天计算
    days8 = max(0, (TODAY - d("2026-09-05")).days)
    expect_rental(rental_by_no(rentals, "R-20260825-001"),
                  status=OVERDUE_UNRETURNED if days8 > 0 else "RENTING",
                  status_name="逾期中" if days8 > 0 else "租用中",
                  rent_days=12, overdue_days=days8,
                  fee=Decimal("200") * days8 * Decimal("1.5"),
                  label="#8 无归还日的 OVERDUE（跨月）")

    # #5 RENTING 逾期未还（09-15~09-20）→ 逾期中
    days5 = max(0, (TODAY - d("2026-09-20")).days)
    expect_rental(rental_by_no(rentals, "R-20260915-001"),
                  status=OVERDUE_UNRETURNED if days5 > 0 else "RENTING",
                  status_name="逾期中" if days5 > 0 else "租用中",
                  rent_days=6, overdue_days=days5,
                  fee=Decimal("60") * days5 * Decimal("1.5"),
                  label="#5 租用中逾期未还")

    # #3 BOOKED（09-25~10-08）→ 已预约，绝不提前出现逾期
    expect_rental(rental_by_no(rentals, "R-20260925-001"),
                  status="BOOKED", status_name="已预约", rent_days=14,
                  overdue_days=0, fee="0.00", label="#3 预约单不提前逾期")


def case_b_two_entrances_consistent(rentals, histories):
    title("B. 两个入口字段逐项一致（租赁列表 vs 乐器台账展开历史）")
    history_views = {}
    for item in histories:
        for rental in item["rentals"]:
            history_views[rental["id"]] = rental
    fields = ["orderNo", "instrumentId", "studentId", "startDate", "endDate",
              "deposit", "dailyRent", "rentAmount", "status", "statusName",
              "returnDate", "overdueFee", "rentDays", "overdueDays"]
    compared = 0
    mismatch = 0
    for view in rentals:
        other = history_views.get(view["id"])
        if other is None:
            bad(f"订单 {view['orderNo']} 在乐器历史里缺失")
            mismatch += 1
            continue
        compared += 1
        for field in fields:
            left, right = view.get(field), other.get(field)
            if field in ("deposit", "dailyRent", "rentAmount", "overdueFee"):
                same = left is not None and right is not None and money(left) == money(right)
            else:
                same = left == right
            if not same:
                bad(f"订单 {view['orderNo']} 字段 {field} 不一致：租赁页={left} / 乐器历史={right}")
                mismatch += 1
    if mismatch == 0 and compared > 0:
        ok(f"{compared} 张订单 × {len(fields)} 个字段逐项一致")


def case_c_sorting(histories):
    title("C. 排序固定：租期开始日倒序，同日按订单 id 倒序")
    checked = 0
    for item in histories:
        rentals = item["rentals"]
        keys = [(r["startDate"], r["id"]) for r in rentals]
        # ISO 日期字符串按字典序即按时间序：整体倒序 = 开始日倒序 + 同日 id 倒序
        expect = sorted(keys, key=lambda x: (x[0], x[1]), reverse=True)
        no = item["instrument"]["instrumentNo"]
        if keys != expect:
            bad(f"乐器 {no} 的租赁历史排序错误：{keys}")
        else:
            checked += 1
    ok(f"{checked} 台乐器的租赁历史排序全部符合「开始日倒序 + 同日开始 id 倒序」")


def case_d_query_scale(inst_empty, inst_busy):
    title("D. 查询规模：不随历史条数 / 乐器台数线性增长")
    _, q_empty = load_batch_history([inst_empty])
    _, q_busy = load_batch_history([inst_busy])
    if q_empty > 0 and q_busy == q_empty:
        ok(f"0 条历史 vs 多条历史：查询次数相同（{q_empty} 次）")
    else:
        bad(f"查询次数随历史条数增长：空历史 {q_empty} 次 / 多历史 {q_busy} 次")

    _, q_two = load_batch_history([inst_empty, inst_busy])
    many_ids = [inst_empty, inst_busy, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    _, q_many = load_batch_history(many_ids)
    if 0 < q_many <= 4 and q_two <= q_many:
        ok(f"批量展开 {len(many_ids)} 台乐器仅 {q_many} 次查询（2 台时 {q_two} 次），一次装配不逐台追加")
    else:
        bad(f"批量展开查询次数异常：2 台 {q_two} 次 / {len(many_ids)} 台 {q_many} 次")


def case_e_empty_and_missing(inst_empty):
    title("E. 空历史与关联对象缺失")
    code, _, body = call("GET", f"/api/instruments/{inst_empty}/history")
    data = body.get("data") or {}
    if code == 200 and data.get("rentals") == [] and data.get("services") == []:
        ok("无历史的乐器展开返回 200 + 空列表")
    else:
        bad(f"空历史返回异常 HTTP {code}: {str(body)[:120]}")

    data, _ = load_batch_history([inst_empty, 999999, 1])
    ids = sorted(item["instrument"]["id"] for item in data)
    if ids == sorted([inst_empty, 1]):
        ok("批量历史混入不存在的 id：跳过缺失，其余正常返回")
    else:
        bad(f"批量历史缺失对象处理异常：返回 ids={ids}")

    code, _, body = call("GET", "/api/instruments/999999/history")
    if code == 400 and body.get("message"):
        ok(f"单台不存在：400 + 中文提示「{body['message']}」")
    else:
        bad(f"单台不存在应返回 400，实际 HTTP {code}")


def case_f_return_then_consistent(inst_busy):
    title("F. 办理归还后两个入口同步更新 + 重复归还保护")
    start = (TODAY - timedelta(days=10)).isoformat()
    end = (TODAY - timedelta(days=2)).isoformat()
    code, _, body = call("POST", "/api/rentals", {
        "instrumentId": inst_busy, "studentId": 1,
        "startDate": start, "endDate": end, "deposit": 100})
    if code != 200:
        bad(f"建单失败：{body}")
        return
    rid = body["data"]["id"]
    order_no = body["data"]["orderNo"]

    def both_views():
        page = {v["id"]: v for v in load_rentals()}[rid]
        hist = {}
        for item in load_batch_history([inst_busy])[0]:
            for r in item["rentals"]:
                hist[r["id"]] = r
        return page, hist.get(rid)

    page, hist = both_views()
    if hist is None:
        bad("归还前：订单在乐器历史里缺失")
        return
    if page["status"] == OVERDUE_UNRETURNED and hist["status"] == OVERDUE_UNRETURNED \
            and page["overdueDays"] == 2 and hist["overdueDays"] == 2 \
            and money(page["overdueFee"]) == money(hist["overdueFee"]) == money("150.00"):
        ok("归还前：两个入口都显示「逾期中」2 天、展示费用 150.00")
    else:
        bad(f"归还前两入口不一致：{page['status']}/{page['overdueDays']}/{page['overdueFee']} vs "
            f"{hist['status']}/{hist['overdueDays']}/{hist['overdueFee']}")

    code, _, body = call("POST", f"/api/rentals/{rid}/return", {"returnDate": TODAY.isoformat()})
    expect_status(code, 200, f"办理归还（{order_no}）")
    page, hist = both_views()
    if hist is None:
        bad("归还后：订单在乐器历史里缺失")
        return
    same = all(page.get(f) == hist.get(f) for f in
               ("status", "statusName", "overdueDays", "returnDate")) \
        and money(page["overdueFee"]) == money(hist["overdueFee"])
    if same and page["status"] == "OVERDUE" and page["statusName"] == "逾期归还" \
            and page["overdueDays"] == 2 and money(page["overdueFee"]) == money("150.00"):
        ok("归还后：两个入口同步变为「逾期归还」2 天、费用 150.00（无旧缓存）")
    else:
        bad(f"归还后两入口结论异常：{page['status']}/{page['overdueDays']}/{page['overdueFee']} vs "
            f"{hist['status']}/{hist['overdueDays']}/{hist['overdueFee']}")

    code, _, body = call("POST", f"/api/rentals/{rid}/return", {"returnDate": TODAY.isoformat()})
    expect_status(code, 400, "重复归还被拒")

    # 旧库里「带归还日的 OVERDUE」同样不允许重复结算（保住原费用 4500.00）
    code, _, body = call("POST", "/api/rentals/2/return", {"returnDate": TODAY.isoformat()})
    expect_status(code, 400, "旧 OVERDUE 已归还单重复归还被拒")
    view = rental_by_no(load_rentals(), "R-20260801-001")
    if money(view["overdueFee"]) == money("4500.00"):
        ok("旧单原费用 4500.00 未被重复结算改写")
    else:
        bad(f"旧单费用被改写：{view['overdueFee']}")


def case_g_dynamic_boundaries(inst_boundary):
    title("G. 动态日期边界：结束日当天不算逾期 / 未来 BOOKED 不提前逾期")

    # 结束日 = 今天：未归还也不算逾期，当天归还 → 已归还、费用 0
    code, _, body = call("POST", "/api/rentals", {
        "instrumentId": inst_boundary, "studentId": 1,
        "startDate": (TODAY - timedelta(days=3)).isoformat(),
        "endDate": TODAY.isoformat(), "deposit": 100})
    rid = body["data"]["id"]
    view = {v["id"]: v for v in load_rentals()}[rid]
    expect_rental(view, status="RENTING", status_name="租用中", rent_days=4,
                  overdue_days=0, fee="0.00", label="结束日=今天 未归还")
    code, _, body = call("POST", f"/api/rentals/{rid}/return", {"returnDate": TODAY.isoformat()})
    expect_status(code, 200, "结束日当天归还")
    view = {v["id"]: v for v in load_rentals()}[rid]
    expect_rental(view, status="RETURNED", status_name="已归还", rent_days=4,
                  overdue_days=0, fee="0.00", label="结束日当天归还后")

    # 未来开始的 BOOKED：不能提前出现逾期
    code, _, body = call("POST", "/api/rentals", {
        "instrumentId": inst_boundary, "studentId": 1,
        "startDate": (TODAY + timedelta(days=3)).isoformat(),
        "endDate": (TODAY + timedelta(days=5)).isoformat(), "deposit": 100})
    rid = body["data"]["id"]
    view = {v["id"]: v for v in load_rentals()}[rid]
    expect_rental(view, status="BOOKED", status_name="已预约", rent_days=3,
                  overdue_days=0, fee="0.00", label="未来开始的 BOOKED")


def make_instrument(tag):
    code, _, body = call("POST", "/api/instruments", {
        "instrumentNo": f"Q-{RUN_STAMP}-{tag}", "name": f"租赁口径验收琴{tag}",
        "category": "PERCUSSION", "brand": "测试", "dailyRent": 50,
        "note": "e2e_rental_history 临时乐器"})
    if code != 200:
        raise RuntimeError(f"临时乐器创建失败：{body}")
    return body["data"]["id"]


def prepare_busy_instrument():
    """一台带 6 张历史订单的乐器（各时段互不重叠）。"""
    iid = make_instrument("B")
    for k in range(2, 8):
        end = TODAY - timedelta(days=15 * k)
        start = end - timedelta(days=4)
        code, _, body = call("POST", "/api/rentals", {
            "instrumentId": iid, "studentId": 1,
            "startDate": start.isoformat(), "endDate": end.isoformat(), "deposit": 100})
        if code != 200:
            raise RuntimeError(f"历史订单创建失败：{body}")
    return iid


def main():
    print(f"目标服务：{BASE}，今天：{TODAY.isoformat()}")
    try:
        code, _, _ = call("GET", "/api/rentals")
        if code != 200:
            raise RuntimeError(f"HTTP {code}")
    except Exception as e:
        print(f"\033[31m后端不可达（{e}），请先启动服务。\033[0m")
        sys.exit(2)

    inst_empty = make_instrument("A")
    inst_busy = prepare_busy_instrument()
    inst_boundary = make_instrument("C")
    ok(f"临时乐器就绪：空历史 id={inst_empty}，多历史 id={inst_busy}（6 张订单），边界 id={inst_boundary}")

    rentals = load_rentals()
    # 覆盖全店乐器（含历史运行留下的临时乐器），保证两个入口逐单可比
    code, _, all_instruments = call("GET", "/api/instruments")
    all_ids = [row["id"] for row in all_instruments["data"]]
    histories, _ = load_batch_history(all_ids)

    case_a_seed_boundaries(rentals)
    case_b_two_entrances_consistent(rentals, histories)
    case_c_sorting(histories)
    case_d_query_scale(inst_empty, inst_busy)
    case_e_empty_and_missing(inst_empty)
    case_f_return_then_consistent(inst_busy)
    case_g_dynamic_boundaries(inst_boundary)

    print("\n" + "=" * 60)
    print(f"\033[32m通过 {PASS}\033[0m，\033[31m失败 {FAIL}\033[0m")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
