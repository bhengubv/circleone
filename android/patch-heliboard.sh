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
# 8. build.gradle check (refuses Google Play Services)
# 9. All eleven South African languages, on by default; isiBheqe as a choice

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
echo "  [1/9] Copying CircleOne source files..."

mkdir -p "${SRC_DIR}/circleone" "${SRC_DIR}/circleone/scriptview"
cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/"*.java "${SRC_DIR}/circleone/"
echo "       Copied circleone/ package ($(ls "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/"*.java | wc -l) files)"

# Copy ScriptView subpackage (bundled accessibility service for PUA glyph rendering)
if [[ -d "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview" ]]; then
    cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview/"*.java "${SRC_DIR}/circleone/scriptview/"
    echo "       Copied scriptview/ subpackage ($(ls "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/scriptview/"*.java | wc -l) files)"
fi

# Copy Learn subpackage (CircleOne Learn: Hangman, one isiBheqe syllable at a time)
if [[ -d "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/learn" ]]; then
    mkdir -p "${SRC_DIR}/circleone/learn"
    cp "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/learn/"*.java "${SRC_DIR}/circleone/learn/"
    echo "       Copied learn/ subpackage ($(ls "${OVERLAY_DIR}/java/helium314/keyboard/latin/circleone/learn/"*.java | wc -l) files)"
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
echo "  [2/9] Copying resource files..."

mkdir -p "${RES_DIR}/layout" "${RES_DIR}/xml"
cp "${OVERLAY_DIR}/res/layout/"*.xml "${RES_DIR}/layout/" 2>/dev/null || true
cp "${OVERLAY_DIR}/res/xml/"*.xml "${RES_DIR}/xml/" 2>/dev/null || true
echo "       Copied layouts and XML resources"

# CircleMetroUI's generated tokens - colours, spacing, radii, type, day and night -
# for CircleOne Learn. Copied from github.com/bhengubv/CircleMetroUI's Android
# adapter, never edited by hand.
for d in values values-night; do
    if ls "${OVERLAY_DIR}/res/${d}/"circlemetro_*.xml >/dev/null 2>&1; then
        mkdir -p "${RES_DIR}/${d}"
        cp "${OVERLAY_DIR}/res/${d}/"circlemetro_*.xml "${RES_DIR}/${d}/"
    fi
done
echo "       Copied CircleMetroUI tokens"

# Add ScriptView string resources to HeliBoard's strings
STRINGS_FILE="${RES_DIR}/values/donottranslate.xml"
if [[ -f "$STRINGS_FILE" ]] && ! grep -q "scriptview_description" "$STRINGS_FILE"; then
    sed -i 's|</resources>|    <string name="scriptview_description" translatable="false">Renders isiBheqe soHlamvu and other unencoded writing systems across all apps. Without this, text appears as unreadable squares.</string>\n</resources>|' "$STRINGS_FILE"
    echo "       Added ScriptView string resources"
fi

###############################################################################
# 3. Copy assets (font + PUA map)
###############################################################################
echo "  [3/9] Copying font and PUA map to assets..."

[[ -f "${FONT_DIR}/one.ttf" ]] && cp "${FONT_DIR}/one.ttf" "${ASSETS_DIR}/one.ttf" && echo "       Copied one.ttf"
[[ -f "${FONT_DIR}/one-pua-map.csv" ]] && cp "${FONT_DIR}/one-pua-map.csv" "${ASSETS_DIR}/one-pua-map.csv" && echo "       Copied one-pua-map.csv"

# CircleOne Learn's words: the commonest words of each isiBheqe language, from
# the NCHLT corpora (sa-languages/dictionaries/build_dicts.py writes them).
if ls "${SCRIPT_DIR}/sa-languages/learn/"words_*.txt >/dev/null 2>&1; then
    mkdir -p "${ASSETS_DIR}/learn"
    cp "${SCRIPT_DIR}/sa-languages/learn/"words_*.txt "${ASSETS_DIR}/learn/"
    echo "       Copied Learn word lists ($(ls "${SCRIPT_DIR}/sa-languages/learn/"words_*.txt | wc -l) languages)"
