#!/usr/bin/env bash
# Installs the release APK on the emulator, walks through the main flows, then lets `monkey`
# tap around. Fails if the app crashes or stops responding. Screenshots go to smoke-shots/.
set -uo pipefail

PKG=com.kekitemkekifalta
APK=$(ls apk/*.apk | head -n 1)
mkdir -p smoke-shots
SHOT=0

fail() {
  echo "::error::$1"
  adb logcat -d -b crash > smoke-crash.log 2>&1 || true
  adb logcat -d > smoke-logcat.log 2>&1 || true
  cat smoke-crash.log || true
  exit 1
}

alive() {
  adb shell pidof "$PKG" > /dev/null || fail "O app fechou durante: $1"
}

shot() {
  SHOT=$((SHOT + 1))
  adb exec-out screencap -p > "smoke-shots/$(printf '%02d' $SHOT)-$1.png" 2>/dev/null || true
}

# Taps the first on-screen node whose text or content-desc matches $1 (exact). Soft: logs if missing.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  local xml
  xml=$(adb shell cat /sdcard/ui.xml 2>/dev/null)
  local point
  point=$(python3 - "$1" "$xml" <<'PY'
import re, sys
want, xml = sys.argv[1], sys.argv[2]
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    text = re.search(r' text="([^"]*)"', node)
    desc = re.search(r' content-desc="([^"]*)"', node)
    if (text and text.group(1) == want) or (desc and desc.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
  if [ -n "$point" ]; then
    adb shell input tap $point
    echo "tap '$1' at $point"
  else
    echo "não achei '$1' na tela"
  fi
  sleep 1.5
}

# Taps the first text field on screen.
tap_field() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  local point
  point=$(adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
xml = sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", xml):
    node = m.group(0)
    if "android.widget.EditText" in node:
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
')
  if [ -n "$point" ]; then
    adb shell input tap $point
    echo "tap campo de texto at $point"
  else
    echo "nenhum campo de texto na tela"
  fi
  sleep 1
}

type_text() {
  adb shell input text "$1"
  sleep 1
}

enter() {
  adb shell input keyevent 66
  sleep 1.5
}

adb install -r -g "$APK" || fail "Falha ao instalar o APK"
adb logcat -c || true

adb shell am start -W -n "$PKG/.MainActivity" || fail "Falha ao abrir o app"
sleep 6
alive "abertura"
shot kekitem-vazio

# --- Walkthrough of the main flow ---
tap_field
type_text "Banana"
enter
type_text "Arroz"
enter
type_text "Coisa%sdiferente"
sleep 1
shot autocompletar
tap "Outros"
adb shell input keyevent 4 # hide keyboard
sleep 1
alive "adicionar itens"
shot kekitem-com-itens

tap "acabou"
alive "marcar acabou"
shot depois-de-acabou

tap "Kekifalta"
alive "abrir kekifalta"
shot kekifalta

tap "Ir ao mercado"
tap_field
type_text "Mercado%steste"
tap "Criar e ir"
alive "criar mercado"
shot modo-mercado

tap "Banana"
tap "Arroz"
shot itens-marcados
tap "Concluir compra"
tap "Concluir"
alive "concluir compra"
shot depois-da-compra

tap "Mercado"
tap "Mercado teste"
alive "mapa do mercado"
shot mapa
tap "Editar rota"
alive "editar rota"
shot rota
adb shell input keyevent 4
adb shell input keyevent 4
sleep 1

tap "Kekitem"
tap "Arroz"
alive "detalhe do item"
shot item
adb shell input keyevent 4
sleep 1

tap "Ajustes"
alive "ajustes"
shot ajustes
tap "Escuro"
tap "Kekitem"
shot kekitem-escuro
tap "Ajustes"
tap "Sistema"
alive "tema"

# --- Random taps ---
adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle 150 -s 4242 -v 1500 > smoke-monkey.log 2>&1
status=$?
tail -n 30 smoke-monkey.log
if [ $status -ne 0 ] || grep -qE "// CRASH|// NOT RESPONDING" smoke-monkey.log; then
  fail "O app quebrou durante o monkey (ver smoke-monkey.log)"
fi
shot depois-do-monkey

adb logcat -d -b crash > smoke-crash.log 2>&1 || true
if grep -q "$PKG" smoke-crash.log; then
  fail "Crash registrado no logcat"
fi
echo "Smoke OK"
