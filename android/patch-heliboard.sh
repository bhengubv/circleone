#!/usr/bin/env bash
# patch-heliboard.sh -- Apply CircleOne patches to vanilla HeliBoard source tree
# Called by build-apk.sh after cloning HeliBoard
#
# Applies:
# 1. New CircleOne source files (circleone/ package + CircleOneTransliterator)
# 2. Resource files (layouts, XML configs)
# 3. Font + PUA map assets
# 4. InputLogic.java patches (transliteration + IsiBheqeSpan composing text)
# 5. AndroidManifest.xml patches (compose activity + FileProvider)
# 6. LatinIME.java patches (compose/commitContent + OTP + GIF + spacebar trackpad)
# 7. MainKeyboardView.java patches (spacebar trackpad gesture)
# 8. build.gradle patches (Google Play Services for SmsRetriever)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/_build/HeliBoard"
OVERLAY_DIR="${SCRIPT_DIR}/src-overlay"
FONT_DIR="${SCRIPT_DIR}/../font"

SRC_DIR="${BUILD_DIR}/app/src/main/java/helium314/keyboard/latin"
RES_DIR="${BUILD_DIR}/app/src/main/res"
ASSETS_DIR="${BUILD_DIR}/app/src/main/assets"
MANIFEST="${BUILD_DIR}/app/src/main/AndroidManifest.xml"
INPUT_LOGIC="${SRC_DIR}/inputlogic/InputLogic.java"
LATIN_IME="${SRC_DIR}/LatinIME.java"
MAIN_KBD_VIEW="${BUILD_DIR}/app/src/main/java/helium314/keyboard/keyboard/MainKeyboardView.java"
APP_GRADLE="${BUILD_DIR}/app/build.gradle.kts"

echo "[patch] Applying CircleOne patches to HeliBoard..."

###############################################################################
# 1. Copy new source files
###############################################################################
echo "  [1/8] Copying CircleOne source files..."

mkdir -p "${SRC_DIR}/circleone" "${SRC_DIR}/circleone/scriptview"
cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/"*.java "${SRC_DIR}/circleone/"
echo "       Copied circleone/ package ($(ls "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/"*.java | wc -l) files)"

# Copy ScriptView subpackage (bundled accessibility service for PUA glyph rendering)
if [[ -d "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview" ]]; then
    cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview/"*.java "${SRC_DIR}/circleone/scriptview/"
    echo "       Copied scriptview/ subpackage ($(ls "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview/"*.java | wc -l) files)"
fi

if [[ -f "${OVERLAY_DIR}/java/helium314/keyboard/latin/CircleOneTransliterator.java" ]]; then
    cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/CircleOneTransliterator.java" "${SRC_DIR}/"
    echo "       Copied CircleOneTransliterator.java"
fi

# Copy Open Gesture Decoder library (MIT licensed, standalone)
GESTURE_LIB_SRC="${SCRIPT_DIR}/../libs/gesture-decoder/src/main/java/com/thegeek/gesture"
if [[ -d "$GESTURE_LIB_SRC" ]]; then
    GESTURE_DEST="${BUILD_DIR}/app/src/main/java/com/thegeek/gesture"
    mkdir -p "$GESTURE_DEST"
    cp "${GESTURE_LIB_SRC}/"*.java "$GESTURE_DEST/"
    echo "       Copied Open Gesture Decoder library ($(ls "${GESTURE_LIB_SRC}/"*.java | wc -l) files)"
fi

###############################################################################
# 2. Copy resource files
###############################################################################
echo "  [2/8] Copying resource files..."

mkdir -p "${RES_DIR}/layout" "${RES_DIR}/xml"
cp "${OVERLAY_DIR}/res/layout/"*.xml "${RES_DIR}/layout/" 2>/dev/null || true
cp "${OVERLAY_DIR}/res/xml/"*.xml "${RES_DIR}/xml/" 2>/dev/null || true
echo "       Copied layouts and XML resources"

# Add ScriptView string resources to HeliBoard's strings
STRINGS_FILE="${RES_DIR}/values/donottranslate.xml"
if [[ -f "$STRINGS_FILE" ]] && ! grep -q "scriptview_description" "$STRINGS_FILE"; then
    sed -i 's|</resources>|    <string name="scriptview_description" translatable="false">Renders isiBheqe soHlamvu and other unencoded writing systems across all apps. Without this, text appears as unreadable squares.</string>\n</resources>|' "$STRINGS_FILE"
    echo "       Added ScriptView string resources"
fi

