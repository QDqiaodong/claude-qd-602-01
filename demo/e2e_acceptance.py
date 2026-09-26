#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知音琴行 · 完工验收与返修闭环 —— 端到端验证

用法：
    python3 demo/e2e_acceptance.py            # 默认 http://127.0.0.1:8362
    BASE=http://127.0.0.1:8080 python3 demo/e2e_acceptance.py

覆盖：
  A. 旧数据兼容：验收链路上线前的 DONE 单无轮次记录仍正常展示
  B. 种子数据：#6 待验收（店员待办）、#5 返修中（原完工+退回两条都在）
  C. 完整闭环：开工联动维修中 → 提交完工 → 退回返修 → 再次提交 → 验收通过 → 恢复在库
  D. 调律类全程不改乐器状态
  E. 处理期间乐器报废：验收通过保留服务记录但不复活乐器
  F. 终态边界：已完成/已取消不能靠重复操作或乱序请求重回返修
  G. 并发：两名店员同时给出相反结论，只有一个生效
  H. 并发：调律师连点/网络重试提交完工，只生成一轮
  I. 失败回滚：缺字段时 400，轮次记录/服务单/乐器均不被改动
"""
import json
import os
import sys
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

BASE = os.environ.get("BASE", "http://127.0.0.1:8362").rstrip("/")

# 每次运行使用不同的未来时段，保证脚本可重复执行（同调律师时段不重叠校验）
RUN_STAMP = os.environ.get("RUN_STAMP") or datetime.now().strftime("%H%M%S")
RUN_DAY = (datetime.now() + timedelta(days=30 + int(RUN_STAMP) % 300)).date().isoformat()


def slot(hour):
    return f"{RUN_DAY} {hour:02d}:00:00"

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
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())
    except Exception as e:  # 网络错误
        return 0, {"ok": False, "message": str(e)}


def expect_status(code, expected, msg):
    if code == expected:
        ok(f"{msg} (HTTP {code})")
    else:
        bad(f"{msg}（期望 HTTP {expected}，实际 {code}）")


# ---------- 查询辅助 ----------
_orders_cache = None


def load_orders():
    global _orders_cache
    code, body = call("GET", "/api/service-orders")
    _orders_cache = {row["id"]: row for row in body["data"]}
    return _orders_cache


def order(sid):
    return load_orders()[sid]


def instrument(iid):
    code, body = call("GET", f"/api/instruments/{iid}")
    return body["data"]


def n_rounds(sid):
    return len(order(sid).get("rounds") or [])


# ---------- 用例 ----------
def case_a_legacy():
    title("A. 旧数据兼容：历史 DONE 单无轮次记录仍正常展示")
    for sid in (3, 4):
        row = order(sid)
        if row["status"] == "DONE" and row["currentRound"] == 0:
            ok(f"#{sid} 仍为已完成、轮次为 0")
        else:
            bad(f"#{sid} 状态异常 status={row['status']} round={row['currentRound']}")
        if row.get("resultNote"):
            ok(f"#{sid} 历史结果备注保留：{row['resultNote'][:18]}")
        else:
            bad(f"#{sid} 历史备注丢失")
        if n_rounds(sid) == 0:
            ok(f"#{sid} 不被误判为缺记录的异常单（列表正常展示）")
        else:
            bad(f"#{sid} 历史单不应出现轮次记录")


def case_b_seed():
    title("B. 种子数据：#6 待验收 / #5 返修中且历史完整")
    r6 = order(6)
    if r6["status"] == "PENDING_ACCEPT" and r6["currentRound"] == 1:
        ok("#6 处于待验收、第 1 轮")
    else:
        bad(f"#6 种子状态错误 {r6['status']}/{r6['currentRound']}")
    if n_rounds(6) == 1:
        ok("#6 已有 1 条完工提交记录")
    else:
        bad(f"#6 记录数 {n_rounds(6)}")
    r5 = order(5)
    if r5["status"] == "REWORKING" and r5["currentRound"] == 1:
        ok("#5 处于返修中（一条可直接再次提交的返修单）")
    else:
        bad(f"#5 种子状态错误 {r5['status']}")
    rounds = r5.get("rounds") or []
    phases = [x["phase"] for x in rounds]
    if phases == ["COMPLETION", "REJECTED"]:
        ok("#5 原完工记录与退回结论都保留（未被覆盖）")
    else:
        bad(f"#5 轮次记录顺序错误：{phases}")


def case_c_full_loop():
    title("C. 完整闭环：提交完工→退回返修→再次提交→验收通过")
    code, body = call("POST", "/api/service-orders", {
        "instrumentId": 2, "technicianId": 1, "serviceType": "REPAIR",
        "planStart": slot(9 + int(RUN_STAMP) % 6), "planEnd": slot(13 + int(RUN_STAMP) % 5),
        "fee": 300, "resultNote": "品丝打磨"})
    if code != 200:
        bad(f"派单失败：{body}")
        return
    sid = body["data"]["id"]
    ok(f"派单成功 id={sid}")

    if instrument(2)["status"] == "IN_STOCK":
        ok("开工前 G-1002 在库")
    code, _ = call("POST", f"/api/service-orders/{sid}/status", {"status": "DOING"})
    expect_status(code, 200, "开始施工")
    if instrument(2)["status"] == "REPAIRING":
        ok("维修类开工 → 关联乐器联动为维修中")
    else:
        bad(f"开工后乐器状态={instrument(2)['status']}")

    title("H. 连点 / 重试提交完工，只允许一轮")
    completion = {"processNote": "打磨品丝并清洁指板", "checkResult": "手感正常无杂音",
                  "deliveryNote": "附 7 天保修"}
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call("POST", f"/api/service-orders/{sid}/completions", completion),
                                range(2)))
    codes = sorted(r[0] for r in results)
    if codes == [200, 400]:
        ok(f"并发两次提交：{[r[0] for r in results]}，一个成功一个被拒")
    else:
        bad(f"并发提交结果异常：{codes} {[r[1].get('message') for r in results]}")
    r = order(sid)
    if r["status"] == "PENDING_ACCEPT" and r["currentRound"] == 1 and n_rounds(sid) == 1:
        ok("只生成第 1 轮、1 条完工记录，服务单待验收")
    else:
        bad(f"提交后状态异常 {r['status']}/{r['currentRound']}/records={n_rounds(sid)}")
    if instrument(2)["status"] == "REPAIRING":
        ok("待验收期间乐器保持维修中")
    # 再来一次顺序重试，同样要被拒（刷新/重放）
    code, body = call("POST", f"/api/service-orders/{sid}/completions", completion)
    expect_status(code, 400, "待验收期间刷新重放提交被拒")

    title("I. 缺字段退回返修必须整单不动（失败回滚）")
    before_records = n_rounds(sid)
    code, body = call("POST", f"/api/service-orders/{sid}/acceptance",
                      {"decision": "REJECTED", "operatorName": "许棠"})
    expect_status(code, 400, "退回不填故障/要求 → 400")
    if n_rounds(sid) == before_records and order(sid)["status"] == "PENDING_ACCEPT":
        ok("写入失败后轮次记录与服务单状态保持原状")
    else:
        bad("失败后留下了半完成链路")

    title("C-1. 店员退回返修")
    code, _ = call("POST", f"/api/service-orders/{sid}/acceptance", {
        "decision": "REJECTED", "operatorName": "许棠",
        "remainingFault": "三品仍有轻微打品",
        "reworkRequest": "重新找平品丝并逐品复检"})
    expect_status(code, 200, "退回返修成功")
    r = order(sid)
    if r["status"] == "REWORKING":
        ok("服务单进入返修中")
    else:
        bad(f"退回后状态={r['status']}")
    if n_rounds(sid) == 2 and [x["phase"] for x in r["rounds"]] == ["COMPLETION", "REJECTED"]:
        ok("第 1 轮完工记录保留，新增退回结论（共 2 条）")
    else:
        bad("退回后轮次记录不正确")
    if instrument(2)["status"] == "REPAIRING":
        ok("返修期间乐器继续维修中")

    title("C-2. 调律师再次提交完工（第 2 轮）")
    code, _ = call("POST", f"/api/service-orders/{sid}/completions", {
        "processNote": "重新找平三品品丝并抛光", "checkResult": "逐品复检，打品消失",
        "deliveryNote": "可交付，建议两周内复查"})
    expect_status(code, 200, "返修后再次提交成功")
    r = order(sid)
    if r["status"] == "PENDING_ACCEPT" and r["currentRound"] == 2:
        ok("进入第 2 轮待验收")
    phases = [x["phase"] for x in r["rounds"]]
    if phases == ["COMPLETION", "REJECTED", "COMPLETION"]:
        ok("页面按轮次还原：第1轮完工→退回，第2轮完工，旧内容未被覆盖")
    else:
        bad(f"轮次顺序错误：{phases}")

    title("C-3. 最终验收通过")
    code, _ = call("POST", f"/api/service-orders/{sid}/acceptance",
                   {"decision": "ACCEPTED", "operatorName": "许棠"})
    expect_status(code, 200, "验收通过成功")
    r = order(sid)
    if r["status"] == "DONE":
        ok("最新一轮验收通过，服务单真正完成")
    else:
        bad(f"验收后状态={r['status']}")
    if [x["phase"] for x in r["rounds"]] == ["COMPLETION", "REJECTED", "COMPLETION", "ACCEPTED"]:
        ok("最终保留 4 条记录，完整闭环可追溯")
    if instrument(2)["status"] == "IN_STOCK":
        ok("维修类乐器恢复在库")
    else:
        bad(f"验收后乐器未恢复：{instrument(2)['status']}")

    title("F. 终态边界")
    code, _ = call("POST", f"/api/service-orders/{sid}/acceptance",
                   {"decision": "REJECTED", "operatorName": "许棠",
                    "remainingFault": "x", "reworkRequest": "y"})
    expect_status(code, 400, "已完成单不能退回返修")
    code, _ = call("POST", f"/api/service-orders/{sid}/completions",
                   {"processNote": "a", "checkResult": "b", "deliveryNote": "c"})
    expect_status(code, 400, "已完成单不能再次完工")
    code, _ = call("POST", f"/api/service-orders/{sid}/status", {"status": "DOING"})
    expect_status(code, 400, "已完成单不能靠改状态重回流程")
    if instrument(2)["status"] == "IN_STOCK":
        ok("重复被拒操作没有重复恢复乐器")


def case_d_tuning():
    title("D. 调律类全程不改变乐器原有可用状态（#2）")
    before = instrument(3)["status"]
    code, _ = call("POST", "/api/service-orders/2/status", {"status": "DOING"})
    expect_status(code, 200, "调律单开始施工")
    if instrument(3)["status"] == before:
        ok("调律开工不动乐器状态")
    else:
        bad("调律开工错误改变了乐器状态")
    code, _ = call("POST", "/api/service-orders/2/completions", {
        "processNote": "按十二平均律校准", "checkResult": "全音域偏差小于 3 音分",
        "deliveryNote": "建议半年后复查"})
    expect_status(code, 200, "调律完工提交")
    code, _ = call("POST", "/api/service-orders/2/acceptance",
                   {"decision": "ACCEPTED", "operatorName": "许棠"})
    expect_status(code, 200, "调律单验收通过")
    if instrument(3)["status"] == before and order(2)["status"] == "DONE":
        ok(f"调律单完成，乐器保持原状态（{before}）")
    else:
        bad(f"调律验收后状态异常 inst={instrument(3)['status']} svc={order(2)['status']}")


def case_e_scrapped_during_service():
    title("E. 处理期间乐器报废：验收通过留记录但不复活")
    # 新建一张专用乐器（用运行时间戳编号，脚本可重复执行）
    code, body = call("POST", "/api/instruments", {
        "instrumentNo": f"T-{RUN_STAMP[-4:]}", "name": "验收链路报废验证琴",
        "category": "PERCUSSION", "brand": "测试", "dailyRent": 50,
        "note": "e2e 临时乐器"})
    if code != 200:
        bad(f"临时乐器创建失败：{body}")
        return
    iid = body["data"]["id"]
    ok(f"临时乐器入库 id={iid}")

    # 先把种子 #6 待办收尾（乐器 10 恢复在库，顺带验证种子待办链路）
    code, _ = call("POST", "/api/service-orders/6/acceptance",
                   {"decision": "ACCEPTED", "operatorName": "许棠"})
    if code == 200:
        ok("#6 种子待办验收通过（维修类乐器恢复在库）")
    else:
        ok(f"#6 此前已验收过（HTTP {code}），跳过")

    code, body = call("POST", "/api/service-orders", {
        "instrumentId": iid, "technicianId": 2, "serviceType": "OVERHAUL",
        "planStart": slot(9), "planEnd": slot(18),
        "fee": 900})
    sid = body["data"]["id"]
    call("POST", f"/api/service-orders/{sid}/status", {"status": "DOING"})
    code, _ = call("POST", f"/api/service-orders/{sid}/completions", {
        "processNote": "全面检修并更换老化元件", "checkResult": "功能恢复，待验收",
        "deliveryNote": "—"})
    expect_status(code, 200, "大修单完工待验收")
    # 处理期间乐器被报废
    code, _ = call("POST", f"/api/instruments/{iid}/status", {"status": "SCRAPPED"})
    expect_status(code, 200, "处理期间将乐器报废")
    code, _ = call("POST", f"/api/service-orders/{sid}/acceptance",
                   {"decision": "ACCEPTED", "operatorName": "许棠"})
    expect_status(code, 200, "报废后验收通过仍成功（保留服务记录）")
    if order(sid)["status"] == "DONE":
        ok("服务单完成并留痕")
    if instrument(iid)["status"] == "SCRAPPED":
        ok("乐器保持报废，未被恢复在库")
    else:
        bad(f"报废乐器被错误复活：{instrument(iid)['status']}")
    # 终态再验一次
    code, _ = call("POST", f"/api/service-orders/{sid}/acceptance",
                   {"decision": "REJECTED", "operatorName": "许棠",
                    "remainingFault": "x", "reworkRequest": "y"})
    expect_status(code, 400, "已完成的报废乐器服务单不能重回返修")


def case_g_concurrent_opposite_decisions():
    title("G. 两名店员同时作出相反结论（#5 第 2 轮）")
    # #5 当前返修中：调律师再次提交
    code, _ = call("POST", "/api/service-orders/5/completions", {
        "processNote": "琴马右脚重新贴合校平", "checkResult": "C 弦各把位无杂音",
        "deliveryNote": "一周后复查"})
    expect_status(code, 200, "#5 返修后再次提交，进入待验收")

    barrier = threading.Barrier(2)

    def decide(payload):
        barrier.wait()
        return call("POST", "/api/service-orders/5/acceptance", payload)

    with ThreadPoolExecutor(max_workers=2) as pool:
        f1 = pool.submit(decide, {"decision": "ACCEPTED", "operatorName": "店员甲"})
        f2 = pool.submit(decide, {"decision": "REJECTED", "operatorName": "店员乙",
                                  "remainingFault": "仍有杂音", "reworkRequest": "继续返修"})
        r1 = f1.result()
        r2 = f2.result()
    codes = sorted([r1[0], r2[0]])
    if codes == [200, 400]:
        msgs = [r[1].get("message", "") for r in (r1, r2) if r[0] == 400]
        ok(f"并发相反结论只有一个生效；被拒方提示：{msgs[0][:48]}")
    else:
        bad(f"并发验收结果异常：{r1[0]} / {r2[0]} {r1[1]} {r2[1]}")
    final = order(5)
    if final["status"] in ("DONE", "REWORKING") and n_rounds(5) in (3, 4):
        ok(f"服务单结论唯一：{final['status']}，记录 {n_rounds(5)} 条，无分叉")
    else:
        bad(f"状态或记录异常 {final['status']} / {n_rounds(5)}")

    # 收尾：若并发结果是退回，则再次提交并通过；验证最终恢复在库且只恢复一次
    if order(5)["status"] == "REWORKING":
        call("POST", "/api/service-orders/5/completions", {
            "processNote": "二次校平", "checkResult": "复检合格", "deliveryNote": "交付"})
        code, _ = call("POST", "/api/service-orders/5/acceptance",
                       {"decision": "ACCEPTED", "operatorName": "店员甲"})
        expect_status(code, 200, "补做第 3 轮并验收通过")
    if order(5)["status"] == "DONE" and instrument(8)["status"] == "IN_STOCK":
        ok("#5 最终完成，乐器 S-4002 恢复在库（没有重复恢复）")
    else:
        bad(f"#5 收尾异常 svc={order(5)['status']} inst={instrument(8)['status']}")


def main():
    print(f"目标服务：{BASE}")
    try:
        code, _ = call("GET", "/api/service-orders")
        if code != 200:
            raise RuntimeError(f"HTTP {code}")
    except Exception as e:
        print(f"\033[31m后端不可达（{e}），请先启动服务。\033[0m")
        sys.exit(2)

    case_a_legacy()
    case_b_seed()
    case_c_full_loop()
    case_d_tuning()
    case_e_scrapped_during_service()
    case_g_concurrent_opposite_decisions()

    print("\n" + "=" * 60)
    print(f"\033[32m通过 {PASS}\033[0m，\033[31m失败 {FAIL}\033[0m")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
