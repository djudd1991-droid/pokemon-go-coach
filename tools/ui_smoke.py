"""Launch the real Android app on a disposable emulator and verify key pages."""

from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = Path(".checks/ui")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args])


def page(name, expected):
    for attempt in range(8):
        adb("shell", "uiautomator", "dump", "/sdcard/coach-ui.xml")
        xml = adb("shell", "cat", "/sdcard/coach-ui.xml")
        tree = ET.fromstring(xml)
        if any(expected in n.get("text", "") for n in tree.iter("node")):
            (OUT / (name + ".xml")).write_bytes(xml)
            (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
            return tree
        time.sleep(1)
    raise AssertionError("Missing screen text: " + expected)


def tap(tree, text):
    for attempt in range(5):
        node = next((n for n in tree.iter("node") if text in n.get("text", "")), None)
        if node is not None:
            break
        size = adb("shell", "wm", "size").decode()
        width, height = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])
        adb("shell", "input", "swipe", str(width//2), str(height*3//4), str(width//2), str(height//3), "400")
        adb("shell", "uiautomator", "dump", "/sdcard/coach-ui.xml")
        tree = ET.fromstring(adb("shell", "cat", "/sdcard/coach-ui.xml"))
    if node is None:
        raise AssertionError("Missing control: " + text)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


try:
    adb("install", "-r", sys.argv[1])
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", "com.david.gocoach/.MainActivity")
    home = page("home", "Start coaching")
    tap(home, "Learning memory")
    memory = page("learning", "Delete all Wild Map learning")
    tap(memory, "Back")
    home = page("home-return", "Start coaching")
    tap(home, "My Pokémon")
    page("collection", "My Pokémon")
    crashes = adb("logcat", "-d", "-b", "crash")
    if b"com.david.gocoach" in crashes:
        raise AssertionError("App crash detected")
    print("PASS: home, memory and collection launch; native screenshots captured.")
finally:
    (OUT / "logcat.txt").write_bytes(adb("logcat", "-d"))