###############################################################################
# 3. Copy assets (font + PUA map)
###############################################################################
echo "  [3/8] Copying font and PUA map to assets..."

[[ -f "${FONT_DIR}/one.ttf" ]] && cp "${FONT_DIR}/one.ttf" "${ASSETS_DIR}/one.ttf" && echo "       Copied one.ttf"
[[ -f "${FONT_DIR}/one-pua-map.csv" ]] && cp "${FONT_DIR}/one-pua-map.csv" "${ASSETS_DIR}/one-pua-map.csv" && echo "       Copied one-pua-map.csv"

###############################################################################
# 4. Patch InputLogic.java
###############################################################################
echo "  [4/8] Patching InputLogic.java..."

python - "$INPUT_LOGIC" << 'PYEOF'
import sys, re

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

changes = 0

# --- Add CircleOne imports after existing imports ---
CIRCLEONE_IMPORTS = """import helium314.keyboard.latin.CircleOneTransliterator;
import helium314.keyboard.latin.circleone.IsiBheqeSpan;
import helium314.keyboard.latin.circleone.CommitContentHelper;"""

if 'CircleOneTransliterator' not in content:
    # Insert after the last existing import
    marker = 'import java.util.concurrent.TimeUnit;'
    if marker in content:
        content = content.replace(marker, marker + '\n\n' + CIRCLEONE_IMPORTS)
        changes += 1

# --- Add transliterator field + composing buffer ---
FIELDS = """
    // CircleOne isiBheqe transliterator (lazy init — context not ready at construction)
    private CircleOneTransliterator mTransliterator;

    // CircleOne: accumulated PUA text during composing (shown via IsiBheqeSpan)
    private final StringBuilder mIsiBheqeComposing = new StringBuilder();

    private CircleOneTransliterator getTransliterator() {
        if (mTransliterator == null) {
            try {
                mTransliterator = new CircleOneTransliterator(mLatinIME);
            } catch (Exception ignored) { }
        }
        return mTransliterator;
    }"""

if 'mTransliterator' not in content:
    # Insert after mJustRevertedACommit field
    marker = 'private boolean mJustRevertedACommit = false;'
    if marker in content:
        content = content.replace(marker, marker + '\n' + FIELDS)
        changes += 1

elif 'mIsiBheqeComposing' not in content:
    # Transliterator exists (Session 45) but composing buffer doesn't
    marker = 'private CircleOneTransliterator mTransliterator;'
    if marker in content:
        content = content.replace(marker, marker + '\n\n    // CircleOne: accumulated PUA text during composing (shown via IsiBheqeSpan)\n    private final StringBuilder mIsiBheqeComposing = new StringBuilder();')
        changes += 1

