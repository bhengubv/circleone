package helium314.keyboard.latin.circleone.learn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.CycleInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CircleOne Learn: Hangman with words you already know.
 *
 * <h3>What it teaches</h3>
 * The player speaks the language. The game shows a real sentence with one
 * word left out; the player knows the word and types it on CircleOne, and as
 * they type, the word appears in isiBheqe soHlamvu tile by tile. So they
 * learn three things at once: the keyboard, how it turns their language into
 * glyphs, and what words they already know look like in isiBheqe. Get it
 * right and the whole sentence is shown in both - the language as written
 * and the same sentence in isiBheqe - so every round ends in reading.
 *
 * <h3>The game</h3>
 * Type the word and press space. A wrong word draws the next stroke of the
 * figure; six and the round is lost. The Clue button - or "?" then space -
 * turns over the next syllable, letters and glyph, for fewer points. Find
 * the word and the rope is cut. The streak makes words longer; a lost round
 * ends the run; the best run is kept on the device.
 *
 * <p>The words and the sentences are real: the most frequent words of each
 * language in the NCHLT corpora the dictionaries come from, each with short
 * sentences from the same corpora (build_dicts.py --learn). Only words whose
 * every syllable has a glyph are dealt. The languages are the ones
 * isibheqe.org.za names as the script's - the siNtu languages of South
 * Africa. Words typed on the "isiZulu (isiBheqe)" layout arrive as glyphs
 * and are read back to their letters, so playing in the script works too.
 *
 * <h3>The look</h3>
 * CircleMetroUI's generated tokens - cm_color_*, cm_space_*, cm_radius_*,
 * cm_type_size_*, cm_motion_* - in light and dark, and the colourway Circle
 * OS is wearing when it runs there. A found word is a row of Metro tiles:
 * solid tile colour, the glyph large in white.
 */
public class LearnActivity extends Activity {

    private static final String TAG = "CircleOneLearn";
    private static final String PREFS = "circleone_learn";
    private static final String BLANK = "____";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*");

    /** isibheqe.org.za's siNtu languages that South Africa writes officially. */
    private static final String[][] LANGS = {
            { "zu", "isiZulu" }, { "xh", "isiXhosa" }, { "nr", "isiNdebele" },
            { "ss", "siSwati" }, { "st", "Sesotho" }, { "tn", "Setswana" },
            { "nso", "Sepedi" }, { "ve", "Tshivenda" }, { "ts", "Xitsonga" },
    };

    private static final int HIT = 10;          // per syllable found, not given by a clue
    private static final int SAVED = 5;         // per chance left when the word is found
    private static final int STREAK = 10;       // per word of the streak so far

    /** A word to find, and the sentences it was left out of. */
    private static final class Entry {
        final List<Syllables.Unit> units;
        final String word;
        final String[] clues;

        Entry(List<Syllables.Unit> units, String word, String[] clues) {
            this.units = units;
            this.word = word;
            this.clues = clues;
        }
    }

    private final Random mRandom = new Random();
    private Syllables mSyllables;
    private Typeface mScript;
    private String mLang;

    // The run.
    private int mScore, mStreak, mBest;

    // The round.
    private Entry mEntry;
    private String mClue;                       // the sentence, with BLANK where the word goes
    private int mGiven;                         // syllables turned over by the Clue button
    private final Set<String> mMissed = new LinkedHashSet<>();
    private boolean mOver, mWon;
    private boolean mJustGiven, mJustMissed;    // the animation to play, once
    private int mRoundPoints;

