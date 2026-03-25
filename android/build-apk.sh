#!/usr/bin/env bash
# build-apk.sh -- Clone HeliBoard, patch identity to CircleOne, build APK
# Usage: ./build-apk.sh [--debug]
#
# Requirements: Java 17+, Android SDK (ANDROID_HOME set), Git, Bash

set -euo pipefail

###############################################################################
# Configuration
###############################################################################
HELIBOARD_REPO="https://github.com/Helium314/HeliBoard.git"
HELIBOARD_TAG="v3.8"
WORK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${WORK_DIR}/_build/HeliBoard"
OUTPUT_DIR="${WORK_DIR}/output"
LAYOUT_SRC="${WORK_DIR}/heliboard-config/one_layout.json"
ASSETS_DIR="${WORK_DIR}/assets"

NEW_APP_ID="za.co.thegeek.circleone"
NEW_APP_NAME="CircleOne"
BUILD_TYPE="Release"

if [[ "${1:-}" == "--debug" ]]; then
    BUILD_TYPE="Debug"
fi

###############################################################################
# Preflight checks
###############################################################################
echo "[1/7] Preflight checks..."

if [[ -z "${ANDROID_HOME:-}" ]]; then
    echo "ERROR: ANDROID_HOME is not set. Point it at your Android SDK." >&2
    exit 1
fi

for cmd in git java; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: $cmd is not installed or not on PATH." >&2
        exit 1
    fi
done

###############################################################################
# Clone HeliBoard
###############################################################################
echo "[2/7] Cloning HeliBoard (tag ${HELIBOARD_TAG})..."

if [[ -d "$BUILD_DIR" ]]; then
    echo "       Build directory already exists -- removing stale copy."
    rm -rf "$BUILD_DIR"
fi

git clone --depth 1 --branch "$HELIBOARD_TAG" "$HELIBOARD_REPO" "$BUILD_DIR"

###############################################################################
# Apply CircleOne source patches (IsiBheqeSpan, compose activity, etc.)
###############################################################################
echo "[2.5/7] Applying CircleOne source patches..."

PATCH_SCRIPT="${WORK_DIR}/patch-heliboard.sh"
if [[ -f "$PATCH_SCRIPT" ]]; then
    bash "$PATCH_SCRIPT"
else
    echo "       WARNING: patch-heliboard.sh not found — skipping CircleOne patches"
fi

###############################################################################
# Patch application identity
###############################################################################
echo "[3/7] Patching application ID and display name..."

# -- build.gradle / build.gradle.kts (applicationId ONLY -- do NOT rename source packages)
find "$BUILD_DIR/app" -maxdepth 1 -name "build.gradle*" | while read -r gf; do
    sed -i "s/applicationId\s*=\?\s*[\"']helium314\.keyboard[\"']/applicationId = \"${NEW_APP_ID}\"/" "$gf"
done

# -- donottranslate.xml (english_ime_name -- this is where HeliBoard stores it)
DONOTTRANSLATE="${BUILD_DIR}/app/src/main/res/values/donottranslate.xml"
if [[ -f "$DONOTTRANSLATE" ]]; then
    sed -i "s|>HeliBoard<|>${NEW_APP_NAME}<|g" "$DONOTTRANSLATE"
    echo "       Patched donottranslate.xml"
fi

# -- strings.xml (settings labels)
STRINGS_FILE="${BUILD_DIR}/app/src/main/res/values/strings.xml"
if [[ -f "$STRINGS_FILE" ]]; then
    sed -i "s/HeliBoard Spell Checker/${NEW_APP_NAME} Spell Checker/g" "$STRINGS_FILE"
    sed -i "s/HeliBoard Settings/${NEW_APP_NAME} Settings/g" "$STRINGS_FILE"
    echo "       Patched strings.xml"
fi

# -- Replace launcher icon with CircleOne icon
echo "       Replacing launcher icons..."
ICON_SRC="${WORK_DIR}/../android/store-listing/graphics/app-icon-1024.png"
if [[ -f "$ICON_SRC" ]] && command -v python &>/dev/null; then
    python -c "
