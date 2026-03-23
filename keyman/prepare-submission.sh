#!/bin/bash
# CircleOne — Prepare files for Keyman keyboard repository submission
# Usage: bash keyman/prepare-submission.sh <path-to-keyboards-fork>
#
# Example:
#   git clone https://github.com/YOUR_USERNAME/keyboards.git
#   bash keyman/prepare-submission.sh ./keyboards

set -e

KEYBOARDS_REPO="${1:?Usage: $0 <path-to-keyboards-fork>}"
TARGET="$KEYBOARDS_REPO/release/c/circleone"
SOURCE="$TARGET/source"

echo "CircleOne — Preparing Keyman submission"
echo "Target: $TARGET"
echo ""

# Create submission directory structure
mkdir -p "$SOURCE"

# Source files (keyboard definition)
cp keyman/circleone.kmn "$SOURCE/"
cp keyman/circleone.kps "$SOURCE/"
cp keyman/circleone.keyman-touch-layout "$SOURCE/"

# Package files (root of keyboard folder)
cp font/one.ttf "$TARGET/" 2>/dev/null || echo "WARNING: font/one.ttf not found — font must be built first"
cp keyman/welcome.htm "$TARGET/"
cp keyman/readme.htm "$TARGET/"
cp keyman/HISTORY.md "$TARGET/"
cp keyman/LICENSE.md "$TARGET/"

echo ""
echo "Done. Files placed in: $TARGET"
echo ""
echo "Next steps:"
echo "  1. cd $TARGET"
echo "  2. Compile with Keyman Developer or ./build.sh"
echo "  3. Test the .kmp package"
echo "  4. Commit and open PR against keymanapp/keyboards"
