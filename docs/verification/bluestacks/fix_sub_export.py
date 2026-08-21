import re, subprocess, time, xml.etree.ElementTree as ET, json
from pathlib import Path
ADB=r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S="emulator-5554"
PKG="com.mars.planner.debug"
OUT=Path(r"F:\1\Cursor\Mars\Mars\docs\verification\bluestacks")

def adb(*a):
    return subprocess.run([ADB,"-s",S,*a], capture_output=True)
def adb_out(*a):
    return subprocess.check_output([ADB,"-s",S,*a])
def dump():
    adb("shell","uiautomator","dump","/sdcard/ui.xml")
    xml=adb_out("shell","cat","/sdcard/ui.xml").decode("utf-8","replace")
    i=xml.find("<?xml"); xml=xml[i:] if i>=0 else xml
    return ET.fromstring(xml)
def bounds(n):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None
def tap_text(needle, contains=True):
    root=dump()
    for n in root.iter("node"):
        t=(n.attrib.get("text") or "").strip()
        if (needle in t) if contains else t==needle:
            b=bounds(n)
            if b:
                x=(b[0]+b[2])//2; y=(b[1]+b[3])//2
                print("tap",t,b); adb("shell","input","tap",str(x),str(y)); time.sleep(0.8); return True
    print("MISS",needle,[ (n.attrib.get("text") or "") for n in root.iter("node") if (n.attrib.get("text") or "").strip()][:30])
    return False
def type_text(t):
    adb("shell","input","text", t.replace(" ","%s")); time.sleep(0.3)
def shot(name):
    raw=adb_out("exec-out","screencap","-p").replace(b"\r\n",b"\n")
    (OUT/name).write_bytes(raw); print("shot",name,len(raw))

# open task A
adb("shell","am","start","-n",f"{PKG}/com.mars.planner.MainActivity"); time.sleep(1.5)
tap_text("Задачи"); time.sleep(0.7)
tap_text("Osnovnaya_A_otchet"); time.sleep(1)
shot("11b_before_sub.png")
# add subtask with Создать
tap_text("Добавить")  # first one - subtasks section usually first
time.sleep(0.6)
# if enhancement dialog, cancel and use first
labs=" ".join([(n.attrib.get("text") or "") for n in dump().iter("node")])
print("dialog labs snippet", labs[:200])
if "дополн" in labs.lower() or "Иде" in labs or "idea" in labs.lower():
    tap_text("Отмена"); time.sleep(0.5)
    # try coordinates of first Добавить from dump
    root=dump(); adds=[]
    for n in root.iter("node"):
        if (n.attrib.get("text") or "")=="Добавить":
            adds.append(bounds(n))
    print("adds",adds)
    if adds:
        b=adds[0]; adb("shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(0.8)

tap_text("Название"); type_text("Podzadacha_cifry")
if not tap_text("Создать"):
    tap_text("Сохранить")
time.sleep(1)
shot("11c_after_sub.png")
labs=" ".join([(n.attrib.get("text") or "") for n in dump().iter("node")])
print("HAS_SUB", "Podzadacha_cifry" in labs)
print("HAS_ENH", "Ideya_grafik" in labs)
adb("shell","input","keyevent","4"); time.sleep(0.7)

# re-export
tap_text("Настройки"); time.sleep(0.8)
tap_text("Экспорт JSON"); time.sleep(1)
tap_text("Экспорт CSV"); time.sleep(1)
base=f"/sdcard/Android/data/{PKG}/files"
listing=adb_out("shell","ls","-1",base).decode()
print(listing)
jsons=sorted([x for x in listing.splitlines() if "mars_backup_" in x])
csvs=sorted([x for x in listing.splitlines() if "mars_tasks_" in x])
j,c=jsons[-1],csvs[-1]
adb("pull",f"{base}/{j}",str(OUT/j))
adb("pull",f"{base}/{c}",str(OUT/c))
data=json.loads((OUT/j).read_text(encoding="utf-8"))
tasks=data["tasks"]; enh=data.get("enhancements") or []
roots=[t for t in tasks if not t.get("parentTaskId") and int(t.get("nestingLevel") or 0)==0]
subs=[t for t in tasks if t.get("parentTaskId")]
print("roots",len(roots),"subs",len(subs),"enh",len(enh))
print("titles",[t["title"] for t in tasks])
csv_text=(OUT/c).read_text(encoding="utf-8")
print("CSV has Podzadacha?", "Podzadacha" in csv_text)
print("CSV lines", len(csv_text.splitlines()))
print("JSON_PATH", OUT/j)
print("CSV_PATH", OUT/c)