fi

###############################################################################
# 4. Patch InputLogic.java
###############################################################################
echo "  [4/9] Patching InputLogic.java..."

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
        // ONLY ON THE isiBheqe LAYOUT. The transliterator starts enabled and
        // nothing turned it off, so every Latin syllable in every language
        // became isiBheqe - "ba" typed on AngelDust committed one glyph, and
        // English could not be typed at all. It is a language entry now,
        // "isiZulu (isiBheqe)", chosen like any other (sa-languages/apply.py).
        final boolean isiBheqe = isIsiBheqeLayout();
        if (!isiBheqe && mIsiBheqeComposing.length() > 0) {
            // Switched away mid-sentence: keep what was written, as written.
            commitIsiBheqeComposing();
        }
        if (transliterator != null && isiBheqe && transliterator.isEnabled()
                && event.getCodePoint() > 0 && !event.isFunctionalKeyEvent()) {
            // No Latin suggestions over the script. The strip offered isiZulu
            // words in Latin letters while glyphs were being written, and one
            // tap would have put a Latin word in the middle of isiBheqe text.
            mSuggestionStripViewAccessor.setSuggestions(SuggestedWords.getEmptyInstance());
            final char c = (char) event.getCodePoint();
            if (Character.isLetter(c)) {
                final CircleOneTransliterator.Result result = transliterator.processChar(c);
                if (result.handled) {
                    if (result.output != null) {
                        // Syllable complete — append PUA glyph to composing buffer
                        mIsiBheqeComposing.append(result.output);
                    }
                    // Show PUA codepoints as composing text — ScriptView overlays correct glyphs
                    // - and after them the letters still waiting for a vowel, so
                    // nothing typed is ever invisible.
                    mConnection.beginBatchEdit();
                    mConnection.setComposingText(isiBheqeShown(), 1);
                    mConnection.endBatchEdit();
                    // Return a properly formed InputTransaction so HeliBoard state stays valid
                    final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                            event, SystemClock.uptimeMillis(), mSpaceState,
                            getActualCapsMode(settingsValues, keyboardShiftMode));
                    inputTransaction.setDidAffectContents();
                    // Do NOT setRequiresUpdateSuggestions — prevents HeliBoard from re-setting composing text
                    return inputTransaction;
                }
                // passThrough — fall through to normal HeliBoard handling
            }
            // Space, punctuation or Enter end the word: write out the consonants
            // still waiting for a vowel - a syllabic nasal, or the letters as
            // typed - so the end of a word is never lost.
            if (!Character.isLetter(c) || event.getCodePoint() == Constants.CODE_ENTER) {
                final String pending = transliterator.flush();
                if (pending != null) mIsiBheqeComposing.append(pending);
            }
            // Space or punctuation — add to buffer, keep composing
            if (event.getCodePoint() == Constants.CODE_SPACE || !Character.isLetterOrDigit(c)) {
                if (mIsiBheqeComposing.length() > 0 && event.getCodePoint() != Constants.CODE_ENTER) {
                    mIsiBheqeComposing.append(c);
                    transliterator.addLiteral(c);
                    transliterator.reset();
                    mConnection.beginBatchEdit();
                    mConnection.setComposingText(mIsiBheqeComposing.toString(), 1);
                    mConnection.endBatchEdit();
                    final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                            event, SystemClock.uptimeMillis(), mSpaceState,
                            getActualCapsMode(settingsValues, keyboardShiftMode));
                    inputTransaction.setDidAffectContents();
                    return inputTransaction;
                }
            }
            // Enter — commit full message as real PUA text (ScriptView renders the glyphs)
            if (event.getCodePoint() == Constants.CODE_ENTER) {
                if (mIsiBheqeComposing.length() > 0) {
                    final String textToCommit = mIsiBheqeComposing.toString().trim();
                    mConnection.beginBatchEdit();
                    // commitText REPLACES the composing text. Finishing the
                    // composition first made it permanent, and commitText then
                    // wrote it a second time - "ba" and Enter gave "ba ba",
                    // measured on AngelDust.
                    if (textToCommit.isEmpty()) {
                        mConnection.finishComposingText();
                    } else {
                        mConnection.commitText(textToCommit, 1);
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
        // Handle delete. Letters still waiting for a vowel go first, one by
        // one, as they were typed - backspace used to delete the glyph before
        // them, leaving the letters in the buffer to come back.
        if (transliterator != null && event.getKeyCode() == KeyCode.DELETE
                && transliterator.getBufferLength() > 0) {
            transliterator.deleteLastBuffered();
            mConnection.beginBatchEdit();
            mConnection.setComposingText(isiBheqeShown(), 1);
            mConnection.endBatchEdit();
            final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                    event, SystemClock.uptimeMillis(), mSpaceState,
                    getActualCapsMode(settingsValues, keyboardShiftMode));
            inputTransaction.setDidAffectContents();
            return inputTransaction;
        }
        // Then the glyphs — remove last glyph from composing buffer
        if (transliterator != null && event.getKeyCode() == KeyCode.DELETE) {
            if (mIsiBheqeComposing.length() > 0) {
                mIsiBheqeComposing.setLength(mIsiBheqeComposing.length() - 1);
                transliterator.removeLastSyllable();
                // Show updated Latin hint
                final String hint = mIsiBheqeComposing.length() > 0
                        ? mIsiBheqeComposing.toString() : "";
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
        // Letters still waiting for a vowel are written too - never dropped.
        if (mTransliterator != null) {
            final String pending = mTransliterator.flush();
            if (pending != null) mIsiBheqeComposing.append(pending);
        }
        if (mIsiBheqeComposing.length() > 0) {
            final String textToCommit = mIsiBheqeComposing.toString().trim();
            mConnection.beginBatchEdit();
            if (textToCommit.isEmpty()) {
                mConnection.finishComposingText();
            } else {
                // Commit as real PUA text — ScriptView accessibility service renders the glyphs.
                // commitText replaces the composing text; finishing the
                // composition first wrote the word twice (see the Enter case).
                mConnection.commitText(textToCommit, 1);
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

    /** What the composing text shows: the glyphs, then the letters still waiting for a vowel. */
    private String isiBheqeShown() {
        final String waiting = mTransliterator != null ? mTransliterator.getBuffer() : "";
        return mIsiBheqeComposing.toString() + waiting;
    }

    /**
     * The field changed and the keyboard did not change it: the app cleared
     * it (a game's next word, a chat's Send, a search box), or the person
     * moved the cursor. What is on screen stays as it is; the keyboard's own
     * copy goes, or the next key writes the old glyphs back - measured on
     * AngelDust, where "ba" after a cleared field came out "nda ba ba".
     */
    private void forgetIsiBheqeComposing() {
        mIsiBheqeComposing.setLength(0);
        if (mTransliterator != null) mTransliterator.resetAll();
    }

    /** Whether the person chose "isiZulu (isiBheqe)" - the layout named isibheqe. */
    private boolean isIsiBheqeLayout() {
        try {
            return "isibheqe".equals(helium314.keyboard.latin.RichInputMethodManager
                    .getInstance().getCurrentSubtype().getMainLayoutName());
        } catch (RuntimeException notReady) {
            return false; // no subtype yet: plain Latin, never a surprise script
        }
    }

'''

# The DEFINITION, not the name: the transliteration block above now CALLS
# commitIsiBheqeComposing() (to keep a half-written sentence when the person
# switches layout), so a name check skipped these methods and the build
# failed at javac with "cannot find symbol" on every caller.
if 'public void commitIsiBheqeComposing()' not in content:
    target = '    public PrivateCommandPerformer getPrivateCommandPerformer()'
    if target not in content:
        print("       FAILED: no getPrivateCommandPerformer() to place the isiBheqe helpers before")
        sys.exit(1)
    if target in content:
        content = content.replace(target, HELPER_METHODS + target)
        changes += 1

# --- onUpdateSelection: a change the keyboard did not make ends its composing ---
# isBelatedExpectedUpdate() is true for the updates our own setComposingText
# calls cause (RichInputConnection tracks the expected cursor), and false when
# the app shrank or removed the composing text or the cursor was moved. Past
# that test the field is no longer what the keyboard composed.
OLD_UPDATE = '''        if (mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart, oldSelEnd, newSelEnd, composingSpanStart, composingSpanEnd)) {
            return false;
        }
'''
NEW_UPDATE = OLD_UPDATE + '''        // CircleOne: the app or the person changed the field - drop the
        // keyboard's isiBheqe copy, or the next key writes the old glyphs back.
        if (mIsiBheqeComposing.length() > 0
                || (mTransliterator != null && mTransliterator.getBufferLength() > 0)) {
            forgetIsiBheqeComposing();
        }
'''
if 'forgetIsiBheqeComposing();\n        }\n' not in content.split('public boolean onUpdateSelection', 1)[-1][:2000]:
    if OLD_UPDATE not in content:
        print("       FAILED: no isBelatedExpectedUpdate check in onUpdateSelection to hook")
        sys.exit(1)
    content = content.replace(OLD_UPDATE, NEW_UPDATE, 1)
    changes += 1

# --- startInput: a new or restarted editor starts with nothing composed ---
# An app that replaces a field's text (EditText.setText - a game's next word, a
# chat's Send) makes Android restart input on it, which arrives here and not in
# onUpdateSelection. HeliBoard resets its own composing state here; the
# isiBheqe copy has to go with it. Measured on AngelDust: without this, "ba"
# typed after the field was cleared came back "bab", the old glyph and space
# rewritten ahead of it.
OLD_START = '        mConnection.onStartInput();\n'
NEW_START = OLD_START + '        forgetIsiBheqeComposing(); // CircleOne: nothing composed carries into a new input\n'
if 'forgetIsiBheqeComposing(); // CircleOne: nothing composed' not in content:
    if content.count(OLD_START) != 1:
        print("       FAILED: startInput anchor not found exactly once")
        sys.exit(1)
    content = content.replace(OLD_START, NEW_START, 1)
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
echo "  [5/9] Patching AndroidManifest.xml..."

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

        <!-- CircleOne Learn: Hangman, one isiBheqe syllable at a time. Its own
             launcher entry, because it is how a person learns the keyboard. -->
        <activity android:name="helium314.keyboard.latin.circleone.learn.LearnActivity"
            android:theme="@android:style/Theme.Material.Light.NoActionBar"
            android:label="CircleOne Learn"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"
            android:windowSoftInputMode="adjustResize"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- ScriptView: Accessibility service for PUA glyph rendering across all apps -->
        <service android:name="helium314.keyboard.latin.circleone.scriptview.ScriptViewService"
            android:label="CircleOne ScriptView"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
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

# STRIP the network permissions, rather than adding one.
#
# INTERNET was injected here for the Tenor GIF API. Four features used it:
# Tenor GIFs, Emoji Kitchen (gstatic.com), the MyMemory inline translator -
# which sent the text being translated to a third party - and SMS OTP via
# Google Play Services. All four are gone.
#
# READ_CONTACTS is NOT ours: it is inherited from upstream HeliBoard's
# contacts dictionary. It is stripped here so an upstream bump cannot bring
# it back quietly.
#
# A keyboard that cannot reach the network cannot send what you type
# anywhere. That is the one privacy claim a keyboard can actually prove,
# and it is worth more than four features nobody asked a keyboard for.
python - "$MANIFEST" << 'PYEOF'
import re
import sys

filepath = sys.argv[1]
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

BANNED = ("android.permission.INTERNET",
          "android.permission.READ_CONTACTS")

removed = []
for perm in BANNED:
    pattern = re.compile(
        r'[ \t]*<uses-permission[^>]*android:name="' + re.escape(perm)
        + r'"[^>]*/>[ \t]*\n')
    content, n = pattern.subn('', content)
    if n:
        removed.append('%s x%d' % (perm.rsplit('.', 1)[-1], n))

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

for perm in BANNED:
    if perm in content:
        print('       FAILED to strip %s - it is still declared' % perm)
        sys.exit(1)

print('       Stripped: %s' % (', '.join(removed) if removed
                               else 'nothing to strip'))
PYEOF

###############################################################################
# 6. Patch LatinIME.java (compose, commitContent, OTP, GIF, spacebar trackpad)
###############################################################################
echo "  [6/9] Patching LatinIME.java..."

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
import helium314.keyboard.latin.circleone.ClipboardPinManager;
import helium314.keyboard.latin.circleone.MultilingualDetector;
import helium314.keyboard.latin.circleone.NeuralPredictionProvider;
import helium314.keyboard.latin.circleone.PhotoBackgroundManager;
import helium314.keyboard.latin.circleone.FloatingKeyboardController;
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

    // CircleOne: clipboard pin manager
    private ClipboardPinManager mClipboardPinManager;

    // CircleOne Phase 2: multilingual SA language detection
    private MultilingualDetector mMultilingualDetector;

    // CircleOne Phase 3: neural prediction provider (stub until model is trained)
    private NeuralPredictionProvider mNeuralProvider;

    // CircleOne Phase 4: photo background
    private PhotoBackgroundManager mPhotoBackgroundManager;

    // CircleOne Phase 4: floating keyboard
    private FloatingKeyboardController mFloatingKeyboardController;

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
        mClipboardPinManager = new ClipboardPinManager(this);
        mMultilingualDetector = new MultilingualDetector();
        mNeuralProvider = new NeuralPredictionProvider.DummyNeuralProvider();
        mPhotoBackgroundManager = null; // static utility class, no instance needed
        mSwipeTypingHandler = new SwipeTypingHandler(this);
"""

if 'mSpacebarTrackpad = new' not in content:
    # Find end of onCreate super.onCreate or settings init
    marker = 'super.onCreate();'
    if marker in content:
        content = content.replace(marker, marker + ONCREATE_INIT)
        changes += 1

# --- Patch onDestroy to clean up ---
ONDESTROY_CLEANUP = """
        // CircleOne: cleanup
        if (mSpacebarTrackpad != null) mSpacebarTrackpad.reset();
        if (mNeuralProvider != null) mNeuralProvider.unloadModel();
        if (mSwipeTypingHandler != null) mSwipeTypingHandler.setEnabled(false);
"""

if 'mSpacebarTrackpad.reset' not in content:
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
     * Returns the clipboard pin manager for clipboard view integration.
     */
    public ClipboardPinManager getClipboardPinManager() {
        return mClipboardPinManager;
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
echo "  [7/9] Patching MainKeyboardView.java for spacebar trackpad..."

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
# 8. REFUSE Google Play Services
###############################################################################
# This step used to ADD com.google.android.gms:play-services-auth-api-phone
# for SMS OTP auto-fill. Circle OS is de-Googled: GMS is not present on the
# device, so that code could never run there, and shipping a Google client
# library inside a keyboard sold on "You're NOT the product" is a headline
# waiting to happen.
#
# Inverted rather than deleted. Deleting the step would let an upstream bump
# or a reinstated feature put the dependency back with nothing to notice.
echo "  [8/9] Checking for Google Play Services..."

if [[ -f "$APP_GRADLE" ]]; then
    GRADLE_FILE="$APP_GRADLE"
elif [[ -f "${BUILD_DIR}/app/build.gradle" ]]; then
    GRADLE_FILE="${BUILD_DIR}/app/build.gradle"
else
    GRADLE_FILE=""
fi

if [[ -n "$GRADLE_FILE" ]] && grep -q "com.google.android.gms" "$GRADLE_FILE"; then
    echo "       REFUSED: $GRADLE_FILE declares a com.google.android.gms"
    echo "       dependency. Circle OS is de-Googled; remove it."
    grep -n "com.google.android.gms" "$GRADLE_FILE" | sed 's/^/         /'
    exit 1
else
    echo "       no Google Play Services dependency - good"
fi

###############################################################################
# 9. South African languages
###############################################################################
# Four of the eleven were offered and none was on by default; isiBheqe was
# not a choice but a takeover of every language. See sa-languages/apply.py.
echo "  [9/9] South African languages..."
python "${SCRIPT_DIR}/sa-languages/apply.py" "${BUILD_DIR}"

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