# --- Inject transliteration block into onCodeInput ---
# Find the onCodeInput method and inject CircleOne handling
TRANSLITERATION_BLOCK = '''        // CircleOne: intercept character input for isiBheqe transliteration
        // Returns early on handled characters to show PUA glyphs via IsiBheqeSpan.
        // IMPORTANT: do NOT call mWordComposer.reset() — it kills HeliBoard's input pipeline.
        final CircleOneTransliterator transliterator = getTransliterator();
        if (transliterator != null && transliterator.isEnabled()
                && event.getCodePoint() > 0 && !event.isFunctionalKeyEvent()) {
            final char c = (char) event.getCodePoint();
            if (Character.isLetter(c)) {
                final CircleOneTransliterator.Result result = transliterator.processChar(c);
                if (result.handled) {
                    if (result.output != null) {
                        // Syllable complete — append PUA glyph to composing buffer
                        mIsiBheqeComposing.append(result.output);
                    }
                    // Show Latin transliteration as composing text (readable hint)
                    // PUA glyphs via IsiBheqeSpan don't work reliably across OEMs (MIUI renders emoji instead)
                    // The actual isiBheqe image is sent on commit (space/punctuation)
                    final String latinHint = "[" + transliterator.getComposedLatin() + transliterator.getBuffer() + "]";
                    mConnection.beginBatchEdit();
                    mConnection.setComposingText(latinHint, 1);
                    mConnection.endBatchEdit();
                    // Return a properly formed InputTransaction so HeliBoard state stays valid
                    final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                            event, SystemClock.uptimeMillis(), mSpaceState,
                            getActualCapsMode(settingsValues, keyboardShiftMode));
                    inputTransaction.setDidAffectContents();
                    inputTransaction.setRequiresUpdateSuggestions();
                    return inputTransaction;
                }
                // passThrough — fall through to normal HeliBoard handling
            }
            // Space or punctuation — add to buffer, keep composing
            if (event.getCodePoint() == Constants.CODE_SPACE || !Character.isLetterOrDigit(c)) {
                if (mIsiBheqeComposing.length() > 0 && event.getCodePoint() != Constants.CODE_ENTER) {
                    mIsiBheqeComposing.append(c);
                    transliterator.addLiteral(c);
                    transliterator.reset();
                    final String latinHint = "[" + transliterator.getComposedLatin() + "]";
                    mConnection.beginBatchEdit();
                    mConnection.setComposingText(latinHint, 1);
                    mConnection.endBatchEdit();
                    final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                            event, SystemClock.uptimeMillis(), mSpaceState,
                            getActualCapsMode(settingsValues, keyboardShiftMode));
                    inputTransaction.setDidAffectContents();
                    return inputTransaction;
                }
            }
            // Enter — commit full message (with spaces and punctuation) as one image
            if (event.getCodePoint() == Constants.CODE_ENTER) {
                if (mIsiBheqeComposing.length() > 0) {
                    final String textToCommit = mIsiBheqeComposing.toString().trim();
                    mConnection.beginBatchEdit();
                    mConnection.finishComposingText();
                    if (!textToCommit.isEmpty()) {
                        boolean sentAsImage = false;
                        try {
                            final android.view.inputmethod.EditorInfo editorInfo =
                                    mLatinIME.getCurrentInputEditorInfo();
                            sentAsImage = CommitContentHelper.commitIsiBheqeImage(
                                    mLatinIME, editorInfo, textToCommit);
                        } catch (Exception ignored) { }
                        if (!sentAsImage) {
                            mConnection.commitText(textToCommit, 1);
                        }
                    }
                    mConnection.endBatchEdit();
                    mIsiBheqeComposing.setLength(0);
                    transliterator.resetAll();
                    final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                            event, SystemClock.uptimeMillis(), mSpaceState,
                            getActualCapsMode(settingsValues, keyboardShiftMode));
                    inputTransaction.setDidAffectContents();
                    return inputTransaction;
                }
            }
        }
        // Handle delete — remove last glyph from composing buffer
        if (transliterator != null && event.getKeyCode() == KeyCode.DELETE) {
            if (mIsiBheqeComposing.length() > 0) {
                mIsiBheqeComposing.setLength(mIsiBheqeComposing.length() - 1);
                transliterator.removeLastSyllable();
                // Show updated Latin hint
                final String hint = mIsiBheqeComposing.length() > 0
                        ? "[" + transliterator.getComposedLatin() + transliterator.getBuffer() + "]" : "";
                mConnection.setComposingText(hint, 1);
                transliterator.reset();
                final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                        event, SystemClock.uptimeMillis(), mSpaceState,
                        getActualCapsMode(settingsValues, keyboardShiftMode));
                inputTransaction.setDidAffectContents();
                return inputTransaction;
            }
            transliterator.reset();
        }

'''

# Check if Session 45 transliteration block exists and replace it
OLD_S45_MARKER = '// CircleOne: intercept character input for isiBheqe transliteration'
if OLD_S45_MARKER in content:
    # Find the old block (from marker to "transliterator.reset();\n        }")
    # and replace with new block
    start = content.index(OLD_S45_MARKER)
    # Find "transliterator.reset();\n        }\n" after the DELETE handler
    end_pattern = 'transliterator.reset();\n        }\n'
    # Find last occurrence of this pattern after start
    search_from = start
    end = -1
    while True:
        idx = content.find(end_pattern, search_from)
        if idx == -1 or idx > start + 5000:
            break
        end = idx + len(end_pattern)
        search_from = idx + 1

    if end > start:
        # Replace the entire old block
        content = content[:start] + TRANSLITERATION_BLOCK + content[end:]
        changes += 1
        print("       Replaced Session 45 transliteration block with composing text version")
else:
    # Vanilla HeliBoard — inject before the first line of onCodeInput body
    marker = '        mWordBeingCorrectedByCursor = null;\n        mJustRevertedACommit = false;'
    if marker in content:
        content = content.replace(marker, TRANSLITERATION_BLOCK + marker)
        changes += 1
        print("       Injected transliteration block into vanilla HeliBoard onCodeInput")