    // Views of the current build.
    private HangmanView mBoard;
    private LinearLayout mTiles, mMissedRow, mBanner;
    private TextView mScoreView, mStatsView, mClueView, mStatus, mBannerTitle, mBannerLatin, mBannerScript,
            mBannerLine;
    private EditText mField;
    private boolean mClearing;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            mSyllables = new Syllables(this);
        } catch (Exception e) {
            Log.w(TAG, "no syllable map", e);
        }
        try {
            mScript = Typeface.createFromAsset(getAssets(), "one.ttf");
        } catch (RuntimeException e) {
            mScript = Typeface.DEFAULT; // the system fallback chain carries the font on Circle OS
        }
        mBest = prefs().getInt("best", 0);
        mLang = startingLanguage();
        deal();
        build();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // the keyboard may have been switched while we were away
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ------------------------------------------------------------- the game

    private String startingLanguage() {
        final String phone = Locale.getDefault().getLanguage();
        for (String[] l : LANGS) if (l[0].equals(phone)) return l[0];
        return "zu";
    }

    /** Two syllables to start; one more for every two words in a row, up to five. */
    private int level() {
        return 1 + Math.min(3, mStreak / 2);
    }

    private void deal() {
        mMissed.clear();
        mOver = false;
        mWon = false;
        mGiven = 0;
        mJustGiven = false;
        mJustMissed = false;
        mRoundPoints = 0;
        mEntry = null;
        mClue = null;
        if (mSyllables == null || mSyllables.isEmpty()) return;
        final int want = level() + 1;
        final List<Entry> exact = new ArrayList<>(), any = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getAssets().open("learn/words_" + mLang + ".txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                final String[] cols = line.split("\t");
                if (cols.length < 2) continue; // a word with no sentence is no clue
                final String word = cols[0].trim();
                final List<Syllables.Unit> units = mSyllables.split(word);
                if (units.size() < 2 || units.size() > 5 || !mSyllables.writable(units)) continue;
                final String[] clues = new String[cols.length - 1];
                System.arraycopy(cols, 1, clues, 0, clues.length);
                final Entry e = new Entry(units, word, clues);
                any.add(e);
                if (units.size() == want) exact.add(e);
            }
        } catch (Exception e) {
            Log.w(TAG, "no words for " + mLang, e);
        }
        final List<Entry> from = exact.isEmpty() ? any : exact;
        if (from.isEmpty()) return;
        mEntry = from.get(mRandom.nextInt(from.size()));
        mClue = mEntry.clues[mRandom.nextInt(mEntry.clues.length)];
    }

    /** Space, enter or Go: a guess while the round is on, the next word once it is over. */
    private void submit() {
        if (mOver) {
            clearField();
            next();
            return;
        }
        guess();
    }

    private void guess() {
        if (mEntry == null || mOver) return;
        final boolean asked = mField.getText().toString().contains("?");
        final String g = mSyllables.readGuess(mField.getText());
        clearField();
        if (asked) {
            clue();
            return;
        }
        if (g.isEmpty()) return;
        if (mMissed.contains(g)) {
            say(g + " - already tried", color("warning", 0xFF805300));
            return;
        }
        if (g.equals(mEntry.word)) {
            mOver = true;
            mWon = true;
            final int n = mEntry.units.size();
            final int found = HIT * (n - mGiven);
            final int bonus = SAVED * (HangmanView.CHANCES - mMissed.size()) + STREAK * mStreak;
            mRoundPoints = found + bonus;
            mScore += mRoundPoints;
            mStreak++;
            keepBest();
            haptic(true);
        } else {
            mMissed.add(g);
            mJustMissed = true;
            haptic(false);
            if (mMissed.size() >= HangmanView.CHANCES) {
                mOver = true;
                keepBest();
            }
        }
        refresh();
        if (mOver) keyboard(false);
    }

    /**
     * Up while playing; down for the result, so the figure, the word and the
     * sentence in both scripts have the screen - the keyboard comes back with
     * the next word.
     */
    private void keyboard(boolean up) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null || mField == null) return;
        if (up) {
            mField.requestFocus();
            imm.showSoftInput(mField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(mField.getWindowToken(), 0);
        }
    }

    /** The clue: the next syllable turned over, letters and glyph. Never the last one. */
    private void clue() {
        if (mEntry == null || mOver) return;
        if (mGiven >= mEntry.units.size() - 1) {
            say("The last syllable is yours to find", color("warning", 0xFF805300));
            return;
        }
        mGiven++;
        mJustGiven = true;
        refresh();
    }

    private void next() {
        if (mOver && !mWon) {
            // The run is over: a fresh one.
            mScore = 0;
            mStreak = 0;
        }
        deal();
        refresh();
        keyboard(true);
    }

    private void keepBest() {
        if (mScore > mBest) {
            mBest = mScore;
            prefs().edit().putInt("best", mBest).apply();
        }
    }

    private void haptic(boolean good) {
        if (mField == null) return;
        final int kind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? (good ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT)
                : (good ? HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.LONG_PRESS);
        mField.performHapticFeedback(kind);
    }

    private void clearField() {
        mClearing = true;
        mField.setText("");
        mClearing = false;
        typing();
    }

    /** A sentence in isiBheqe soHlamvu: each word split into its syllables, each syllable its glyph. */
    private String inScript(String sentence) {
        final StringBuilder out = new StringBuilder();
        final Matcher m = WORD.matcher(sentence);
        int at = 0;
        while (m.find()) {
            out.append(sentence, at, m.start());
            for (Syllables.Unit u : mSyllables.split(m.group())) out.append(u.glyph != null ? u.glyph : u.latin);
            at = m.end();
        }
        out.append(sentence.substring(at));
        return out.toString();
    }

    // ---------------------------------------------------------------- look

    private int color(String role, int fallback) {
        String way = null;
        try {
            way = Settings.Secure.getString(getContentResolver(), "circle_metro_skin");
        } catch (RuntimeException ignored) {
            // not Circle OS: the default colourway
        }
        int id = way == null ? 0 : getResources().getIdentifier("cm_skin_" + way + "_" + role, "color", getPackageName());
        if (id == 0) id = getResources().getIdentifier("cm_color_" + role, "color", getPackageName());
        return id == 0 ? fallback : androidx.core.content.ContextCompat.getColor(this, id);
    }

    private int dimen(String name, int fallbackDp) {
        final int id = getResources().getIdentifier(name, "dimen", getPackageName());
        return id != 0 ? getResources().getDimensionPixelSize(id)
                : Math.round(fallbackDp * getResources().getDisplayMetrics().density);
    }

    private long motion(String name, int fallbackMs) {
        final int id = getResources().getIdentifier("cm_motion_" + name, "integer", getPackageName());
        return id != 0 ? getResources().getInteger(id) : fallbackMs;
    }

    private int space(int step) { return dimen("cm_space_" + step, step == 8 ? 32 : step * 4); }

    private float type(String name, int sp) { return dimen("cm_type_size_" + name, sp); }

    private TextView text(CharSequence s, int colour, float px, boolean bold) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, float radius) {
        final GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(Math.max(1, dimen("cm_border_width", 1)), stroke);
        return g;
    }

    private TextView button(String label, boolean primary, View.OnClickListener run) {
        final TextView b = text(label, primary ? color("on_accent", 0xFFFFFFFF) : color("accent_text", 0xFF00548F),
                type("label", 14), true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dimen("cm_target_min", 48));
        b.setMinWidth(dimen("cm_target_min", 48));
        b.setPadding(space(5), space(2), space(5), space(2));
        b.setBackground(primary ? round(color("accent", 0xFF005EA8), 0, dimen("cm_radius_control", 12))
                : round(color("accent_soft", 0xFFDCEEFF), 0, dimen("cm_radius_pill", 999)));
        b.setOnClickListener(run);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private LinearLayout.LayoutParams gap(int step) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = space(step);
        return lp;
    }

    private LinearLayout.LayoutParams gapWrap(int step) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = space(step);
        return lp;
    }

    // --------------------------------------------------------------- build

    private void build() {
        final int canvas = color("canvas", 0xFFF3F6FA);
        final int primary = color("text_primary", 0xFF14202C);
        final int secondary = color("text_secondary", 0xFF475667);
        final boolean edgeToEdge = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        final int t = dimen("cm_target_min", 48);

        // One screen, no scrolling: the gallows takes whatever height is left.
        // The window draws behind the bars, so since Android 15 the system no
        // longer resizes it for the keyboard (adjustResize alone does nothing):
        // the root is padded by the bars, and at the bottom by the keyboard
        // when it is up, so the whole game sits above the keyboard and the
        // gallows shrinks to make room.
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(canvas);
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(space(5), space(3), space(5), space(3));
        root.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Title, score, and the one door to the rules and the credit.
        final LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        final TextView title = text("Hangman", primary, type("title", 28), false);
        title.setLetterSpacing(-0.03f);
        titles.addView(title);
        mStatsView = text("", secondary, type("caption", 12), false);
        titles.addView(mStatsView);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mScoreView = text("", color("on_tile", 0xFFFFFFFF), type("title", 28), true);
        mScoreView.setGravity(Gravity.CENTER);
        mScoreView.setMinWidth(t * 2);
        mScoreView.setPadding(space(3), space(1), space(3), space(1));
        mScoreView.setBackground(round(color("tile", 0xFF005EA8), 0, dimen("cm_radius_tile", 14)));
        head.addView(mScoreView);
        final TextView info = text("?", color("accent_text", 0xFF00548F), type("title", 28) * 0.8f, true);
        info.setGravity(Gravity.CENTER);
        info.setContentDescription("How to play");
        info.setBackground(round(color("accent_soft", 0xFFDCEEFF), 0, dimen("cm_radius_pill", 999)));
        info.setOnClickListener(v -> rules());
        final LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(t, t);
        ip.leftMargin = space(2);
        head.addView(info, ip);
        page.addView(head);

        if (!circleOneIsTheKeyboard()) page.addView(keyboardCard(primary), gap(2));

        page.addView(languages(), gap(2));

        // The clue: a real sentence with the word left out.
        mClueView = text("", primary, type("body", 16) * 1.15f, false);
        mClueView.setPadding(space(4), space(3), space(4), space(3));
        mClueView.setBackground(round(color("surface", 0xFFFFFFFF), color("outline", 0xFFC6D0DB),
                dimen("cm_radius_card", 18)));
        page.addView(mClueView, gap(3));

        // The gallows takes whatever height is left.
        mBoard = new HangmanView(this, color("outline_strong", 0xFF78899B), color("accent", 0xFF005EA8),
                color("critical", 0xFFB3261E), color("positive", 0xFF176B3A), dimen("cm_space_1", 4) * 1.5f,
                motion("slow", 360), motion("slow", 360));
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = space(2);
        page.addView(mBoard, sp);

        // The word, as tiles - filled with glyphs as it is typed.
        final HorizontalScrollView tileScroll = new HorizontalScrollView(this);
        tileScroll.setHorizontalScrollBarEnabled(false);
        mTiles = new LinearLayout(this);
        mTiles.setGravity(Gravity.CENTER);
        tileScroll.addView(mTiles);
        final LinearLayout tileRow = new LinearLayout(this);
        tileRow.setGravity(Gravity.CENTER_HORIZONTAL);
        tileRow.addView(tileScroll);
        page.addView(tileRow, gap(2));

        // Chances left, and the words that were not it - and in their place
        // once the round is over, the result: the sentence in both scripts.
        mStatus = text("", primary, type("label", 14), true);
        mStatus.setGravity(Gravity.CENTER);
        page.addView(mStatus, gap(2));
        mMissedRow = new LinearLayout(this);
        mMissedRow.setGravity(Gravity.CENTER);
        final HorizontalScrollView missedScroll = new HorizontalScrollView(this);
        missedScroll.setHorizontalScrollBarEnabled(false);
        missedScroll.addView(mMissedRow);
        final LinearLayout missedLine = new LinearLayout(this);
        missedLine.setGravity(Gravity.CENTER_HORIZONTAL);
        missedLine.addView(missedScroll);
        page.addView(missedLine, gap(1));
        mBanner = new LinearLayout(this);
        mBanner.setOrientation(LinearLayout.VERTICAL);
        mBanner.setPadding(space(4), space(3), space(4), space(3));
        mBanner.setBackground(round(color("surface", 0xFFFFFFFF), color("outline", 0xFFC6D0DB),
                dimen("cm_radius_card", 18)));
        mBannerTitle = text("", primary, type("title", 28) * 0.8f, true);
        mBanner.addView(mBannerTitle);
        mBannerLatin = text("", primary, type("body", 16), false);
        mBanner.addView(mBannerLatin, gap(1));
        mBannerScript = text("", color("accent_text", 0xFF00548F), type("title", 28) * 0.9f, false);
        mBannerScript.setTypeface(mScript);
        mBannerScript.setLineSpacing(0, 1.35f);
        mBanner.addView(mBannerScript, gap(1));
        final LinearLayout end = new LinearLayout(this);
        end.setGravity(Gravity.CENTER_VERTICAL);
        mBannerLine = text("", secondary, type("caption", 12), false);
        end.addView(mBannerLine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        end.addView(button("Next word", true, v -> next()));
        mBanner.addView(end, gap(2));
        mBanner.setVisibility(View.GONE);
        page.addView(mBanner, gap(2));

        // Typing, and beside it the clue - also "?" then space.
        final LinearLayout play = new LinearLayout(this);
        play.setGravity(Gravity.CENTER_VERTICAL);
        mField = new EditText(this);
        mField.setSingleLine(true);
        mField.setHint("Type the missing word");
        mField.setTextColor(primary);
        mField.setHintTextColor(color("text_disabled", 0xFF5B6877));
        mField.setTextSize(TypedValue.COMPLEX_UNIT_PX, type("body", 16) * 1.15f);
        mField.setMinHeight(t + space(2));
        mField.setPadding(space(4), space(2), space(4), space(2));
        mField.setBackground(round(color("surface", 0xFFFFFFFF), color("accent", 0xFF005EA8),
                dimen("cm_radius_control", 12)));
        mField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mField.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mField.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submit();
                return true;
            }
            return false;
        });
        mField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (mClearing) return;
                // Space ends the word, as it does when typing.
                if (s.toString().matches("(?s).*\\s.*")) {
                    submit();
                    return;
                }
                typing();
            }
        });
        play.addView(mField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView clue = button("Clue", false, v -> clue());
        clue.setContentDescription("Clue: turn over the next syllable");
        final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = space(2);
        play.addView(clue, cp);
        page.addView(play, gap(3));

        if (edgeToEdge) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                final Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                final Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(sys.left, sys.top, sys.right, Math.max(sys.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(root);
        if (edgeToEdge) {
            // Android 11+: draw behind the bars and pad by their real size.
            // AFTER setContentView: before it the window has no decor view and
            // getInsetsController() throws - which crashed this screen on its
            // first launch on AngelDust.
            getWindow().setStatusBarColor(0);
            getWindow().setNavigationBarColor(0);
            getWindow().setDecorFitsSystemWindows(false);
            final WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                final boolean light = (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
                final int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                bars.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        }
        refresh();
        // The game is played on the keyboard: bring it up - unless a result is showing.
        mField.post(() -> keyboard(!mOver));
    }

    /** A row of the nine languages; the chosen one wears the accent. */
    private View languages() {
        final HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        final LinearLayout row = new LinearLayout(this);
        for (String[] l : LANGS) {
            final boolean on = l[0].equals(mLang);
            final TextView chip = text(l[1], on ? color("on_accent", 0xFFFFFFFF) : color("text_primary", 0xFF14202C),
                    type("label", 14), on);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dimen("cm_target_min", 48));
            chip.setPadding(space(4), space(2), space(4), space(2));
            chip.setBackground(round(on ? color("accent", 0xFF005EA8) : color("surface", 0xFFFFFFFF),
                    on ? 0 : color("outline", 0xFFC6D0DB), dimen("cm_radius_pill", 999)));
            chip.setOnClickListener(v -> {
                if (l[0].equals(mLang)) return;
                // A new language is a new run: the streak was earned in the old one.
                mLang = l[0];
                mScore = 0;
                mStreak = 0;
                deal();
                build();
            });
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = space(2);
            row.addView(chip, lp);
        }
        s.addView(row);
        return s;
    }

    private void rules() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("A sentence is shown with one word left out. You know the word - type it on CircleOne "
                        + "and press space. As you type, the word appears in isiBheqe soHlamvu, syllable by "
                        + "syllable.\n\n"
                        + "Find it and you see the whole sentence in both: as you know it, and in isiBheqe. "
                        + "Each wrong word draws a stroke - six and the round is lost.\n\n"
                        + "Clue (or ? then space) turns over the next syllable, for fewer points.\n\n"
                        + "Points: " + HIT + " per syllable you found, " + SAVED + " per chance left, and a "
                        + "streak bonus. Words grow longer as the streak grows. Losing a round ends the run.\n\n"
                        + "Pick isiZulu (isiBheqe) on the keyboard - swipe the space bar - to type in the script "
                        + "itself.\n\n"
                        + "Words and sentences from the NCHLT Text Corpora - South African Department of Arts "
                        + "and Culture & CTexT, North-West University - CC BY 2.5 South Africa.")
                .setPositiveButton("Play", null)
                .show();
    }

    private boolean circleOneIsTheKeyboard() {
        try {
            final String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return ime != null && ime.startsWith(getPackageName() + "/");
        } catch (RuntimeException e) {
            return true; // cannot tell: do not nag
        }
    }

    /** The game teaches the keyboard, so it says so when another one is in use - with the one action. */
    private View keyboardCard(int primary) {
        final LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(space(4), space(2), space(2), space(2));
        c.setBackground(round(color("accent_soft", 0xFFDCEEFF), 0, dimen("cm_radius_card", 18)));
        final TextView say = text("Play with CircleOne to learn its keys.", primary, type("label", 14), true);
        c.addView(say, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(button("Choose", true, v -> {
            final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));
        return c;
    }

    // ------------------------------------------------------------- refresh

    /** A passing word under the word: a warning, a hint. Cleared by the next refresh. */
    private void say(String message, int colour) {
        mStatus.setText(message);
        mStatus.setTextColor(colour);
        shake(mStatus);
    }

    private void shake(View v) {
        v.animate().cancel();
        v.setTranslationX(0);
        v.animate().translationX(space(2)).setInterpolator(new CycleInterpolator(3))
                .setDuration(motion("slow", 360)).start();
    }

    /** The syllables typed so far, for the tiles to show as glyphs while the word is typed. */
    private List<Syllables.Unit> typed() {
        if (mField == null || mSyllables == null) return new ArrayList<>();
        final String g = mSyllables.readGuess(mField.getText());
        return g.isEmpty() ? new ArrayList<>() : mSyllables.split(g);
    }

    /** While typing: only the tiles change. */
    private void typing() {
        if (mTiles == null || mEntry == null || mOver) return;
        tiles(typed());
    }

    private void tiles(List<Syllables.Unit> typed) {
        mTiles.removeAllViews();
        final List<Syllables.Unit> units = mEntry.units;
        final boolean lost = mOver && !mWon;
        // Every tile in view (COMPONENTS.md - no clipping). A tile keeps its
        // size while the word fits; when it does not the width is shared out
        // evenly instead, and the glyph shrinks to its tile.
        final int n = units.size();
        final int inside = getResources().getDisplayMetrics().widthPixels - 2 * space(5);
        final int fit = inside / n - space(2);
        final int tileMin = dimen("cm_target_min", 48) + space(3);
        final boolean share = fit < tileMin;
        final int bigGlyph = Math.round(type("title", 28) * 1.3f);
        final int glyphSize = share ? Math.min(bigGlyph, fit - 2 * space(2)) : bigGlyph;
        for (int i = 0; i < n; i++) {
            final Syllables.Unit u = units.get(i);
            final boolean given = i < mGiven;
            final boolean shown = mWon || lost || given;
            // While typing, tile i shows what the player's own i-th syllable
            // is in isiBheqe - right or wrong, it is their word being written.
            final Syllables.Unit mine = !shown && i < typed.size() ? typed.get(i) : null;
            final LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            if (!share) tile.setMinimumWidth(tileMin);
            tile.setPadding(space(2), space(2), space(2), space(2));
            // Found: Metro tiles. Given by a clue: the soft accent. On a loss:
            // the word in the critical colour, never mistaken for one found.
            final int fill = mWon ? color("tile", 0xFF005EA8)
                    : given || mine != null ? color("accent_soft", 0xFFDCEEFF) : color("surface_raised", 0xFFF9FBFD);
            final int ink = mWon ? color("on_tile", 0xFFFFFFFF)
                    : lost ? color("critical", 0xFFB3261E)
                    : given || mine != null ? color("accent_text", 0xFF00548F) : color("text_primary", 0xFF14202C);
            tile.setBackground(round(fill, mWon ? 0 : color("outline", 0xFFC6D0DB), dimen("cm_radius_tile", 14)));
            final String glyphText = shown ? u.glyph : mine != null ? (mine.glyph != null ? mine.glyph : mine.latin) : " ";
            final TextView glyph = text(glyphText, ink, glyphSize, false);
            glyph.setTypeface(mScript);
            glyph.setGravity(Gravity.CENTER);
            tile.addView(glyph);
            final TextView latin = text(shown ? u.latin : mine != null ? mine.latin : "_", ink, type("label", 14), true);
            latin.setGravity(Gravity.CENTER);
            tile.addView(latin);
            tile.setContentDescription(shown ? "syllable " + u.latin : mine != null ? "typed " + mine.latin
                    : "hidden syllable");
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    share ? fit : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = space(2);
            mTiles.addView(tile, lp);
            final boolean pop = (mWon && mJustGiven) || (mJustGiven && i == mGiven - 1);
            if (pop) {
                // A tile turning over: a pop, Metro's tile motion.
                tile.setScaleX(0.6f);
                tile.setScaleY(0.6f);
                tile.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.5f))
                        .setStartDelay(mWon ? i * motion("fast", 120) : 0)
                        .setDuration(motion("normal", 220)).start();
            }
        }
    }

    private void refresh() {
        if (mTiles == null) return;
        mMissedRow.removeAllViews();
        mScoreView.setText(String.valueOf(mScore));
        mScoreView.setContentDescription("Score " + mScore);
        final String lang = languageName(mLang);
        mStatsView.setText(lang + "  ·  Level " + level() + "  ·  Streak " + mStreak + "  ·  Best " + mBest);
        if (mEntry == null) {
            mTiles.removeAllViews();
            mClueView.setText(mSyllables == null || mSyllables.isEmpty()
                    ? "The isiBheqe syllable map is missing from this build."
                    : "No words for this language in this build.");
            mStatus.setText("");
            mBoard.set(0, false, false, false);
            mBanner.setVisibility(View.GONE);
            return;
        }
        final boolean lost = mOver && !mWon;
        mClueView.setText(clueText());
        // The win pops every tile in turn; reuse the flag for it.
        if (mWon) mJustGiven = true;
        tiles(mOver ? new ArrayList<>() : typed());
        if (mJustMissed) shake(mTiles);
        mBoard.set(mMissed.size(), lost, mWon, mJustMissed || mWon);
        mJustGiven = false;
        mJustMissed = false;

        // Chances as pips, then the wrong words struck through.
        final int left = HangmanView.CHANCES - mMissed.size();
        final StringBuilder pips = new StringBuilder();
        for (int i = 0; i < HangmanView.CHANCES; i++) pips.append(i < left ? "●" : "○").append(' ');
        mStatus.setTextColor(left <= 2 ? color("critical", 0xFFB3261E) : color("text_primary", 0xFF14202C));
        mStatus.setText(pips.toString().trim() + "   " + left + (left == 1 ? " chance" : " chances")
                + "  ·  " + mEntry.units.size() + " syllables");
        for (String m : mMissed) {
            final TextView x = text(m, color("critical", 0xFFB3261E), type("label", 14), true);
            x.setPaintFlags(x.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            x.setPadding(space(2), 0, space(2), 0);
            x.setContentDescription(m + ", not the word");
            mMissedRow.addView(x);
        }

        // The end of a round: the result takes the place of the chances.
        mStatus.setVisibility(mOver ? View.GONE : View.VISIBLE);
        mMissedRow.setVisibility(mOver ? View.GONE : View.VISIBLE);
        if (mOver) {
            final String full = mClue.replace(BLANK, mEntry.word);
            if (mWon) {
                mBannerTitle.setTextColor(color("positive", 0xFF176B3A));
                mBannerTitle.setText("Saved! +" + mRoundPoints);
                mBannerLine.setText("Streak " + mStreak);
            } else {
                mBannerTitle.setTextColor(color("critical", 0xFFB3261E));
                mBannerTitle.setText("The word was " + mEntry.word);
                mBannerLine.setText("Run over - " + mScore + " points" + (mScore >= mBest && mScore > 0
                        ? ", a new best!" : "  ·  best " + mBest));
            }
            // The sentence in both: as the player knows it, and in isiBheqe.
            mBannerLatin.setText(withWord(full, mEntry.word));
            mBannerScript.setText(inScript(full));
            if (mBanner.getVisibility() != View.VISIBLE) {
                mBanner.setVisibility(View.VISIBLE);
                mBanner.setAlpha(0f);
                mBanner.setTranslationY(space(5));
                mBanner.animate().alpha(1f).translationY(0).setDuration(motion("normal", 220)).start();
            }
        } else {
            mBanner.setVisibility(View.GONE);
        }
    }

    /** The clue sentence, its blank marked in the accent. */
    private CharSequence clueText() {
        final SpannableStringBuilder s = new SpannableStringBuilder(mClue);
        final int at = mClue.indexOf(BLANK);
        if (at >= 0) {
            s.setSpan(new ForegroundColorSpan(color("accent_text", 0xFF00548F)), at, at + BLANK.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), at, at + BLANK.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** The completed sentence, the word in it in bold. */
    private CharSequence withWord(String full, String word) {
        final SpannableStringBuilder s = new SpannableStringBuilder(full);
        final int at = mClue.indexOf(BLANK);
        if (at >= 0) {
            s.setSpan(new StyleSpan(Typeface.BOLD), at, at + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(color("accent_text", 0xFF00548F)), at, at + word.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private static String languageName(String code) {
        for (String[] l : LANGS) if (l[0].equals(code)) return l[1];
        return code;
    }
}
