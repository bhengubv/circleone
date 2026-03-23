#!/usr/bin/env bash
# Generate the Xcode project from project.yml using XcodeGen.
# Run this once after cloning on a Mac.
#
# Prerequisites:
#   brew install xcodegen
#
# Usage:
#   cd ios/CircleOne
#   ./generate-xcodeproj.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check for XcodeGen
if ! command -v xcodegen &>/dev/null; then
    echo "XcodeGen not found. Installing via Homebrew..."
    brew install xcodegen
fi

# Copy font and CSV into the project for bundling
FONT_DIR="../../font"
if [[ -f "$FONT_DIR/one.ttf" ]]; then
    echo "Font one.ttf found."
else
    echo "WARNING: font/one.ttf not found. Build the font first:"
    echo "  cd font && fontforge -script generate_glyphs.py"
fi

# Generate the Xcode project
echo "Generating Xcode project..."
xcodegen generate

echo ""
echo "Done. Open CircleOne.xcodeproj in Xcode."
echo ""
echo "Before building:"
echo "  1. Set your DEVELOPMENT_TEAM in project.yml (or Xcode signing settings)"
echo "  2. Select a real device or simulator"
echo "  3. Build and run CircleOneApp scheme"
echo ""
echo "To test the keyboard:"
echo "  1. Run the app on a device"
echo "  2. Go to Settings → General → Keyboard → Keyboards → Add New Keyboard"
echo "  3. Select CircleOne"
echo "  4. Use the globe key to switch to CircleOne in any text field"