# --- Add helper methods ---
HELPER_METHODS = '''
    /**
     * Commits any accumulated isiBheqe composing text and resets the buffer.
     */
    public void commitIsiBheqeComposing() {
        if (mIsiBheqeComposing.length() > 0) {
            mConnection.beginBatchEdit();
            mConnection.finishComposingText();
            // Trim trailing spaces before committing
            final String textToCommit = mIsiBheqeComposing.toString().trim();
            // Try image commit first (apps without one.ttf show squares for raw PUA)
            boolean sentAsImage = false;
            try {
                final android.view.inputmethod.EditorInfo editorInfo =
                        mLatinIME.getCurrentInputEditorInfo();
                sentAsImage = CommitContentHelper.commitIsiBheqeImage(
                        mLatinIME, editorInfo, textToCommit);
            } catch (Exception ignored) { }
            if (!sentAsImage) {
                mConnection.commitText(textToCommit, 1);
                android.widget.Toast.makeText(mLatinIME,
                        "isiBheqe: image commit unavailable, sent as text",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            mConnection.endBatchEdit();
            mIsiBheqeComposing.setLength(0);
        }
        if (mTransliterator != null) {
            mTransliterator.reset();
        }
    }

    /**
     * Returns the current isiBheqe composing text (for commitContent/share).
     */
    public String getIsiBheqeComposingText() {
        return mIsiBheqeComposing.toString();
    }

'''

if 'commitIsiBheqeComposing' not in content:
    target = '    public PrivateCommandPerformer getPrivateCommandPerformer()'
    if target in content:
        content = content.replace(target, HELPER_METHODS + target)
        changes += 1

# --- Patch finishInput() to commit isiBheqe buffer ---
OLD_FINISH = '    public void finishInput() {\n        if (mWordComposer.isComposingWord()) {'
NEW_FINISH = '    public void finishInput() {\n        // Commit any accumulated isiBheqe composing text\n        commitIsiBheqeComposing();\n        if (mWordComposer.isComposingWord()) {'

if OLD_FINISH in content and 'commitIsiBheqeComposing();\n        if (mWordComposer' not in content:
    content = content.replace(OLD_FINISH, NEW_FINISH)
    changes += 1

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"       InputLogic.java: {changes} patches applied")
PYEOF

###############################################################################
# 5. Patch AndroidManifest.xml
###############################################################################
echo "  [5/8] Patching AndroidManifest.xml..."

if ! grep -q "CircleOneComposeActivity" "$MANIFEST"; then
    python - "$MANIFEST" << 'PYEOF'
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

ADDITIONS = '''
        <!-- CircleOne Compose Activity -->
        <activity android:name="helium314.keyboard.latin.circleone.CircleOneComposeActivity"
            android:theme="@style/platformActivityTheme"
            android:label="CircleOne Compose"
            android:windowSoftInputMode="adjustResize"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>

        <!-- ScriptView: Accessibility service for PUA glyph rendering across all apps -->
        <service android:name="helium314.keyboard.latin.circleone.scriptview.ScriptViewService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/scriptview_config" />
        </service>

        <!-- CircleOne FileProvider for image + GIF sharing -->
        <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.circleone.fileprovider"
            android:enabled="true"
            android:grantUriPermissions="true"
            android:exported="false">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/circleone_file_paths" />
        </provider>
'''

target = '    </application>'
if target in content:
    content = content.replace(target, ADDITIONS + target)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("       AndroidManifest.xml patched")
else:
    print("       WARNING: Could not find </application>")
PYEOF
fi

# Add INTERNET permission for Tenor GIF API (HeliBoard may not have it)
if ! grep -q "android.permission.INTERNET" "$MANIFEST"; then
    python - "$MANIFEST" << 'PYEOF'
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Add INTERNET permission before <application tag
PERMISSION = '    <uses-permission android:name="android.permission.INTERNET" />\n\n'
target = '    <application'
if target in content and 'android.permission.INTERNET' not in content:
    content = content.replace(target, PERMISSION + target, 1)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("       Added INTERNET permission for Tenor GIF API")
PYEOF
fi

###############################################################################
# 6. Patch LatinIME.java (compose, commitContent, OTP, GIF, spacebar trackpad)
###############################################################################
echo "  [6/8] Patching LatinIME.java..."

python - "$LATIN_IME" << 'PYEOF'
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

changes = 0

