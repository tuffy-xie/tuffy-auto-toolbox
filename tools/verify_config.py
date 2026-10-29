#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
配置保存 / 恢复 / 队列生成 / 今日-明日隔离 验证（真机）。

为什么用 Python：uiautomator dump 只返回可见节点，需要「回顶 -> 逐屏下滚 -> 到底即停」
的查找逻辑；PowerShell 版在滚动与坐标刷新上不稳定，故用本驱动完成同一套断言。

用法:
    python tools/verify_config.py
    python tools/verify_config.py --ad-target 5
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime

ADB = os.environ.get("ADB", r"C:\Users\xiejiatao\android-tools\platform-tools\adb.exe")
PKG = "com.anapocket.auto"
SVC = "com.anapocket.auto/com.anapocket.auto.AutoAccessibilityService"
LOG = "/sdcard/Android/data/com.anapocket.auto/files/logs/ana_auto.log"
EV = "/sdcard/Android/data/com.anapocket.auto/files/evidence"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STAMP = datetime.now().strftime("CFG-%Y%m%d-%H%M%S")
OUT = os.path.join(ROOT, "evidence", STAMP)
os.makedirs(OUT, exist_ok=True)

results = []
lines = []


def log(s):
    print(s)
    lines.append(s)


def res(i, name, status, note):
    results.append({"id": i, "name": name, "status": status, "note": note})
    print("  [%s] %s %s - %s" % (status, i, name, note))


