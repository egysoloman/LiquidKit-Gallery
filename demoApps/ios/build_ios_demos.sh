#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-/tmp/liquidkit-ios-demos}"
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
ARCH="$(uname -m)"
TARGET="${ARCH}-apple-ios27.0-simulator"

mkdir -p "$OUT_DIR"

build_app() {
  local NAME="$1"
  local BUNDLE_ID="$2"
  local DISPLAY_NAME="$3"
  local SRC="$SCRIPT_DIR/${NAME}App.swift"
  local APP_DIR="$OUT_DIR/${NAME}.app"

  rm -rf "$APP_DIR"
  mkdir -p "$APP_DIR"

  xcrun --sdk iphonesimulator swiftc \
    -parse-as-library \
    -sdk "$SDK" \
    -target "$TARGET" \
    -framework SwiftUI \
    -framework UIKit \
    -framework Charts \
    -O \
    "$SRC" \
    -o "$APP_DIR/$NAME"

  cat > "$APP_DIR/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key><string>zh-Hans</string>
  <key>CFBundleDisplayName</key><string>${DISPLAY_NAME}</string>
  <key>CFBundleExecutable</key><string>${NAME}</string>
  <key>CFBundleIdentifier</key><string>${BUNDLE_ID}</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleName</key><string>${NAME}</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSRequiresIPhoneOS</key><true/>
  <key>UIApplicationSceneManifest</key>
  <dict>
    <key>UIApplicationSupportsMultipleScenes</key><true/>
  </dict>
  <key>UILaunchScreen</key><dict/>
  <key>UIRequiredDeviceCapabilities</key><array><string>arm64</string></array>
  <key>UISupportedInterfaceOrientations</key>
  <array>
    <string>UIInterfaceOrientationPortrait</string>
    <string>UIInterfaceOrientationLandscapeLeft</string>
    <string>UIInterfaceOrientationLandscapeRight</string>
  </array>
</dict>
</plist>
PLIST

  codesign --force --sign - "$APP_DIR" >/dev/null 2>&1
  echo "Built iOS app: $APP_DIR ($BUNDLE_ID)"
}

build_app "GlassMusic" "com.liquidkit.demo.music" "流光音乐"
build_app "GlassWeather" "com.liquidkit.demo.weather" "晴雨气象"
build_app "GlassControl" "com.liquidkit.demo.control" "智能中枢"