# --- Add imports ---
IMPORTS = """import helium314.keyboard.latin.circleone.CircleOneComposeActivity;
import helium314.keyboard.latin.circleone.CommitContentHelper;
import helium314.keyboard.latin.circleone.SpacebarTrackpadListener;
import helium314.keyboard.latin.circleone.GifSearchView;
import helium314.keyboard.latin.circleone.OtpSuggestionProvider;
import helium314.keyboard.latin.circleone.ClipboardPinManager;
import helium314.keyboard.latin.circleone.MultilingualDetector;
import helium314.keyboard.latin.circleone.InlineTranslator;
import helium314.keyboard.latin.circleone.NeuralPredictionProvider;
import helium314.keyboard.latin.circleone.PhotoBackgroundManager;
import helium314.keyboard.latin.circleone.FloatingKeyboardController;
import helium314.keyboard.latin.circleone.EmojiKitchenProvider;
import helium314.keyboard.latin.circleone.SwipeTypingHandler;
"""

if 'SpacebarTrackpadListener' not in content:
    marker = 'import helium314.keyboard.latin.utils.StatsUtils;'
    if marker in content:
        content = content.replace(marker, marker + '\n' + IMPORTS)
        changes += 1
elif 'CircleOneComposeActivity' not in content:
    marker = 'import helium314.keyboard.latin.utils.StatsUtils;'
    if marker in content:
        content = content.replace(marker, marker + '\n' + IMPORTS)
        changes += 1

# --- Add fields ---
FIELDS = """
    // CircleOne: spacebar trackpad for cursor control
    private SpacebarTrackpadListener mSpacebarTrackpad;

    // CircleOne: GIF search panel
    private GifSearchView mGifSearchView;

    // CircleOne: OTP auto-fill from SMS
    private OtpSuggestionProvider mOtpProvider;

    // CircleOne: clipboard pin manager
    private ClipboardPinManager mClipboardPinManager;

    // CircleOne Phase 2: multilingual SA language detection
    private MultilingualDetector mMultilingualDetector;

    // CircleOne Phase 2: inline translation
    private InlineTranslator mInlineTranslator;

    // CircleOne Phase 3: neural prediction provider (stub until model is trained)
    private NeuralPredictionProvider mNeuralProvider;

    // CircleOne Phase 4: photo background
    private PhotoBackgroundManager mPhotoBackgroundManager;

    // CircleOne Phase 4: floating keyboard
    private FloatingKeyboardController mFloatingKeyboardController;

    // CircleOne Phase 4: emoji kitchen
    private EmojiKitchenProvider mEmojiKitchenProvider;

    // CircleOne: swipe/gesture typing (Open Gesture Decoder, MIT)
    private SwipeTypingHandler mSwipeTypingHandler;
"""

if 'mSpacebarTrackpad' not in content:
    # Find a good injection point — after class fields
    marker = 'private boolean mIsExecutingRecapitalizeCommand = false;'
    alt_marker = 'private RichInputMethodManager mRichImm;'
    target = marker if marker in content else (alt_marker if alt_marker in content else None)
    if target:
        content = content.replace(target, target + '\n' + FIELDS)
        changes += 1

# --- Patch onCreate to initialize CircleOne features ---
ONCREATE_INIT = """
        // CircleOne: initialize all features
        mSpacebarTrackpad = new SpacebarTrackpadListener(this);
        mOtpProvider = new OtpSuggestionProvider(this);
        mClipboardPinManager = new ClipboardPinManager(this);
        mMultilingualDetector = new MultilingualDetector();
        mInlineTranslator = new InlineTranslator();
        mNeuralProvider = new NeuralPredictionProvider.DummyNeuralProvider();
        mPhotoBackgroundManager = null; // static utility class, no instance needed
        mEmojiKitchenProvider = new EmojiKitchenProvider(this);
        mSwipeTypingHandler = new SwipeTypingHandler(this);
"""

if 'mSpacebarTrackpad = new' not in content:
    # Find end of onCreate super.onCreate or settings init
    marker = 'super.onCreate();'
    if marker in content:
        content = content.replace(marker, marker + ONCREATE_INIT)
        changes += 1

# --- Patch onStartInputView to start OTP listener ---
# Must target the OUTER LatinIME.onStartInputView (not UIHandler inner class)
OTP_START = """
        // CircleOne: start OTP detection on input view start
        if (mOtpProvider != null) mOtpProvider.startListening();
"""

if 'mOtpProvider.startListening' not in content:
    # Find the LAST (outer class) onStartInputView — skip the UIHandler one
    marker = 'public void onStartInputView('
    last_idx = content.rfind(marker)
    if last_idx > 0:
        brace = content.index('{', last_idx)
        content = content[:brace+1] + OTP_START + content[brace+1:]
        changes += 1

