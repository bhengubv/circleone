package helium314.keyboard.latin.circleone.learn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The gallows and the figure, one stroke per wrong guess - drawn, not shown.
 *
 * <p>Each new stroke draws itself in, so a miss is something that happens,
 * not a picture that changes. The figure has a face that worries as the
 * chances run out. Win and the rope is cut: the figure stands free on the
 * ground in the positive colour and jumps. Lose and it turns the critical
 * colour with crossed-out eyes.
 *
 * <p>The drawing fits any box: it is laid out in a 5:4 frame centred in the
 * view, so the screen can give the gallows whatever height is left above
 * the keyboard and it still reads as a gallows.
 */
final class HangmanView extends View {

    static final int CHANCES = 6;

    private final Paint mFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFigure = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFace = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();
    private final int mAccent, mCritical, mPositive;
    private final long mDraw, mBounce;

    private int mWrong;
    private boolean mLost, mWon;
    private float mStroke = 1f;     // how much of the newest stroke is drawn
    private float mJump;            // 0..1 height of the winner's jump
    private ValueAnimator mAnim;

    HangmanView(Context c, int outline, int accent, int critical, int positive, float stroke,
            long drawMs, long bounceMs) {
        super(c);
        mAccent = accent;
        mCritical = critical;
        mPositive = positive;
        mDraw = drawMs;
        mBounce = bounceMs;
        for (Paint p : new Paint[] { mFrame, mFigure, mFace }) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        mFace.setStrokeWidth(stroke * 0.6f);
        mFrame.setColor(outline);
        mFigure.setColor(accent);
        mFace.setColor(accent);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        describe();
    }