def sh(cmd, timeout=90):
    p = subprocess.run([ADB, "shell", cmd], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return (p.stdout or "") + (p.stderr or "")


def shq(cmd):
    return sh(cmd).strip()


def dump_nodes(retries=3):
    raw = ""
    for _ in range(retries):
        sh("uiautomator dump /sdcard/v.xml")
        raw = sh("cat /sdcard/v.xml")
        if "</hierarchy>" in raw:
            break
        time.sleep(0.8)
    i = raw.find("<?xml")
    if i < 0:
        return []
    raw = raw[i:]
    j = raw.rfind("</hierarchy>")
    if j > 0:
        raw = raw[: j + len("</hierarchy>")]
    try:
        root = ET.fromstring(raw)
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        b = n.attrib.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        out.append({
            "text": (n.attrib.get("text") or "").strip(),
            "desc": (n.attrib.get("content-desc") or "").strip(),
            "cls": n.attrib.get("class") or "",
            "checked": n.attrib.get("checked") or "false",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
        })
    return out


def swipe(x1, y1, x2, y2, ms=260):
    sh("input swipe %d %d %d %d %d" % (x1, y1, x2, y2, ms))


def scroll_top():
    for _ in range(4):
        swipe(700, 500, 700, 1500, 200)
        time.sleep(0.2)
    time.sleep(0.35)


def find_any(text, cls=None, max_screens=24):
    """回顶后逐屏下滚查找；到底（签名不再变化）即停。"""
    scroll_top()
    prev = None
    for _ in range(max_screens):
        nodes = dump_nodes()
        for n in nodes:
            if (n["text"] == text or n["desc"] == text) and (cls is None or n["cls"] == cls):
                # 等滚动惯性停下后重新取一次坐标，避免点到旧位置
                time.sleep(0.45)
                for m in dump_nodes():
                    if (m["text"] == text or m["desc"] == text) and (cls is None or m["cls"] == cls):
                        return m
                return n
        if not nodes:
            time.sleep(0.8)
            continue
        sig = tuple((n["text"], n["cx"], n["cy"]) for n in nodes)
        if sig == prev:
            return None
        prev = sig
        swipe(700, 1300, 700, 500, 260)
        time.sleep(0.45)
    return None


def tap(n):
    sh("input tap %d %d" % (n["cx"], n["cy"]))
    time.sleep(0.5)


def snapshot(name):
    shq("cat %s/settings_snapshot.json" % EV)
    raw = shq("cat %s/settings_snapshot.json" % EV)
    if not raw:
        return None
    try:
        d = json.loads(raw)
    except Exception:
        return None
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
    return d


def log_baseline():
    out = shq("wc -l < %s" % LOG)
    m = re.search(r"(\d+)", out)
    return int(m.group(1)) if m else 0


def log_new(base):
    out = sh("cat %s" % LOG)
    arr = out.split("\n")
    return "\n".join(arr[base:])


def ensure_a11y(base):
    d = sh("dumpsys accessibility")
    m = re.search(r"Bound services:\{([^}]*)\}", d)
    bound = bool(m and PKG in m.group(1))
    if bound:
        return True
    log("- 无障碍未绑定，重新写入 enabled_accessibility_services")
    sh("settings put secure enabled_accessibility_services %s" % SVC)
    sh("settings put secure accessibility_enabled 1")
    for _ in range(12):
        time.sleep(2)
        if "service connected" in log_new(base):
            return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ad-target", type=int, default=0, help="验证结束后把广告目标设为该值（0=不改）")
    args = ap.parse_args()

    log("# 配置保存 / 恢复 / 队列生成 验证（真机）")
    log("")
    log("| 项 | 值 |")
    log("|---|---|")
    log("| 验证 ID | `%s` |" % STAMP)
    log("| 时间 | %s |" % datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %z"))
    log("| 设备 | %s |" % shq("getprop ro.product.model"))

    base = log_baseline()
    sh("am force-stop %s" % PKG)
    time.sleep(1.5)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(3)
    a11y = ensure_a11y(base)
    log("| 无障碍绑定 | %s |" % a11y)
    if not a11y:
        res("C0", "无障碍服务绑定", "FAIL", "无法绑定，后续无法验证")
        finish()
        return

    A = snapshot("snapshot_A.json")
    if not A:
        res("C0", "读取设置快照", "FAIL", "settings_snapshot.json 不存在")
        finish()
        return
    log("| 初始配置 | adTarget=%s dayToday=%s dayTomorrow=%s CAR=%s WALKkm=%s |" % (
        A["adTarget"], A["dayToday"], A["dayTomorrow"], A["catEnabled"]["CAR"], A["catKm"]["WALK"]))

    # ---------- 1. 修改配置 ----------
    car = find_any("車", cls="android.widget.CheckBox")
    if car and car["checked"] == "true":
        tap(car)
    time.sleep(0.4)

    walk = find_any("徒歩", cls="android.widget.CheckBox")
    edit = None
    if walk:
        for n in dump_nodes():
            if n["cls"] == "android.widget.EditText" and abs(n["cy"] - walk["cy"]) < 40:
                edit = n
                break
    if edit:
        tap(edit)
        time.sleep(0.4)
        sh("input keyevent KEYCODE_MOVE_END")
        for _ in range(3):
            sh("input keyevent KEYCODE_DEL")
        sh("input text 6")
        time.sleep(0.4)
        sh("input keyevent KEYCODE_BACK")
        time.sleep(0.5)

    tm = find_any("明日（参加予約）", cls="android.widget.CheckBox")
    if tm and tm["checked"] != "true":
        tap(tm)
    time.sleep(0.4)

    save = find_any("保存配置")
    saved = False
    if save:
        tap(save)
        saved = True
    time.sleep(2.0)
    B = snapshot("snapshot_B.json")

    log("")
    log("## 1. 保存")
    log("")
    if B:
        log("- 保存后快照：dayToday=%s dayTomorrow=%s CAR=%s WALKkm=%s" % (
            B["dayToday"], B["dayTomorrow"], B["catEnabled"]["CAR"], B["catKm"]["WALK"]))
    okB = bool(saved and B and (B["catEnabled"]["CAR"] is False) and B["catKm"]["WALK"] == 6 and B["dayTomorrow"])
    res("C1", "界面修改后保存生效", "PASS" if okB else "FAIL",
        "saved=%s CAR=%s WALKkm=%s tomorrow=%s" % (
            saved, B["catEnabled"]["CAR"] if B else "?", B["catKm"]["WALK"] if B else "?", B["dayTomorrow"] if B else "?"))

    # ---------- 2. 进程重启后持久化 ----------
    sh("am force-stop %s" % PKG)
    time.sleep(1.5)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(2.5)
    a11y2 = ensure_a11y(base)
    log("- force-stop 后重新绑定无障碍：%s" % a11y2)
    time.sleep(1.5)
    C = snapshot("snapshot_C.json")

    log("")
    log("## 2. 重启后恢复")
    log("")
    if C:
        log("- 重启后快照：dayToday=%s dayTomorrow=%s CAR=%s WALKkm=%s" % (
            C["dayToday"], C["dayTomorrow"], C["catEnabled"]["CAR"], C["catKm"]["WALK"]))
    okC = bool(C and B and C["adTarget"] == B["adTarget"] and C["dayToday"] == B["dayToday"]
               and C["dayTomorrow"] == B["dayTomorrow"]
               and C["catEnabled"]["CAR"] == B["catEnabled"]["CAR"]
               and C["catKm"]["WALK"] == B["catKm"]["WALK"])
    res("C2", "配置在进程重启后持久化（DataStore）", "PASS" if okC else "FAIL",
        "restart CAR=%s WALKkm=%s tomorrow=%s" % (
            C["catEnabled"]["CAR"] if C else "?", C["catKm"]["WALK"] if C else "?", C["dayTomorrow"] if C else "?"))

    # ---------- 3. 界面控件恢复 ----------
    car2 = find_any("車", cls="android.widget.CheckBox")
    tm2 = find_any("明日（参加予約）", cls="android.widget.CheckBox")
    walk2 = find_any("徒歩", cls="android.widget.CheckBox")
    edit2 = None
    if walk2:
        for n in dump_nodes():
            if n["cls"] == "android.widget.EditText" and abs(n["cy"] - walk2["cy"]) < 40:
                edit2 = n
                break
    ui_ok = bool(car2 and car2["checked"] == "false" and tm2 and tm2["checked"] == "true"
                 and edit2 and edit2["text"] == "6")
    log("- 界面状态：車 checked=%s 明日 checked=%s 徒歩km=%s" % (
        car2["checked"] if car2 else "?", tm2["checked"] if tm2 else "?", edit2["text"] if edit2 else "?"))
    res("C3", "重启后界面控件恢复为已保存值", "PASS" if ui_ok else "FAIL",
        "car=%s tomorrow=%s walkKm=%s" % (
            car2["checked"] if car2 else "?", tm2["checked"] if tm2 else "?", edit2["text"] if edit2 else "?"))

    # ---------- 4. 队列生成 ----------
    base2 = log_baseline()
    a11y3 = ensure_a11y(base2)
    run = find_any("立即执行已选任务")
    if run:
        tap(run)
    qt = ""
    for _ in range(30):
        time.sleep(3)
        qt = log_new(base2)
        if "queue size=" in qt:
            break
    m = re.search(r"queue size=(\d+) :: (.+)", qt)
    ids = []
    if m:
        ids = [x.strip() for x in m.group(2).strip().split(",")]
    ch = [i for i in ids if i.startswith("CH-")]
    has_car = any(i.endswith("-CAR") for i in ch)
    dates = []
    for i in ch:
        mm = re.match(r"CH-(\d{4}-\d{2}-\d{2})-", i)
        if mm and mm.group(1) not in dates:
            dates.append(mm.group(1))

    log("")
    log("## 3. 队列生成")
    log("")
    log("- a11y=%s clicked=%s" % (a11y3, bool(run)))
    log("- queue：%s 项" % (m.group(1) if m else "?"))
    log("- ids：%s" % ", ".join(ids))
    q_ok = bool(run and m and (not has_car) and len(dates) >= 2)
    res("C4", "按选择生成队列（跳过未选的車、含明日）", "PASS" if q_ok else "FAIL", "ids=%s" % ",".join(ids))
    res("C5", "今日/明日任务日期隔离", "PASS" if len(dates) >= 2 else "FAIL", "dates=%s" % ",".join(dates))

    # 运行开始后引擎会把 ANA Pocket 拉到前台，必须等焦点真正回到本应用再找按钮
    stop = None
    for _ in range(10):
        sh("am start -n %s/.MainActivity" % PKG)
        time.sleep(1.5)
        if PKG in sh("dumpsys window | grep mCurrentFocus"):
            stop = find_any("停止运行")
            if stop:
                break
        time.sleep(1.0)
    if stop:
        tap(stop)
    res("C6", "停止运行按钮可用", "PASS" if stop else "FAIL", "点击后引擎在下一步检查点退出")

    # ---------- 5. 还原配置 ----------
    time.sleep(2)
    for _ in range(3):
        sh("am start -n %s/.MainActivity" % PKG)
        time.sleep(2.5)
        if PKG in sh("dumpsys window | grep mCurrentFocus"):
            break
    want_car = "true" if A["catEnabled"]["CAR"] else "false"
    car3 = find_any("車", cls="android.widget.CheckBox")
    if car3 and car3["checked"] != want_car:
        for _ in range(3):
            tap(car3)
            time.sleep(0.7)
            again = find_any("車", cls="android.widget.CheckBox")
            if again and again["checked"] == want_car:
                car3 = again
                break
            car3 = again
    walk3 = find_any("徒歩", cls="android.widget.CheckBox")
    edit3 = None
    if walk3:
        for n in dump_nodes():
            if n["cls"] == "android.widget.EditText" and abs(n["cy"] - walk3["cy"]) < 40:
                edit3 = n
                break
    if edit3:
        tap(edit3)
        time.sleep(0.35)
        sh("input keyevent KEYCODE_MOVE_END")
        for _ in range(3):
            sh("input keyevent KEYCODE_DEL")
        sh("input text %s" % A["catKm"]["WALK"])
        time.sleep(0.35)
        sh("input keyevent KEYCODE_BACK")
        time.sleep(0.4)
    if not A["dayTomorrow"]:
        tm3 = find_any("明日（参加予約）", cls="android.widget.CheckBox")
        if tm3 and tm3["checked"] == "true":
            for _ in range(3):
                tap(tm3)
                time.sleep(0.7)
                again = find_any("明日（参加予約）", cls="android.widget.CheckBox")
                if again and again["checked"] == "false":
                    break
                tm3 = again

    if args.ad_target > 0:
        sp = find_any("", cls="android.widget.Spinner")
        if sp:
            tap(sp)
            time.sleep(1.0)
            opt = None
            for _ in range(6):
                for n in dump_nodes():
                    if n["text"] == str(args.ad_target):
                        opt = n
                        break
                if opt:
                    break
                time.sleep(0.5)
            if opt:
                tap(opt)
                log("- 广告目标设为 %d（测试窗口控制）" % args.ad_target)

    save2 = find_any("保存配置")
    if save2:
        tap(save2)
    time.sleep(2)
    A2 = snapshot("snapshot_restored.json")
    restored_ok = bool(A2 and A2["catEnabled"]["CAR"] == A["catEnabled"]["CAR"]
                       and A2["catKm"]["WALK"] == A["catKm"]["WALK"]
                       and A2["dayTomorrow"] == A["dayTomorrow"]
                       and A2["adTarget"] == A["adTarget"])
    res("C7", "测试后配置已还原", "PASS" if restored_ok else "FAIL",
        "CAR=%s WALKkm=%s tomorrow=%s" % (
            A2["catEnabled"]["CAR"] if A2 else "?", A2["catKm"]["WALK"] if A2 else "?", A2["dayTomorrow"] if A2 else "?"))

    finish()


def finish():
    fails = [r for r in results if r["status"] == "FAIL"]
    overall = "FAIL" if fails else "PASS"
    log("")
    log("| ID | 检查项 | 判定 | 说明 |")
    log("|---|---|---|---|")
    for r in results:
        log("| %s | %s | **%s** | %s |" % (r["id"], r["name"], r["status"], r["note"].replace("|", "/")))
    log("")
    log("**总体判定：%s**" % overall)

    with open(os.path.join(OUT, "REPORT.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(OUT, "results.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("")
    print("========== CONFIG RESULT: %s ==========" % overall)
    print("OUT: %s" % OUT)


if __name__ == "__main__":
    main()
