#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HyperOS 上安装 APK 并完成授权（可重复执行）。

背景：HyperOS 的「USB 安装」策略会让 `adb install` 返回
INSTALL_FAILED_USER_RESTRICTED。可行路径是把 APK 推到 /sdcard/Download，
用 content:// URI 调起系统安装器，再自动点掉「更新 / 扫描应用 / 安装」。
本脚本把这条路径 + appops/无障碍授权 + 哈希校验串起来。

用法:
    python tools/install_apk.py dist/ANA-Pocket-Auto-v9-release.apk
"""
import hashlib
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", r"C:\Users\xiejiatao\android-tools\platform-tools\adb.exe")
PKG = "com.anapocket.auto"
SVC = "com.anapocket.auto/com.anapocket.auto.AutoAccessibilityService"


def sh(cmd, timeout=120):
    p = subprocess.run([ADB, "shell", cmd], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return ((p.stdout or "") + (p.stderr or "")).strip()


def nodes():
    sh("uiautomator dump /sdcard/i.xml")
    raw = sh("cat /sdcard/i.xml")
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
        out.append({"text": (n.attrib.get("text") or "").strip(),
                    "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2})
    return out


def find(text):
    for n in nodes():
        if n["text"] == text:
            return n
    return None


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    if len(sys.argv) < 2:
        print("usage: install_apk.py <apk>")
        return 1
    apk = os.path.abspath(sys.argv[1])
    local = sha256(apk)
    print("APK   : %s" % apk)
    print("SHA256: %s" % local)
    print("size  : %d" % os.path.getsize(apk))

    dev_out = subprocess.run([ADB, "devices"], capture_output=True, text=True,
                             encoding="utf-8", errors="replace").stdout
    devs = [l for l in dev_out.splitlines() if l.endswith("\tdevice")]
    if not devs:
        print("ERROR: no device")
        return 2

    out = subprocess.run([ADB, "install", "-r", apk], capture_output=True,
                         text=True, encoding="utf-8", errors="replace").stdout
    print("adb install -> %s" % out.strip().splitlines()[-1] if out.strip() else "adb install -> ?")

    if "Success" not in out:
        print("回退到系统安装器 UI 路径...")
        name = "ana_install_%d.apk" % int(time.time())
        subprocess.run([ADB, "push", apk, "/sdcard/Download/%s" % name], capture_output=True)
        time.sleep(1)
        q = sh("content query --uri content://media/external/file --projection _id:_display_name "
               "--where \"_display_name='%s'\"" % name)
        m = re.search(r"_id=(\d+)", q)
        if not m:
            print("ERROR: media id not found")
            return 3
        mid = m.group(1)
        sh("am start -a android.intent.action.VIEW -d content://media/external/file/%s "
           "-t application/vnd.android.package-archive --grant-read-uri-permission" % mid)
        time.sleep(3)
        for _ in range(14):
            hit = False
            for label in ("更新", "安装", "继续安装", "扫描应用", "仍然安装", "允许"):
                n = find(label)
                if n:
                    sh("input tap %d %d" % (n["cx"], n["cy"]))
                    print("  tap %s" % label)
                    time.sleep(4)
                    hit = True
                    break
            if not hit:
                focus = sh("dumpsys window | grep mCurrentFocus")
                if "packageinstaller" not in focus:
                    break
                time.sleep(2)

    # 授权
    sh("appops set %s ACCESS_RESTRICTED_SETTINGS allow" % PKG)
    sh("appops set %s SYSTEM_ALERT_WINDOW allow" % PKG)
    sh("appops set %s SCHEDULE_EXACT_ALARM allow" % PKG)
    sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    sh("settings put secure enabled_accessibility_services %s" % SVC)
    sh("settings put secure accessibility_enabled 1")
    time.sleep(3)

    path = sh("pm path %s" % PKG)
    base = path.split("package:")[-1].strip() if "package:" in path else ""
    dev = ""
    if base:
        m = re.search(r"([0-9a-fA-F]{64})", sh("sha256sum '%s'" % base))
        if m:
            dev = m.group(1).lower()

    print("pm path   : %s" % path)
    print("device sha: %s" % dev)
    print("match     : %s" % ("YES" if dev == local else "NO"))
    print("a11y      : %s" % sh("settings get secure enabled_accessibility_services"))
    print("version   : %s" % sh("dumpsys package %s | grep versionName" % PKG))
    return 0 if dev == local else 4


if __name__ == "__main__":
    sys.exit(main())