    /** The state of the round; {@code animate} draws a new stroke in, or starts the winner's jump. */
    void set(int wrong, boolean lost, boolean won, boolean animate) {
        final int w = Math.max(0, Math.min(CHANCES, wrong));
        final boolean newStroke = w > mWrong;
        final boolean newWin = won && !mWon;
        mWrong = w;
        mLost = lost;
        mWon = won;
        final int colour = won ? mPositive : lost ? mCritical : mAccent;
        mFigure.setColor(colour);
        mFace.setColor(colour);
        if (mAnim != null) mAnim.cancel();
        mStroke = 1f;
        mJump = 0f;
        if (animate && newStroke && !lost) {
            mAnim = ValueAnimator.ofFloat(0f, 1f);
            mAnim.setDuration(mDraw);
            mAnim.setInterpolator(new DecelerateInterpolator());
            mAnim.addUpdateListener(a -> { mStroke = (float) a.getAnimatedValue(); invalidate(); });
            mStroke = 0f;
            mAnim.start();
        } else if (animate && newWin) {
            // Three jumps, each lower than the last.
            mAnim = ValueAnimator.ofFloat(0f, 3f);
            mAnim.setDuration(mBounce * 3);
            mAnim.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                final int hop = (int) t;
                final float f = t - hop;
                mJump = (float) Math.sin(Math.PI * f) / (hop + 1);
                invalidate();
            });
            mAnim.start();
        }
        describe();
        invalidate();
    }

    private void describe() {
        setContentDescription(mWon ? "Saved" : mLost ? "Out of chances"
                : (CHANCES - mWrong) + " of " + CHANCES + " chances left");
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnim != null) mAnim.cancel();
        super.onDetachedFromWindow();
    }

    /** How much of stroke {@code n} (1-based) to draw. */
    private float part(int n) {
        if (n > mWrong) return 0f;
        return n == mWrong ? mStroke : 1f;
    }

    private void line(Canvas c, float x0, float y0, float x1, float y1, float p, Paint paint) {
        if (p <= 0f) return;
        c.drawLine(x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        // A 5:4 frame, as large as the view allows, centred.
        float fw = getWidth(), fh = fw * 0.8f;
        if (fh > getHeight()) { fh = getHeight(); fw = fh * 1.25f; }
        if (fw <= 0 || fh <= 0) return;
        final float ox = (getWidth() - fw) / 2f, oy = (getHeight() - fh) / 2f;

        final float post = ox + fw * 0.22f, beam = ox + fw * 0.60f;
        final float top = oy + fh * 0.06f, ground = oy + fh * 0.94f;

        c.drawLine(ox + fw * 0.10f, ground, ox + fw * 0.90f, ground, mFrame);  // ground
        c.drawLine(post, ground, post, top, mFrame);                           // post
        c.drawLine(post, top, beam, top, mFrame);                              // beam
        c.drawLine(post, top + fh * 0.14f, post + fw * 0.10f, top, mFrame);    // brace

        final float r = fh * 0.075f;
        if (mWon) {
            // The rope is cut: the figure stands on the ground beside the
            // gallows, arms up, jumping.
            final float lift = mJump * fh * 0.12f;
            final float x = ox + fw * 0.76f;
            final float feet = ground - lift, hip = feet - fh * 0.20f, neck = hip - fh * 0.24f;
            final float cy = neck - r;
            drawHead(c, x, cy, r, 1f);
            drawFace(c, x, cy, r);
            c.drawLine(x, neck, x, hip, mFigure);
            c.drawLine(x, neck + fh * 0.05f, x - fw * 0.08f, neck - fh * 0.08f, mFigure);
            c.drawLine(x, neck + fh * 0.05f, x + fw * 0.08f, neck - fh * 0.08f, mFigure);
            c.drawLine(x, hip, x - fw * 0.06f, feet, mFigure);
            c.drawLine(x, hip, x + fw * 0.06f, feet, mFigure);
            return;
        }

        final float rope = top + fh * 0.12f;
        c.drawLine(beam, top, beam, rope, mFrame);
        final float cy = rope + r, neck = cy + r, hip = neck + fh * 0.26f, shoulder = neck + fh * 0.06f;
        if (mWrong >= 1) {
            drawHead(c, beam, cy, r, part(1));
            if (part(1) >= 1f) drawFace(c, beam, cy, r);
        }
        line(c, beam, neck, beam, hip, part(2), mFigure);
        line(c, beam, shoulder, beam - fw * 0.09f, shoulder + fh * 0.13f, part(3), mFigure);
        line(c, beam, shoulder, beam + fw * 0.09f, shoulder + fh * 0.13f, part(4), mFigure);
        line(c, beam, hip, beam - fw * 0.08f, hip + fh * 0.18f, part(5), mFigure);
        line(c, beam, hip, beam + fw * 0.08f, hip + fh * 0.18f, part(6), mFigure);
    }

    private void drawHead(Canvas c, float x, float cy, float r, float p) {
        mOval.set(x - r, cy - r, x + r, cy + r);
        c.drawArc(mOval, -90f, 360f * p, false, mFigure);
    }

    /** Calm at first, worried as the chances run out, crossed out when they are gone. */
    private void drawFace(Canvas c, float x, float cy, float r) {
        final float ex = r * 0.38f, ey = cy - r * 0.22f, e = r * 0.14f;
        if (mLost) {
            for (float sx : new float[] { x - ex, x + ex }) {
                c.drawLine(sx - e, ey - e, sx + e, ey + e, mFace);
                c.drawLine(sx - e, ey + e, sx + e, ey - e, mFace);
            }
        } else {
            c.drawPoint(x - ex, ey, mFace);
            c.drawPoint(x + ex, ey, mFace);
        }
        final float my = cy + r * 0.38f, mw = r * 0.42f;
        mOval.set(x - mw, my - r * 0.25f, x + mw, my + r * 0.25f);
        if (mWon || mWrong <= 2) {
            c.drawArc(mOval, 20f, 140f, false, mFace);          // smile
        } else if (mWrong <= 4 && !mLost) {
            c.drawLine(x - mw * 0.8f, my, x + mw * 0.8f, my, mFace); // unsure
        } else {
            mOval.offset(0, r * 0.22f);
            c.drawArc(mOval, 200f, 140f, false, mFace);         // frown
        }
    }
}