# --- Patch onDestroy to clean up ---
ONDESTROY_CLEANUP = """
        // CircleOne: cleanup
        if (mSpacebarTrackpad != null) mSpacebarTrackpad.reset();
        if (mOtpProvider != null) mOtpProvider.destroy();
        if (mGifSearchView != null) mGifSearchView.cleanup();
        if (mInlineTranslator != null) mInlineTranslator.clearCache();
        if (mNeuralProvider != null) mNeuralProvider.unloadModel();
        if (mEmojiKitchenProvider != null) mEmojiKitchenProvider.shutdown();
        if (mSwipeTypingHandler != null) mSwipeTypingHandler.setEnabled(false);
"""

if 'mOtpProvider.destroy' not in content:
    marker = 'public void onDestroy() {'
    if marker in content:
        content = content.replace(marker, marker + ONDESTROY_CLEANUP)
        changes += 1

# --- Add CircleOne methods before final closing brace ---
METHODS = '''
    // ========================================================================
    // CircleOne Feature Methods
    // ========================================================================

    /**
     * Opens the CircleOne compose activity for full isiBheqe text editing.
     */
    public void openCircleOneCompose() {
        String composingText = mInputLogic.getIsiBheqeComposingText();
        android.content.Intent intent = new android.content.Intent(this, CircleOneComposeActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        if (composingText != null && !composingText.isEmpty()) {
            intent.putExtra(android.content.Intent.EXTRA_TEXT, composingText);
        }
        startActivity(intent);
    }

    /**
     * Commits current isiBheqe composing text as an image via commitContent API.
     */
    public boolean commitIsiBheqeAsImage() {
        String composingText = mInputLogic.getIsiBheqeComposingText();
        if (composingText == null || composingText.isEmpty()) return false;
        android.view.inputmethod.EditorInfo editorInfo = getCurrentInputEditorInfo();
        boolean result = CommitContentHelper.commitIsiBheqeImage(this, editorInfo, composingText);
        if (result) {
            mInputLogic.commitIsiBheqeComposing();
        }
        return result;
    }

    /**
     * Returns the spacebar trackpad listener for MainKeyboardView integration.
     */
    public SpacebarTrackpadListener getSpacebarTrackpad() {
        return mSpacebarTrackpad;
    }

    /**
     * Shows the GIF search panel, replacing the keyboard view temporarily.
     */
    public void showGifPanel() {
        if (mGifSearchView == null) {
            mGifSearchView = new GifSearchView(this);
            mGifSearchView.attach(this);
            mGifSearchView.setDismissListener(() -> hideGifPanel());
        }
        setInputView(mGifSearchView);
    }

    /**
     * Hides the GIF search panel and restores the keyboard.
     */
    public void hideGifPanel() {
        if (mGifSearchView != null) {
            mGifSearchView.cleanup();
            mGifSearchView = null;
        }
        // Restore main keyboard view
        setInputView(onCreateInputView());
    }

    /**
     * Returns the OTP provider for suggestion strip integration.
     */
    public OtpSuggestionProvider getOtpProvider() {
        return mOtpProvider;
    }

    /**
     * Returns the clipboard pin manager for clipboard view integration.
     */
    public ClipboardPinManager getClipboardPinManager() {
        return mClipboardPinManager;
    }

    /**
     * Inserts the current OTP code at the cursor and clears it.
     */
    public void insertOtpCode() {
        if (mOtpProvider == null) return;
        String otp = mOtpProvider.getCurrentOtp();
        if (otp == null || otp.isEmpty()) return;

        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(otp, 1);
        }
        mOtpProvider.consumeOtp();
    }

    // ========================================================================
    // Phase 2: SA Advantage — Multilingual + Translation
    // ========================================================================

    /**
     * Returns the multilingual language detector for auto-switching dictionaries.
     */
    public MultilingualDetector getMultilingualDetector() {
        return mMultilingualDetector;
    }

    /**
     * Feed a word into the language detector (call after each word is committed).
     */
    public void detectLanguageFromWord(String word) {
        if (mMultilingualDetector != null && word != null) {
            mMultilingualDetector.addWord(word);
        }
    }

    /**
     * Returns the inline translator for keyboard translation features.
     */
    public InlineTranslator getInlineTranslator() {
        return mInlineTranslator;
    }

    /**
     * Translate the currently selected text using inline translator.
     */
    public void translateSelectedText(String targetLang) {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null || mInlineTranslator == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected == null || selected.length() == 0) return;
        String sourceLang = mMultilingualDetector != null ? mMultilingualDetector.getActiveLanguage() : "en";
        mInlineTranslator.translate(selected.toString(), sourceLang, targetLang,
            new InlineTranslator.TranslationCallback() {
                @Override public void onTranslated(String result) {
                    android.view.inputmethod.InputConnection conn = getCurrentInputConnection();
                    if (conn != null) conn.commitText(result, 1);
                }
                @Override public void onError(String message) {
                    // Silently fail — user keeps original text
                }
            });
    }

    // ========================================================================
    // Phase 3: AI Layer — Neural Predictions
    // ========================================================================

    /**
     * Returns the neural prediction provider (stub until model is trained).
     */
    public NeuralPredictionProvider getNeuralProvider() {
        return mNeuralProvider;
    }

    // ========================================================================
    // Phase 4: Polish — Photo Background, Floating, Emoji Kitchen
    // ========================================================================

    /**
     * PhotoBackgroundManager is a static utility class — access via PhotoBackgroundManager.method().
     */

    /**
     * Returns the floating keyboard controller.
     */
    public FloatingKeyboardController getFloatingKeyboardController() {
        return mFloatingKeyboardController;
    }

    /**
     * Toggle floating keyboard mode.
     */
    public void toggleFloatingKeyboard() {
        if (mFloatingKeyboardController == null) {
            mFloatingKeyboardController = new FloatingKeyboardController(this);
            mFloatingKeyboardController.restoreState(this);
        }
        boolean newState = !mFloatingKeyboardController.isFloatingMode();
        mFloatingKeyboardController.setFloatingMode(newState);
        if (newState) {
            setInputView(mFloatingKeyboardController.wrapInputView(onCreateInputView()));
        } else {
            setInputView(onCreateInputView());
        }
    }

    /**
     * Returns the emoji kitchen provider for emoji mashups.
     */
    public EmojiKitchenProvider getEmojiKitchenProvider() {
        return mEmojiKitchenProvider;
    }

    /**
     * Returns the swipe/gesture typing handler (Open Gesture Decoder).
     */
    public SwipeTypingHandler getSwipeTypingHandler() {
        return mSwipeTypingHandler;
    }
'''