from PIL import Image
import os, sys
src = Image.open(sys.argv[1])
base = sys.argv[2]
sizes = {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
for d, px in sizes.items():
    r = src.resize((px,px), Image.LANCZOS)
    for n in ['ic_launcher.png','ic_launcher_round.png']:
        r.save(os.path.join(base, f'mipmap-{d}', n))
# Remove adaptive icon XML overrides
av26 = os.path.join(base, 'mipmap-anydpi-v26')
for f in ['ic_launcher.xml','ic_launcher_round.xml']:
    p = os.path.join(av26, f)
    if os.path.exists(p): os.remove(p)
" "$ICON_SRC" "${BUILD_DIR}/app/src/main/res"
    echo "       Launcher icons replaced."
else
    echo "       WARNING: Could not replace icons (need Python + Pillow + app-icon-1024.png)"
fi

###############################################################################
# Copy CircleOne assets
###############################################################################
echo "[4/7] Copying CircleOne layout and assets..."

HELIBOARD_ASSETS="${BUILD_DIR}/app/src/main/assets"
mkdir -p "$HELIBOARD_ASSETS/layouts"

if [[ -f "$LAYOUT_SRC" ]]; then
    cp "$LAYOUT_SRC" "$HELIBOARD_ASSETS/layouts/one_layout.json"
    echo "       Copied one_layout.json"
fi

# Copy dictionaries if present
if [[ -d "$ASSETS_DIR/dictionaries" ]]; then
    cp -r "$ASSETS_DIR/dictionaries/"* "$HELIBOARD_ASSETS/" 2>/dev/null || true
    echo "       Copied dictionary files"
fi

# Copy font if present
if [[ -d "$ASSETS_DIR/fonts" ]]; then
    mkdir -p "$HELIBOARD_ASSETS/fonts"
    cp "$ASSETS_DIR/fonts/"* "$HELIBOARD_ASSETS/fonts/" 2>/dev/null || true
    echo "       Copied font files"
fi

###############################################################################
# Signing configuration (optional)
###############################################################################
echo "[5/7] Checking for signing configuration..."

KEYSTORE_PROPS="${WORK_DIR}/keystore.properties"
if [[ -f "$KEYSTORE_PROPS" ]]; then
    echo "       Found keystore.properties -- release will be signed."
    # Inject signing config into app/build.gradle.kts if not already present
    APP_GRADLE="${BUILD_DIR}/app/build.gradle.kts"
    if [[ -f "$APP_GRADLE" ]] && ! grep -q "circleoneRelease" "$APP_GRADLE"; then
        # Copy keystore.properties into build dir so Gradle can read it
        cp "$KEYSTORE_PROPS" "$BUILD_DIR/keystore.properties"
    fi
else
    echo "       No keystore.properties found -- building unsigned / debug."
    if [[ "$BUILD_TYPE" == "Release" ]]; then
        BUILD_TYPE="Debug"
        echo "       Falling back to Debug build (no signing key)."
    fi
fi

###############################################################################
# Build
###############################################################################
echo "[6/7] Building ${BUILD_TYPE} APK..."

cd "$BUILD_DIR"

if [[ "$BUILD_TYPE" == "Release" ]]; then
    ./gradlew assembleRelease --no-daemon
else
    ./gradlew assembleDebug --no-daemon
fi

###############################################################################
# Collect output
###############################################################################
echo "[7/7] Collecting APK..."

mkdir -p "$OUTPUT_DIR"

BUILD_TYPE_LOWER="$(echo "$BUILD_TYPE" | tr '[:upper:]' '[:lower:]')"
APK_SEARCH="${BUILD_DIR}/app/build/outputs/apk/${BUILD_TYPE_LOWER}"

APK_FILE="$(find "$APK_SEARCH" -name "*.apk" -type f 2>/dev/null | head -1)"

if [[ -n "$APK_FILE" ]]; then
    cp "$APK_FILE" "${OUTPUT_DIR}/circleone-${BUILD_TYPE_LOWER}.apk"
    echo ""
    echo "Build complete."
    echo "APK: ${OUTPUT_DIR}/circleone-${BUILD_TYPE_LOWER}.apk"
else
    echo "ERROR: Could not find built APK in ${APK_SEARCH}" >&2
    exit 1
fi
