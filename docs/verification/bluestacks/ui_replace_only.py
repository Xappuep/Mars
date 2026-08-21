import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB=r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"; S="emulator-5554"; PKG="com.mars.planner.debug"
OUT=Path(r"F:\1\Cursor\Mars\Mars\docs\verification\bluestacks")
FN="mars_backup_1787341748162.json"

def adb(*a): return subprocess.run([ADB,"-s",S,*a], capture_output=True)
def out(*a): return subprocess.check_output([ADB,"-s",S,*a])
def dump():
    adb("shell","uiautomator","dump","/sdcard/ui.xml")
    x=out("shell","cat","/sdcard/ui.xml").decode("utf-8","replace"); i=x.find("<?xml"); x=x[i:] if i>=0 else x
    return ET.fromstring(x)
def B(n):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None
def labs(): return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]
def tap(text, exact=False):
    for n in dump().iter("node"):
        t=(n.attrib.get("text") or "").strip()
        if (t==text) if exact else (text in t):
            b=B(n)
            if b:
                print("tap",t,b); adb("shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(0.9); return True
    print("MISS",text, labs()[:25]); return False
def shot(n):
    raw=out("exec-out","screencap","-p").replace(b"\r\n",b"\n"); (OUT/n).write_bytes(raw); print("shot",n)
def back(k=1):
    for _ in range(k): adb("shell","input","keyevent","4"); time.sleep(0.5)

# dismiss picker
for _ in range(5):
    L=" ".join(labs())
    if "Загрузки" in L and "Объединить" not in L and "Настройки" not in L:
        back()
    else:
        break
adb("shell","am","start","-n",f"{PKG}/com.mars.planner.MainActivity"); time.sleep(1.2)
tap("Настройки"); time.sleep(0.5)
tap("Импорт JSON"); time.sleep(1.2)
shot("40_picker.png")
print("picker", labs())
# if already in downloads with file
if FN not in " ".join(labs()):
    tap("Загрузки"); time.sleep(0.8)
# click file center
tap(FN); time.sleep(0.5)
# list all Открыть nodes
opens=[]
for n in dump().iter("node"):
    if (n.attrib.get("text") or "").strip()=="Открыть":
        opens.append((B(n), n.attrib.get("enabled"), n.attrib.get("clickable")))
print("Open buttons", opens)
# Prefer clickable enabled Open; if none, tap last Open
if opens:
    # choose largest Y (likely action bar bottom) or enabled
    opens_sorted=sorted([o for o in opens if o[0]], key=lambda o: o[0][1])
    # try bottommost
    b=opens_sorted[-1][0]
    print("tap Open at", b)
    adb("shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(1.2)
shot("41_after_open.png")
print("after open", labs()[:30])
# if still picker, try double tap file
if "Объединить" not in " ".join(labs()):
    tap(FN); time.sleep(0.3); tap(FN); time.sleep(1.2)
    print("after dbl", labs()[:30])
shot("42_dialog1.png")
assert "Объединить" in " ".join(labs()), "no import dialog"
# Заменить…
tap("Заменить"); time.sleep(1)
shot("43_dialog2.png")
print("dialog2", labs())
# confirm Заменить exact
ok=False
for n in dump().iter("node"):
    if (n.attrib.get("text") or "").strip()=="Заменить":
        b=B(n)
        if b:
            print("confirm", b); adb("shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); ok=True; time.sleep(1.3); break
print("confirmed", ok)
shot("44_after_replace.png")
print("msgs", [x for x in labs() if "замен" in x.lower() or "Резерв" in x or "pre_replace" in x or "Данные" in x])
# filesDir via run-as correctly
r=adb("shell","run-as",PKG,"ls","files")
print("filesDir", r.stdout.decode("utf-8","replace"))
print("stderr", r.stderr.decode("utf-8","replace"))