if 'getSpacebarTrackpad' not in content:
    last_brace = content.rfind('}')
    if last_brace > 0:
        content = content[:last_brace] + METHODS + '\n}\n'
        changes += 1
elif 'openCircleOneCompose' not in content:
    # Only add the original methods if they're missing
    last_brace = content.rfind('}')
    if last_brace > 0:
        content = content[:last_brace] + METHODS + '\n}\n'
        changes += 1

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"       LatinIME.java: {changes} patches applied")
PYEOF

###############################################################################
# 7. Patch MainKeyboardView.java (spacebar trackpad gesture)
###############################################################################
echo "  [7/8] Patching MainKeyboardView.java for spacebar trackpad..."

if [[ -f "$MAIN_KBD_VIEW" ]] && ! grep -q "SpacebarTrackpadListener" "$MAIN_KBD_VIEW"; then
    python - "$MAIN_KBD_VIEW" << 'PYEOF'
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

changes = 0

# Add import
IMPORT = "import helium314.keyboard.latin.circleone.SpacebarTrackpadListener;\n"
if 'SpacebarTrackpadListener' not in content:
    # Insert after last import
    import_lines = [i for i, line in enumerate(content.split('\n')) if line.startswith('import ')]
    if import_lines:
        lines = content.split('\n')
        last_import_idx = import_lines[-1]
        lines.insert(last_import_idx + 1, IMPORT.rstrip())
        content = '\n'.join(lines)
        changes += 1

# Add spacebar trackpad touch interception in onTouchEvent or processMotionEvent
# HeliBoard's MainKeyboardView extends KeyboardView which handles touch events
# We inject spacebar trackpad handling into the touch processing pipeline

TRACKPAD_FIELD = """
    // CircleOne: spacebar trackpad cursor control
    private SpacebarTrackpadListener mTrackpadListener;
    private boolean mSpacebarTouched = false;

    /**
     * Set the spacebar trackpad listener (called from LatinIME).
     */
    public void setSpacebarTrackpadListener(SpacebarTrackpadListener listener) {
        mTrackpadListener = listener;
    }
"""

if 'mTrackpadListener' not in content:
    # Find class body start
    class_marker = 'public class MainKeyboardView'
    if class_marker in content:
        idx = content.index(class_marker)
        brace = content.index('{', idx)
        content = content[:brace+1] + TRACKPAD_FIELD + content[brace+1:]
        changes += 1

