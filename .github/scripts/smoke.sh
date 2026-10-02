#!/usr/bin/env bash
# Installs the release APK on the emulator, opens it and lets `monkey` tap around.
# Fails if the app crashes or stops responding.
set -uo pipefail

PKG=com.kekitemkekifalta
APK=$(ls apk/*.apk | head -n 1)

fail() {
  echo "::error::$1"
  adb logcat -d -b crash > smoke-crash.log 2>&1 || true
  adb logcat -d > smoke-logcat.log 2>&1 || true
  cat smoke-crash.log || true
  exit 1
}

adb install -r -g "$APK" || fail "Falha ao instalar o APK"
adb logcat -c || true

adb shell am start -W -n "$PKG/.MainActivity" || fail "Falha ao abrir o app"
sleep 8
adb shell pidof "$PKG" > /dev/null || fail "O app fechou logo depois de abrir"

adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle 150 -s 4242 -v 1500 > smoke-monkey.log 2>&1
status=$?
tail -n 30 smoke-monkey.log
if [ $status -ne 0 ] || grep -qE "// CRASH|// NOT RESPONDING" smoke-monkey.log; then
  fail "O app quebrou durante o monkey (ver smoke-monkey.log)"
fi

adb logcat -d -b crash > smoke-crash.log 2>&1 || true
if grep -q "$PKG" smoke-crash.log; then
  fail "Crash registrado no logcat"
fi
echo "Smoke OK"