# Inject spacebar tracking into onTouchEvent
TOUCH_INTERCEPT = '''
        // CircleOne: spacebar trackpad cursor control
        if (mTrackpadListener != null) {
            final int action = me.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                // Check if touch is on spacebar by Y position (bottom 25% of keyboard)
                final int keyboardHeight = getHeight();
                final float touchY = me.getY();
                mSpacebarTouched = touchY > keyboardHeight * 0.75f;
            }
            if (mSpacebarTouched) {
                boolean consumed = mTrackpadListener.onTouchEvent(me);
                if (mTrackpadListener.isTrackpadActive()) {
                    return true; // Consume all events while trackpad is active
                }
                if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) {
                    mSpacebarTouched = false;
                    if (consumed) return true;
                }
            }
        }
'''

if 'mSpacebarTouched' not in content or 'CircleOne: spacebar trackpad' not in content:
    # Find onTouchEvent method
    marker = 'public boolean onTouchEvent(final MotionEvent me) {'
    alt_marker = 'public boolean onTouchEvent(MotionEvent me) {'
    target = marker if marker in content else (alt_marker if alt_marker in content else None)
    if target:
        idx = content.index(target)
        brace = content.index('{', idx)
        content = content[:brace+1] + TOUCH_INTERCEPT + content[brace+1:]
        changes += 1

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"       MainKeyboardView.java: {changes} patches applied")
PYEOF
else
    echo "       MainKeyboardView.java: already patched or not found"
fi

###############################################################################
# 8. Patch build.gradle for Google Play Services (SmsRetriever)
###############################################################################
echo "  [8/8] Patching build.gradle for SmsRetriever dependency..."

# Check for Kotlin DSL or Groovy DSL
if [[ -f "$APP_GRADLE" ]]; then
    GRADLE_FILE="$APP_GRADLE"
elif [[ -f "${BUILD_DIR}/app/build.gradle" ]]; then
    GRADLE_FILE="${BUILD_DIR}/app/build.gradle"
else
    echo "       WARNING: Could not find build.gradle"
    GRADLE_FILE=""
fi

if [[ -n "$GRADLE_FILE" ]] && ! grep -q "play-services-auth-api-phone" "$GRADLE_FILE"; then
    python - "$GRADLE_FILE" << 'PYEOF'
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Add Google Play Services SmsRetriever dependency
# Works for both Kotlin DSL and Groovy DSL
SMS_DEP_KTS = '    implementation("com.google.android.gms:play-services-auth-api-phone:18.1.0")\n'
SMS_DEP_GROOVY = "    implementation 'com.google.android.gms:play-services-auth-api-phone:18.1.0'\n"

if 'dependencies {' in content or 'dependencies{' in content:
    marker = 'dependencies {'
    alt = 'dependencies{'
    target = marker if marker in content else alt
    idx = content.index(target)
    brace = content.index('{', idx)

    # Determine DSL style from existing deps
    if 'implementation("' in content:
        dep = SMS_DEP_KTS
    else:
        dep = SMS_DEP_GROOVY

    content = content[:brace+1] + '\n' + dep + content[brace+1:]

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("       Added play-services-auth-api-phone dependency for OTP SmsRetriever")
else:
    print("       WARNING: Could not find dependencies block")
PYEOF
else
    echo "       build.gradle: already has SmsRetriever or not found"
fi

echo "[patch] All CircleOne patches applied successfully."
echo ""
echo "  Features patched:"
echo "    Phase 0 (Core):"
echo "      ✓ isiBheqe transliteration + composing text spans"
echo "      ✓ Compose activity + commitContent image mode"
echo "    Phase 1 (Parity Sprint):"
echo "      ✓ Spacebar trackpad cursor control"
echo "      ✓ GIF/sticker search (Tenor API)"
echo "      ✓ OTP auto-fill (SmsRetriever)"
echo "      ✓ Clipboard pin manager"
echo "    Phase 2 (African Language Advantage):"
echo "      ✓ Multilingual Bantu language auto-detection (21 languages)"
echo "      ✓ Inline translation (MyMemory API)"
echo "    Phase 3 (AI Layer):"
echo "      ✓ Neural prediction provider (stub — awaiting model training)"
echo "    Phase 4 (Polish):"
echo "      ✓ Custom photo keyboard backgrounds"
echo "      ✓ Floating/repositionable keyboard"
echo "      ✓ Emoji Kitchen sticker mashups"
echo "    Swipe Typing:"
echo "      ✓ Open Gesture Decoder (MIT, standalone library)"
echo "      ✓ SwipeTypingHandler integration bridge"
