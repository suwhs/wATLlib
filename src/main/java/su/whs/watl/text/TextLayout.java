package su.whs.watl.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 07.12.14.
 * <p/>
 * base class for calculate formatted text spans placement and draw
 * <p/>
 * <p/>
 * we cannot extends Layout, because android.text.Layout has some 'final' methods
 */


/**
 * * states:
 * measured (invalidates on change base font size)
 * layouted (depends on 'measured' and 'width', 'lineBreaker', 'imagePlacementHandler' + if requested height > reflowed Height)
 */

public class TextLayout implements ContentView.OptionsChangeListener {

    private int mMeasuredTo = 0;

    private Spanned mText;
    private int mParagraphStartMargin = 0;
    private int mParagraphTopMargin = 0;
//    protected ImagePlacementHandler mImagePlacementHandler;


    /* time interval, used in LineSpan.reflow() for call onProgress()  */
    protected static final int DEFAULT_REFLOW_STEP_MS = 150;

    private static final String TAG = "TextLayout";
    protected char[] chars; /* for performance - we need direct access to text as array of chars */
    protected LineSpan lineSpan;
    protected int width;              //
    private int reflowedWidth = -1;
    protected int height = -1;
    protected int reflowedHeight = -1;
    protected int requestedHeight = -1;
    private float reflowedTextSize = -1;

    protected TextInfoInvalidateListener listener = null;
    private boolean reflowedJustification = true;
    protected boolean justification = true;
    protected int viewHeight = -1;
    protected List<TextLine> lines;
    private TextPaint paint = new TextPaint();
    private TextPaint reflowPaint = new TextPaint();
    private ContentView.Options mOptions = null;

    private int mStart;
    private int mEnd;
    protected int mStartLine = 0;

    // protected LineBreaker mLineBreaker = new DefaultLineBreaker();
    // private boolean debugDraw;
    private int mLineHeightTreshold = 0;
    private boolean mNeedTotalHeight = false;
    private boolean mReflowFinished = false;

    /* selection handling vars */
    private int mSelectionStart = 0;
    private int mSelectionEnd = 0;
    private int mSelectionColor = Color.GRAY;

    /* highlight handling vars */
    private int mHighlightStart = 0;
    private int mHighlightEnd = 0;
    private int mHighlightColor = Color.YELLOW;
    private boolean mIsLayouted = false;

    public boolean isLayouted() {
        return mIsLayouted;
    }

    public void notifyTextHeightChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mIsLayouted = true;
                if (listener != null)
                    listener.onTextHeightChanged();
            }
        });
    }

    protected void notifyTextInfoInvalidated() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mIsLayouted = false;
                if (listener != null)
                    listener.onTextInfoInvalidated();
            }
        });
    }

    protected void notifyTextReady() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mIsLayouted = true;
                if (listener != null) {
                    listener.onTextHeightChanged();
                    listener.onTextReady();
                }
            }
        });
    }

    public int getLineForVertical(float y, int startLine) {
        int bottom = 0;
        if (lines == null) {
            Log.e(TAG, "need reflow!");
            return 0;
        }
        for (int j = startLine; j < lines.size(); j++ /* LineSpan.LineDescription line : lines */) {
            TextLine line = lines.get(j);
            if (y > bottom && y < bottom + line.height) {
                return j;
            }
            bottom += line.height;
        }
        return -1;
    }

    public int getLineForVertical(float y) {
        return getLineForVertical(y, 0);
    }

    public void setInvalidateListener(TextInfoInvalidateListener listener) {
        this.listener = listener;
    }

    protected interface LineSpanReflowProgressListener {
        /**
         * notify listener about reflow() progress
         *
         * @param lines            - actual lines list
         * @param height           - collected lines height
         * @param viewHeightExceed - notify listener about view height exceed
         * @return
         */
        public boolean onProgress(List<TextLine> lines, int height, boolean viewHeightExceed);

        /**
         * notify listener about reflow() finished
         *
         * @param lines  - result of reflow() (list of LineDescription)
         * @param height - summary of lines height
         */
        public void onFinish(List<TextLine> lines, int height);
    }

    protected interface LineSpanReflowProgressListener2 extends LineSpanReflowProgressListener {
        public boolean updateGeometry(int[] geometry);
    }


    /**
     * default LineBreaker implementation
     */

    private static class DefaultLineBreaker extends LineBreaker {
        private static final String TAG = "DefaultLineBreaker";

        @Override
        public int nearestLineBreak(char[] text, int start, int _end, int limit) {
            int end = _end;

            for (; end >= start; end--) {
                if (text[end] == ' ' || text[end] == ',' || text[end] == '.' || text[end] == '!' || text[end] == '-' || text[end] == '?')
                    break;
            }
            if ((end > start - 1) && end < limit && Character.isLetter(text[end]) && Character.isLetter(text[end + 1]))
                end = end | HYPHEN;
            return end; // force break, if not fit
        }
    }

    public TextLayout(Spanned text, int start, int end, TextPaint paint, TextInfoInvalidateListener invalidateListener) {
        this(text, start, end, paint, new ContentView.Options(), invalidateListener);
    }

    /**
     * @param text               - Spanned instance
     * @param start              - begin of text whe want to layout
     * @param end                - end of text
     * @param paint              - default paint
     * @param invalidateListener
     */

    public TextLayout(Spanned text, int start, int end, TextPaint paint, ContentView.Options options, TextInfoInvalidateListener invalidateListener) {
        listener = invalidateListener;
        mStart = start;
        mEnd = end;
        chars = new char[end - start];
        mText = text;
        if (mText instanceof Editable) {
            /* we need to monitor DynamicDrawableSpan for updates, invalidates, resizes */
            ((Editable) mText).setSpan(new SpanWatcher() {
                @Override
                public void onSpanAdded(Spannable text, Object what, int start, int end) {
                }

                @Override
                public void onSpanRemoved(Spannable text, Object what, int start, int end) {

                }

                @Override
                public void onSpanChanged(Spannable text, Object what, int ostart, int oend, int nstart, int nend) {

                }
            }, 0, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextUtils.getChars(text, start, end, chars, 0);
        mOptions = options;
        mOptions.setChangeListener(this);
        if (mOptions.getLineBreaker() == null) {
            mOptions.setLineBreaker(new DefaultLineBreaker());
        }
        lineSpan = LineSpan.prepare(text, start, end, mParagraphStartMargin, mParagraphTopMargin);
        this.paint = paint;
    }

    public TextLayout() {

    }

    public TextLayout(Spanned text, TextPaint paint) {
        this(text, 0, text.length(), paint, null);
    }

    public TextLayout(Spanned text, TextPaint paint, TextInfoInvalidateListener invalidateListener) {
        this(text, 0, text.length(), paint, invalidateListener);
    }

    public void release() {
        Log.v(TAG, "layout release :" + this);
        mText = null;
        chars = null;
        if (lines != null) {
            //   for (LineSpan.LineDescription line : lines)
            //       line.release();
            lines.clear();
        }
        /*
        if (lineSpan != null) // BIG EFFECT
            lineSpan.release();
            */
        synchronized (this) {
            lineSpan = null;
        }
    }


    /**
     * @param view           - view we attached to
     * @param x              - x coordinate
     * @param y              - y coordinate
     * @param startsFromLine
     * @return clicked character offset
     */

    public int getOffsetForCoordinates(View view, float x, float y, int startsFromLine) {
        int bottom = 0;
        if (lines == null) {
            Log.e(TAG, "need reflow!");
            return 0;
        }
        for (int j = startsFromLine; j < lines.size(); j++ /* LineSpan.LineDescription line : lines */) {
            TextLine line = lines.get(j);
            if (line.wrapHeight > 0 && line.span != null && line.span.get().isDrawable) {
                if (y < bottom || y > (bottom + line.wrapHeight))
                    continue;
                if (line.span.get().gravity == Gravity.LEFT) {
                    if (line.span.get().drawableScaledWidth > x)
                        return line.span.get().start;
                } else if (line.span.get().gravity == Gravity.RIGHT) {
                    if (x > width - line.span.get().drawableScaledWidth) {
                        return line.span.get().start;
                    }
                } else {
                    return line.span.get().start;
                }
                continue;
            } else if (y > bottom && y < bottom + line.height) {
                int i = getPrimaryHorizontal(line, (int) x, width);
                return i;
            }
            bottom += line.height;
        }
        return -1;
    }


    public int getLinesCount() {
        return lines == null ? 0 : lines.size();
    }

    public int getLineStart(int line) {
        if (lines != null)
            return lines.get(line).start;
        return 0;
    }

    public int getLineEnd(int line) {
        if (lines != null && lines.size() > line)
            return lines.get(line).end;
        return 0;
    }

    public int getLineTop(int line) {
        if (lines == null) return 0;
        int y = 0;
        for (int i = 0; i < line; i++) {
            y += lines.get(i).height;
        }
        return y;
    }

    public int getLineBottom(int line) {
        int top = getLineTop(line);
        return top + lines.get(line).height;
    }

    /**
     * calculate horizontal offset of character with index 'position'
     *
     * @param line
     * @param position
     * @param viewWidth
     * @return
     */

    public int getPrimaryHorizontal(int line, int position, float viewWidth) {
        return getCharacterOffsetX(lines.get(line), position, getOptions().isJustification(), viewWidth);
    }

    /**
     * @return
     */

    public int getHeight() {
        return this.height;
    }

    /**
     * @param size
     */

    public void setTextSize(float size) {
        float oldSize = paint.getTextSize();
        if (oldSize != size && lineSpan != null) {
            invalidateMeasurement();
        }
        paint.setTextSize(size);
        Log.v(TAG, "setTextSize() restarts reflow() trough setSize(" + width + "," + requestedHeight + "," + viewHeight);
        setSize(width, requestedHeight, viewHeight);

        // invalidate();
    }

    /* set default TextPaint */
    /* TODO: froce invalidate all? */

    /**
     * @param paint
     */

    public void setPaint(TextPaint paint) {
        this.paint = paint;
        if (isLayouted())
            invalidateMeasurement();
    }

    /* set size for layout */

    /**
     * @param width  - width of view
     * @param height - height of view or -1, if need height of all text
     */

    public void setSize(int width, int height) {
        setSize(width, height, -1);
    }

    /**
     * set size for layout where 'visible height' < available height
     * <p/>
     * used for split layout to pages
     *
     * @param width      - width of view
     * @param height     - height (-1 if unlimited height)
     * @param viewHeight - 'visible height'
     */

    public void setSize(int width, int height, int viewHeight) {
        /* do not call invalidate() if setSize called with already calculated parameters */
        // Log.v(TAG, "paint textSize=" + paint.getTextSize() + ", reflowedTextSize = " + reflowedTextSize);
        Log.v(TAG, "setSize(" + width + "," + height + "," + viewHeight + ")");
        this.width = width;
        if (height > -1) {
            this.height = height;
            this.requestedHeight = height;
        }
        if (height < 0) {
            mNeedTotalHeight = true;
        } else {
            mNeedTotalHeight = false;
        }
        this.viewHeight = viewHeight;
        if (!isLayouted())
            invalidate();
    }

    /*
    * it's a 2 case - we have height limit, and we need total text height as result
    *
    * so, in first case - we set flags mNeedTotalHeight = false
    *
    * and in other - flag mNeedTotalHeight = true
    *
    * so, in LineSpanReflowProgressListener whe check that flag
    * and if mNeedTotalHeight = true - we must call requestLayout() for parent, and in onMeasure
    * we returns actual knwon height
    *
    * so, we need known - if onMeasure called
    *
    * */

    /**
     * invalidate layout
     * actually, invalidate() works only if width/font size changed
     */
    public void invalidate() {
        if (reflowedWidth == width && reflowedTextSize == paint.getTextSize())
            return;
        onBeforeReflow();
        doReflowInBackground();
        reflowedWidth = width;
        reflowedTextSize = paint.getTextSize();
    }

    protected void onBeforeReflow() {
        mIsLayouted = false;
        if (listener != null)
            listener.onTextInfoInvalidated();
    }

    /**
     * return layout's Options
     *
     * @return
     */

    public ContentView.Options getOptions() {
        return mOptions;
    }

    /**
     * start thread with layout calculation
     */
    private void doReflowInBackground() {
        /* */
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("TextLayout setSize() must be called from UI Thread");
        }
        if (mReflowBackgroundTask != null && isReflowBackgroundTaskRunning()) {
            setReflowBackgroundTaskCancelled(true);
            try {
                mReflowBackgroundTask.join();
            } catch (InterruptedException e) {
            }
        }
        mReflowBackgroundTask = new Thread(new ReflowBackgroundTask());
        mIsLayouted = true;
        mReflowBackgroundTask.start();
    }

    /**
     * render calculated lines to canvas
     *
     * @param canvas - canvas
     * @param left   - left offset of layout rect
     * @param top    - top offset of layout rect
     * @param right  - right bound of layout rect
     * @param bottom - bottom bound of layout rect
     */


    public void draw(Canvas canvas, int left, int top, int right, int bottom, int startLine) {
        workPaint.set(paint);
        int state = canvas.save();
        int width = right - left;
        if (reflowedWidth < width)
            width = reflowedWidth;
        canvas.translate(left, top);
        canvas.clipRect(0, 0, right - left, bottom - top);

        if (getLineSpan() != null) {
            if (isReflowBackgroundTaskRunning()) {
                draw(lines, startLine, getChars(), canvas, width, viewHeight, getPaint(), 0, 0, 0, 0, 0, 0, getOptions().isJustification());
            } else {
                draw(lines, startLine, getChars(), canvas, width, viewHeight, getPaint(), getSelectionStarts(), getSelectionEnds(), getSelectionColor(), mHighlightStart, mHighlightEnd, mHighlightColor, getOptions().isJustification());
            }
        } else {
            Log.w(TAG, "could not draw() getLines() returns null");
        }
        canvas.restoreToCount(state);
    }

    /**
     * @param canvas
     * @param left
     * @param top
     * @param right
     * @param bottom
     */

    public void draw(Canvas canvas, int left, int top, int right, int bottom) {
        draw(canvas, left, top, right, bottom, mStartLine);
    }


    /**
     * @return char[] content of getText()
     */
    protected char[] getChars() {
        return chars;
    }

    /**
     * @return LineSpan chain
     */

    protected LineSpan getLineSpan() {
        return lineSpan;
    }

    /**
     * set highlight text range
     *
     * @param start - range start
     * @param end   - - range end
     * @param color - color (ARGB)
     */

    public void setHighlight(int start, int end, int color) {
        mHighlightStart = start;
        mHighlightEnd = end;
        mHighlightColor = color;
    }

    /**
     * set selected text range
     *
     * @param start - range start
     * @param end   - range end
     * @param argb  - color (ARGB)
     */

    public void setSelection(int start, int end, int argb) {
        mSelectionStart = start;
        mSelectionEnd = end;
        mSelectionColor = argb;
    }

    protected int getSelectionColor() {
        return mSelectionColor;
    }

    /**
     * reset hightlits (similar to setHighlight(0,0,0))
     */

    public void resetHighlight() {
        mHighlightStart = 0;
        mHighlightEnd = 0;
    }

    /**
     * @return begin of selection range
     */

    public int getSelectionStarts() {
        return mSelectionStart;
    }

    /**
     * @return end of selection range
     */

    public int getSelectionEnds() {
        return mSelectionEnd;
    }

    /*
        'protected'
        for override reflow() call parameters
     */

    /**
     * layout calculation method (called from background thread)
     *
     * @param listener - callbacks
     */

    protected void runReflow(LineSpanReflowProgressListener listener) {
        lines = null;
        reflowPaint.set(paint);
        synchronized (this) {
            if (lineSpan != null)
                lineSpan.clearCache(false);
        }
        reflow(chars, mStart, mEnd, lineSpan,
                0, width, 0, requestedHeight, viewHeight,
                reflowPaint,
                listener
                , getOptions());
    }

    public static class TextLine {
        public int whitespaces = 0;
        public int descent;
        public float width; // real line width (with margin, but without justification)
        public float justifyArgument = 0f;
        public LeadingMarginSpan leadingMargin;
        WeakReference<LineSpan> span;
        // LineSpanBreak startBreak = null; // deprecated
        int start;
        int end;
        int height;
        WeakReference<LineSpanBreak> afterBreak = null;
        int margin = 0;
        int direction = Layout.DIR_LEFT_TO_RIGHT;
        int gravity = Gravity.NO_GRAVITY;
        int wrapHeight = 0;
        int wrapWidth = 0;
        int wrapMargin = 0;

        public TextLine(ReflowState state, int lineStartAt, LeadingMarginSpan leadingMarginSpan) {
            this.whitespaces = state.whitespaces;
            this.width = state.lineWidth;
            this.span = new WeakReference<LineSpan>(state.carrierReturnSpan);
            this.afterBreak = state.carrierReturnBreak == null ? null : new WeakReference<LineSpanBreak>(state.carrierReturnBreak);
            this.start = lineStartAt;
            this.height = state.height; // (int) (state.height + state.descent + state.leading * lineSpacingMultiplier + lineSpacingAdd);
            this.descent = state.descent;
            this.end = state.character + 1;
            this.leadingMargin = leadingMarginSpan;

        }

        public TextLine(LineSpan span, int wrapHeight, int wrapWidth, int gravity) {
            assert (!span.isDrawable);
            this.wrapWidth = wrapWidth;
            this.wrapHeight = wrapHeight;
            this.span = new WeakReference<LineSpan>(span);
            this.width = 0;
            this.height = 0;

            if (span != null) {
                this.start = span.start;
                this.end = span.end;
                this.span.get().gravity = gravity;
            }
        }

        public TextLine(LineSpan span, int width, int height) {
            if (span != null)
                this.span = new WeakReference<LineSpan>(span);
            this.width = width;
            this.height = height;
            if (span != null) {
                this.start = span.start;
                this.end = span.end;
            }
        }

        /* TODO: REMOVE - no effect */
        public void release() {

        }


        public String dump(char[] chars) {
            String str = new String(chars);
            return "ss=" + span.get().start + ",se=" + span.get().end + " [w/h=" + width + "/" + height + ",w=" + whitespaces + ",(s=" + start + ",e=" + end + "j=" + justifyArgument + ")]: '" + str.subSequence(start, end) + "'\n" + dumpSpans(chars);
        }


        private String dumpSpans(char[] chars) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            LineSpan current = this.span.get();
            while (current != null && current.start < end) {
                ssb.append(current.toString(chars) + "\n");
                current = current.next;
            }
            return ssb.toString();
        }

        public void justify(int width) {
            if (whitespaces > 0) {
                justifyArgument = (width - this.width - 0.5f) / whitespaces;
            }
        }


    }

    /**
     * used for drawing LeadingMarginSpan
     */

    private static class FakeSpanned implements Spanned {
        private ParagraphStyle mStyle;
        private int mPosition;

        public FakeSpanned(ParagraphStyle style, int position) {
            mStyle = style;
            mPosition = position;
        }

        @Override
        public <T> T[] getSpans(int i, int i2, Class<T> tClass) {
            return null;
        }

        @Override
        public int getSpanStart(Object o) {
            return mPosition;
        }

        @Override
        public int getSpanEnd(Object o) {
            return 0;
        }

        @Override
        public int getSpanFlags(Object o) {
            return 0;
        }

        @Override
        public int nextSpanTransition(int i, int i2, Class aClass) {
            return 0;
        }

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(int i) {
            return 0;
        }

        @Override
        public CharSequence subSequence(int i, int i2) {
            return null;
        }
    }

    /* inner class for reflow in background */

    class ReflowBackgroundTask implements Runnable {
        /* TODO: need to supports 'continue' reflow from given position and with given geometry */
        @Override
        public void run() {
            if (lineSpan == null) {
                return;
            }
            setReflowBackgroundTaskRunning(true);
            setReflowBackgroundTaskCancelled(false);
            setReflowFinished(false);
            /* default reflow listener supports multiviews reflow (with same width)*/
            runReflow(new LineSpanReflowProgressListener() {
                @Override
                public boolean onProgress(List<TextLine> lines, int height, boolean viewHeightExceed) {
                    TextLayout.this.lines = lines;
                    if (mNeedTotalHeight && listener != null) {
                        TextLayout.this.height = height;
                        notifyTextHeightChanged();
                    }
                    /* */
                    boolean finish = false;
                    if (viewHeightExceed && listener instanceof TextInfoInvalidateListenerExt) {
                        finish = !((TextInfoInvalidateListenerExt) listener).onHeightExceed(height);
                    }
                    if (isReflowBackgroundTaskCancelled()) {
                        finish = true;
                    }
                    if (finish) {
                        Log.v(TAG, "reflow background task cancelled");
                        setReflowBackgroundTaskRunning(false);
                        setReflowBackgroundTaskCancelled(false);
                        return false;
                    }
                    return true;
                }

                @Override
                public void onFinish(List<TextLine> lines, int height) {
                    TextLayout.this.lines = lines;
                    setReflowBackgroundTaskCancelled(false);
                    setReflowBackgroundTaskRunning(false);
                    setReflowFinished(true);
                    if (mNeedTotalHeight && listener != null) {
                        TextLayout.this.height = height;
                        // listener.onTextHeightChanged();
                        notifyTextHeightChanged();
                        // listener.onTextReady();
                        notifyTextReady();
                    } else if (listener != null) {
                        // listener.onTextInfoInvalidated();
                        // listener.onTextReady();
                        notifyTextReady();
                    }
                }
            });
        }
    }

    /**
     * @param line
     * @return line descent (actually = bottom - baseline)
     */
    public float getLineDescent(int line) {
        return lines.get(line).descent;
    }

    public int getLineForPosition(int position) {
        /* prevent race condition on this.lines field */
        List<TextLine> lines = this.lines;
        int result = 0;
        if (lines != null)
            for (int i = 0; i < lines.size(); i++) {
                TextLine line = lines.get(i);
                if (line.end > position && line.span != null) // need to skip fake lines with span==null
                    return i;
            }
        return -1;
    }

    /**
     * @param line
     * @return line height (bottom - top)
     */
    public float getLineHeight(int line) {
        return lines.get(line).height;
    }

    /*
    public void setImagePlacementHandler(ImagePlacementHandler imagePlacementHandler) {
        mImagePlacementHandler = imagePlacementHandler;
    } */

    /**
     * @return char sequence, actually same object, that was passed to constructor
     */
    public CharSequence getText() {
        return mText;
    }

    /**
     * @return start of working range in getText()
     */

    public int getStart() {
        return mStart;
    }

    /**
     * @return end of working range in getText()
     */
    public int getEnd() {
        return mEnd;
    }

    public LineSpan getLineSpanForPosition(int offset) {
        LineSpan span = lineSpan;
        while (span != null && span.end < offset) {
            span = span.next;
        }
        return span;
    }

    /**
     * @return paint instance
     */
    public TextPaint getPaint() {
        return paint;
    }


    /* do reflow() in background */
    private Thread mReflowBackgroundTask = null;
    private boolean mReflowBackgroundTaskRunning = false;
    private boolean mReflowBackgroundTaskCancelled = false;

    public synchronized boolean isReflowBackgroundTaskRunning() {
        return mReflowBackgroundTaskRunning;
    }

    protected synchronized boolean isReflowBackgroundTaskCancelled() {
        return mReflowBackgroundTaskCancelled;
    }

    protected synchronized void setReflowBackgroundTaskRunning(boolean running) {
        mReflowBackgroundTaskRunning = running;
    }

    protected synchronized void setReflowBackgroundTaskCancelled(boolean cancelled) {
        mReflowBackgroundTaskCancelled = cancelled;
    }

    protected synchronized void setReflowFinished(boolean finished) {
        mReflowFinished = finished;
        mIsLayouted = true;
    }

    protected synchronized boolean isReflowFinished() {
        return mReflowFinished;
    }

    /**
     * called when font size was changed
     */
    @Override
    public void invalidateMeasurement() {
        stopReflowIfNeed();
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new RuntimeException("invalidateMeasurement() must be called from UI Thread");
        invalidateLines();
        if (lineSpan != null)
            LineSpan.clearMeasurementData(lineSpan);
    }

    /**
     * called when layout geometry was changed, or some handlers replaced
     */

    @Override
    public void invalidateLines() {
        stopReflowIfNeed();
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new RuntimeException("invalidateLines() must be called from UI Thread");
        mIsLayouted = false;
        if (this.lines != null)
            lines.clear();
        this.lines = null;

    }

    /**
     * stop background thread, if running
     */

    protected void stopReflowIfNeed() {
        if (isReflowBackgroundTaskRunning()) {
            setReflowBackgroundTaskCancelled(true);
            if (mReflowBackgroundTask != null)
                try {
                    mReflowBackgroundTask.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "reflow background task join was interrupted");
                }
            Log.v(TAG, "reflow() task stopped");
        }
    }

    /**
     * main method used for calculating spans geometry with given base TextPaint, width and height limit, and so on
     *
     * @param text        - array of chars
     * @param lineStartAt - first character of line we starts
     * @param textEnd     - last available character (so, we process only slice text[lineStartAt:textEnd]
     * @param _startSpan  - if not null - will continue reflow from _startSpan (used for lasy reflow)
     * @param x           - start from given x coord
     * @param width       - available width limit in pixels
     * @param y           - start from given y coord
     * @param height      - available height limit (or -1 if no known height - i.e. for scrollable) : TODO
     * @param viewHeight  - used for lazy reflow (we reflow at least viewHeight*1.5 for performance) : TODO
     * @param paint       - base Paint
     * @param options     - options
     * @return
     */

    protected void reflow(char[] text,
                          int lineStartAt,
                          int textEnd,
                          LineSpan _startSpan,
                          float x,
                          int width,
                          int y,
                          final int height,
                          int viewHeight,
                          TextPaint paint,
                          LineSpanReflowProgressListener progress,
                          ContentView.Options options) {
        // extract options
        LineBreaker lineBreaker = options.getLineBreaker();
        float lineSpacingMultiplier = options.getLineSpacingMultiplier();
        int lineSpacingAdd = options.getmLineSpacingAdd();
        int steplimit = options.getReflowQuantize();
        boolean justification = options.isJustification();
        ImagePlacementHandler imagePlacementHandler = options.getImagePlacementHandler();

        LineSpan span = _startSpan;
        if (options == null) options = new ContentView.Options();
        List<TextLine> result = new ArrayList<TextLine>();
        TextPaint workPaint = new TextPaint();
        ParagraphStyle[] paragraphStyles = null;
        LeadingMarginSpan leadingMarginSpan = null;
        List<ReflowState> stack = new ArrayList<ReflowState>();
        long timeQuantStart = System.currentTimeMillis();
        LineSpan lastParagraphStarts = null;
        Point scale = new Point();
        Rect paddings = new Rect();
        int breakPosition = 0;
        boolean forceBreak = false;
        int[] geometry = new int[2];
        int leadingMargin = 0;
        LeadingMarginSpan actualLeadingMarginSpan = null;

        int linesAddedInParagraph = 0;

        if (width < 1) {
            if ((progress instanceof LineSpanReflowProgressListener2) && ((LineSpanReflowProgressListener2) progress).updateGeometry(geometry)) {
                width = geometry[0];
                viewHeight = geometry[1];
            } else {
                throw new IllegalArgumentException("reflow called with argument 'width' < 1");
            }
        }

        int wrapWidth = width;
        int wrapHeight = 0;
        int wrapMargin = 0;
        int wrapEnd = y;
        /**
         * reflow() supports "defer" image to next view
         */
        List<LineSpan> deffered = new ArrayList<LineSpan>();
        ReflowState state = new ReflowState(span, x);
        state.startSpan = span;
        state.carrierReturnSpan = span;
        if (span.start < lineStartAt) {
            if (span.end <= lineStartAt) {
                Log.v(TAG, "incorrect start span");
                return;
            } else {
                if (span.widths == null)
                    LineSpan.measure(span, text, workPaint, true);
                for (int i = span.start; i < lineStartAt; i++) {
                    state.processedWidth += span.widths[i - span.start];
                }
            }
            state.character = lineStartAt;
            state.lastWhitespace = lineStartAt;
        }

        LineSpan wrappedSpan = null;
        int viewHeightLeft = viewHeight;
        int collectedHeight = 0;

        /* recursion function port */
        recursion:
        while (span != null) {
            span.clearCache(true);
            long currentTime = System.currentTimeMillis();
            long timeSpent = currentTime - timeQuantStart;
            /* if we spent more times, than steplimit - execute callback */
            if (timeSpent > steplimit) {
                /* note - if viewHeightExceed == false - break loop if onProgress() returns false */
                if (!progress.onProgress(result, y + collectedHeight, false)) {
                    return;
                }
                timeQuantStart = currentTime;
            }

            int spanHeight = 0;
            int spanLeading = 0;
            int spanDescent = 0;
            leadingMargin = span.margin;

            if (span.isDrawable) {
                if (span.width == 0)
                    LineSpan.measure(span, text, workPaint);
            } else if (span.widths == null) {
                workPaint.set(paint);
                LineSpan.measure(span, text, workPaint);
            }

            // FIXME: move handling of LeadingMarginSpan2 from draw here

            if (span.paragraphStyles != paragraphStyles) {
                leadingMarginSpan = null;
                if (span.paragraphStyles != null)
                    for (ParagraphStyle paragraphStyle : span.paragraphStyles) {
                        if (paragraphStyle instanceof LeadingMarginSpan) {
                            leadingMarginSpan = (LeadingMarginSpan) paragraphStyle;
                            if (leadingMarginSpan != actualLeadingMarginSpan) {
                                actualLeadingMarginSpan = leadingMarginSpan;
                            }
                        }
                    }
                paragraphStyles = span.paragraphStyles;
            }

            if (!span.isDrawable) {
                spanLeading = (int) span.leading;
                spanHeight = (int) (span.height + span.descent + (spanLeading * lineSpacingMultiplier) + lineSpacingAdd);
                spanDescent = span.descent;
            }

            if (spanHeight > state.height) state.height = spanHeight;
            if (spanLeading > state.leading) state.leading = spanLeading;
            if (spanDescent > state.descent) state.descent = spanDescent;

            if (!span.isDrawable && height > -1 && (y + state.height > height)) {
                Log.v(TAG, "1 break recursion at: " + state.character + " isDrawable = " + span.isDrawable);
                break recursion;
            }

            int lineMargin = leadingMargin;

            if (state.lineWidth == 0f && (leadingMargin > 0 || span.paragraphStart)) {
                lineMargin = leadingMargin;
                if (span.paragraphStart && span != lastParagraphStarts) {
                    lastParagraphStarts = span;
                    lineMargin += span.paragraphStartMargin;
                    state.height += span.paragraphTopMargin;
                }
                state.lineWidth = lineMargin;
            }

            /* flag */
            boolean drawableScaleBreak = false;

            // begin handle span
            /* forceBreak required if we revert char sequence due to hyphenation/linebreak algorygthm */
            processing:
            while (state.character < span.end || forceBreak) {
                if (state.skipWhitespaces) { // handle skip whitespaces
                    state.doSkipWhitespaces(text);
                    if (lineStartAt < state.character) // skipWhitespaces must works only at lineStart, so actually, this code execute after line ends
                        lineStartAt = state.character;
                    forceBreak = false; // cancel forcebreak
                    // restore heights and checking if new line exeed view Height
                    if (state.character < span.end) { // guard
                        // recover spanHeight
                        if (!span.isDrawable) {
                            spanLeading = (int) span.leading;
                            spanHeight = (int) (span.height + span.descent + (spanLeading * lineSpacingMultiplier) + lineSpacingAdd);
                            spanDescent = span.descent;
                        }

                        if (spanHeight > state.height) state.height = spanHeight;
                        if (spanLeading > state.leading) state.leading = spanLeading;
                        if (spanDescent > state.descent) state.descent = spanDescent;
                        // check if switched line exceed view height (height defined and collected height exceed)
                        if (height > -1 && collectedHeight + y + state.height > height) {
                            Log.v(TAG, "2 break recursion at: " + state.character + " isDrawable = " + span.isDrawable);
                            break recursion;
                        }
                        if (viewHeightLeft - state.height < 0 && !span.isDrawable) {
                            if (viewHeight > -1 && !progress.onProgress(result, y, true)) {
                                Log.v(TAG, "3 break recursion at viewHeightExceed (onProgress returns true)");
                                break recursion;
                            } else if (viewHeight > -1 && progress instanceof LineSpanReflowProgressListener2) {
                                /* special case - ask geometry for next portion */
                                if (((LineSpanReflowProgressListener2) progress).updateGeometry(geometry)) {
                                    width = geometry[0];
                                    viewHeight = geometry[1];
                                    Log.v(TAG, "change geometry to : width=" + width + ", viewHeight=" + viewHeight);
                                }
                            }

                            if (viewHeight > -1) { // TODO: reorganize conditions
                                collectedHeight += y;
                                y = 0;  // y zeroed only if viewHeight greater than -1
                                viewHeightLeft = viewHeight;
                                wrapWidth = width;
                                wrapHeight = 0;
                                wrapMargin = 0;
                            }

                            /* handle deffered images */
                            if (deffered.size() > 0) {
                                LineSpan defferedSpan = deffered.remove(0);
                                DynamicDrawableSpan defferedDrawableSpan = defferedSpan.getDrawable();
                                int placement = imagePlacementHandler.place(defferedDrawableSpan, viewHeightLeft, width, width, -1, scale, paddings, false);
                                int gravity = LineSpan.imageAlignmentToGravity(ImagePlacementHandler.getAlignment(placement));

                                if (imagePlacementHandler.isWrapText(placement)) {
                                    wrappedSpan = defferedSpan;
                                    wrapHeight = scale.y + paddings.top + paddings.bottom;
                                /* need to store y+wrapHeight as minimum height for layout */
                                    wrapEnd = collectedHeight + y + wrapHeight;

                                    int paddingWidth = paddings.right + paddings.left;
                                    wrapWidth = width - scale.x - paddingWidth;

                                    if (gravity == Gravity.LEFT)
                                        wrapMargin = width - wrapWidth;

                                    result.add(new TextLine(defferedSpan, scale.y, scale.x + paddingWidth, gravity));

                                    // if (scale.x+paddingWidth+state.lineWidth>)
                                    lineStartAt = state.character + 1;
                                } else if (imagePlacementHandler.isNewLineAfter(placement)) {
                                    TextLine ld;

                                    if (state.height < scale.y) {
                                        state.height = scale.y + paddings.top + paddings.bottom;
                                    }

                                    y += state.height;
                                    viewHeightLeft -= state.height;

                                    if (height > -1 && y > height - 1) {
                                        Log.v(TAG, "9 break recursion height=" + height + ", y=" + y);
                                        break recursion;
                                    }

                                    ld = new TextLine(defferedSpan, scale.x, scale.y);
                                    ld.margin = lineMargin;
                                    result.add(ld);
                                } else {
                                    Log.w(TAG, "unsupported placement for deffered image: " + placement);
                                    continue processing;
                                }
                                defferedSpan.drawableScaledWidth = scale.x;
                                defferedSpan.drawableScaledHeight = scale.y;
                                Drawable dds = defferedDrawableSpan.getDrawable();
                                if (dds != null) {
                                    dds.setBounds(paddings.left, paddings.top, paddings.left + scale.x, paddings.top + scale.y);
                                } else {
                                    Log.w(TAG, "no drawable on DynamicDrawableSpan: " + defferedDrawableSpan);
                                }
                            } // END HANDLING DEFFERED IMAGES
                        }
                    }
                    continue processing;
                } // end handle whitespaces block
                // view line width exceed or force break
                if ((forceBreak || state.lineWidth + span.widths[state.character - span.start] > wrapWidth) && !drawableScaleBreak) {

                    if (span.isDrawable) {
                        drawableScaleBreak = true;
                        continue processing;
                    }
                    if (state.character <= lineStartAt) {
                        Log.v(TAG, "line exceed at first character");
                    }
                    // TODO: need special processing for large images (scaling, depends on ImagePlacementHandler response)
                    // FIXME: use lineBreakVal transfer instead breakPosition (we lost hyphen flag)
                    int lineBreakVal = forceBreak ? breakPosition : lineBreaker.nearestLineBreak(text, state.lastWhitespace, state.character - 1, span.end);
                    breakPosition = LineBreaker.getPosition(lineBreakVal);
                    if (state.character == state.lastWhitespace) {
                        Log.w(TAG, "first character on line does not fit to given width");
                        breakPosition = state.character;
                    }
                    boolean hyphen = LineBreaker.isHyphen(lineBreakVal);
                    if (breakPosition < state.lastWhitespace) {
                        Log.e(TAG, "possible crash in future: error in lineBreaker:(" + lineBreaker + ") - nearestLineBreak(text,start,end,limit) must returns value, that greater or equal start");
                    }
                    if (breakPosition <= lineStartAt) { // in some cases breakPosition<lineStartAt
                        breakPosition = state.character - 1;
                        Log.e(TAG, "lineBreaker (" + lineBreaker + ") returns breakPosition before lineStarts");
                    }
                    forceBreak = false; // cancel force break

                    /* check if breakPosition < span.start (rollback to previous state) */
                    if (breakPosition < span.start) {
                        span.breakFirst = null;
                        state = stack.remove(0);
                        span = state.span;
                        forceBreak = true;
                        continue processing;
                    }

                    /* force close line code - test, if we can move it to procedure */
                    // local variables used :(

                    TextLine ld;
                    boolean isWhitespace = text[breakPosition] == ' ';

                    state.rollback(breakPosition);

                    y += state.height;
                    ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                    ld.margin = lineMargin + wrapMargin;
                    ld.wrapMargin = wrapMargin;
                    result.add(ld);

                    state.breakLine(isWhitespace, ld); // nb: breakLine does not increment state.character

                    if (justification)
                        ld.justify(wrapWidth);

                    /* handle exceed view height */
                    viewHeightLeft -= ld.height;
                    /* handle wrap ends */
                    wrapHeight -= ld.height;
                    /* check if wrap around drawable finished */
                    if (wrapWidth < width && wrapHeight < 1) {
                        /* adjust vertical wrapped image position here */
                        if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                            int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                            wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight);
                            wrappedSpan = null;
                        }

                        wrapWidth = width;
                        wrapMargin = 0;
                    }

                    if (span.paragraphStart && lastParagraphStarts == span && lineMargin > span.margin) {
                        lineMargin = span.margin;
                    }

                    if (lineMargin > 0 || span.paragraphStart) {
                        state.lineWidth = lineMargin;
                    }
                    state.character++; // nb: breakline does not increment state.character
                    state.skipWhitespaces = true;
                    lineStartAt = state.character;
                    linesAddedInParagraph++;
                } else if (text[state.character] == '\n') {
                    // handle force carrier return
                    state.processedWidth += span.widths[state.character - span.start];
                    /* eliminate empty line only if non-empty-lines-count > threshold */
                    if (options.isFilterEmptyLines() && state.character == lineStartAt && linesAddedInParagraph < options.getEmptyLinesThreshold()) {
                        TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                        state.carrierReturn(ld);
                        linesAddedInParagraph = 0;
                    } else {
                        TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                        if (options.isFilterEmptyLines() && state.character == lineStartAt) {
                            linesAddedInParagraph = 0;
                            ld.height = options.getEmptyLineHeightLimit();
                        } else
                            linesAddedInParagraph++;
                        y += ld.height;
                        result.add(ld);
                        ld.margin = lineMargin + wrapMargin;
                        ld.wrapMargin = wrapMargin;
                        ld.wrapMargin = wrapMargin;
                        state.carrierReturn(ld);
                        /* handle exceed view height */
                        viewHeightLeft -= ld.height;
                        /* handle wrap ends */
                        wrapHeight -= ld.height; // maybe after carrier return wrap margin must be resets?
                        if (wrapWidth < width && wrapHeight < 1) {

                            if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                                int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                                wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight);
                                wrappedSpan = null;
                            }

                            wrapWidth = width;
                            wrapMargin = 0;
                            /* FIXME: adjust vertical wrapped image position here ? */
                        }


                    }

                    if (lineMargin > 0 || span.paragraphStart) {
                        state.lineWidth = lineMargin;
                    }

                    lineStartAt = state.character;
                    state.skipWhitespaces = true;
                } else if (text[state.character] == 65532) { // 65532 - OBJECT REPLACEMENT CHAR

                    state.lastWhitespace = state.character; // force object replacement character as last whitespace
                    drawableScaleBreak = false;
                    forceBreak = false;

                    boolean collectHeights = true;
                    DynamicDrawableSpan dds = span.getDrawable();

                    if (imagePlacementHandler != null) { // if we have image placement handler
                        if (dds != null) {
                            float leftWidth = width - state.lineWidth;
                            int placement = imagePlacementHandler.place(dds, height > 0 ? ((int) (height - y - 1)) : viewHeightLeft, (int) (leftWidth), width, (int) x, scale, paddings, viewHeight > 0);
                            if (scale.x + paddings.left + paddings.top > leftWidth) {
                                Log.w(TAG, "error in imagePlacementHandler = scale.x (width paddings) > width (" + scale.x + " > " + leftWidth + ")");
                                // Drawable drawable = dds.getDrawable();
                                float __width = scale.x;
                                float __height = scale.y;
                                float __ratio = __height / __width;
                                scale.x = (int) leftWidth - paddings.left - paddings.right;
                                scale.y = (int) (__ratio * scale.x);
                                Log.v(TAG, "fixed x=" + scale.x + ", y=" + scale.y);
                            }
                            if (placement == ImagePlacementHandler.DEFER) {
                                deffered.add(span);
                                if (state.character == lineStartAt) {
                                    lineStartAt++;
                                }
                                state.character++;
                                continue processing;
                            }
                            boolean finishLine = false;
                            if (ImagePlacementHandler.isNewLineBefore(placement) || lineStartAt == state.character ||
                                    (ImagePlacementHandler.isWrapText(placement) && wrapHeight > 0)) {
                                finishLine = true;
                            }

                            if (finishLine) {
                                if (lineStartAt < state.character) {
                                    TextLine ld;
                                    y += state.height;
                                    viewHeightLeft -= state.height;

                                    if (height > -1 && y > height - 1) {
                                        Log.v(TAG, "4 break recursion while finish line");
                                        break recursion;
                                    }

                                    ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                                    ld.margin = lineMargin + wrapMargin;
                                    ld.wrapMargin = wrapMargin;
                                    ld.end--;
                                    result.add(ld);
                                    state.carrierReturn(ld);
                                    state.character--; // correct shifted by carrierReturn() position;
                                    // WARN: do not execute justification on line before images
                                    lineStartAt = state.character;
                                /* handle wrap ends */
                                    wrapHeight -= ld.height;
                                } else if (lineStartAt == state.character) {
                                    if (wrapHeight > 0) {
                                        result.add(new TextLine(null, 0, wrapHeight));   // TODO: need correctly handle in draw()
                                        wrapHeight = 0;
                                        wrapMargin = 0;
                                    }
                                }
                            }

                            int gravity = LineSpan.imageAlignmentToGravity(ImagePlacementHandler.getAlignment(placement));

                            if (ImagePlacementHandler.isWrapText(placement) && scale.x < leftWidth) {
                                collectHeights = false;
                                if (wrapHeight > 0) {
                                    // before, we must close active wrap
                                    result.add(new TextLine(null, 0, wrapHeight));
                                    /**
                                     * need correctly handle case, if current line calculated for wrap, and looks like
                                     * 'ccccccI', where ccccc - collected characters, and I - current DynamicDrawableSpan
                                     *
                                     * if previous wrapWidth may be less, than wrapWidth for current DynamicDrawableSpan - it may be
                                     * error in ImagePlacementHandler, if 'cccccI' in wrapped mode does not fit 'width' of view
                                     * - need check, force close 'ccccc' and starts 'I' with new line
                                     *
                                     */
                                    wrapMargin = 0;
                                    /*
                                        closing algorythm:
                                            close current line (move under 'if ( .isWrapText()) condition
                                            if wrapHeight > 0 - create fake line with height = wrapHeight
                                     */
                                } /* wrapHeight closed */
                                wrappedSpan = span;
                                wrapHeight = scale.y + paddings.top + paddings.bottom;
                                /* need to store y+wrapHeight as minimum height for layout */
                                wrapEnd = collectedHeight + y + wrapHeight;

                                int paddingWidth = paddings.right + paddings.left;
                                wrapWidth = width - scale.x - paddingWidth;

                                if (gravity == Gravity.LEFT)
                                    wrapMargin = width - wrapWidth;

                                result.add(new TextLine(span, scale.y, scale.x + paddingWidth, gravity));

                                // if (scale.x+paddingWidth+state.lineWidth>)
                                lineStartAt = state.character + 1;
                            } else if (imagePlacementHandler.isWrapText(placement)) {
                                Log.v(TAG, "processed last!");
                                // image at the end of line, which is stars wrap,
                                // so - close line, shift lineStartsAt and set values for wrapWidth etc
                                int paddingWidth = paddings.right + paddings.left;
                                result.add(new TextLine(span, scale.y, scale.x + paddingWidth, gravity));
                                // add fake image 'line'

                                TextLine ld;
                                ld = new TextLine(state, lineStartAt, leadingMarginSpan);

                                wrapWidth = width - scale.x - paddingWidth;

                                if (gravity == Gravity.LEFT)
                                    wrapMargin = width - wrapWidth;

                                ld.margin = lineMargin + wrapMargin;
                                ld.wrapMargin = wrapMargin;
                                ld.end--;
                                result.add(ld);
                                state.carrierReturn(ld); // finish line before image
                                lineStartAt = state.character + 1;
                                wrappedSpan = span;
                                wrapHeight = scale.y + paddings.top + paddings.bottom;
                                /* need to store y+wrapHeight as minimum height for layout */
                                wrapEnd = collectedHeight + y + wrapHeight;
                            } else {
                                if (ImagePlacementHandler.isNewLineAfter(placement)) {
                                    TextLine ld;

                                    if (state.height < scale.y) {
                                        state.height = scale.y + paddings.top + paddings.bottom;
                                    }

                                    y += state.height;
                                    viewHeightLeft -= state.height;

                                    if (height > -1 && y > height - 1) {
                                        Log.v(TAG, "6 break recursion height=" + height + ", y=" + y);
                                        break recursion;
                                    }

                                    ld = new TextLine(span, scale.x, scale.y);
                                    ld.margin = lineMargin;
                                    result.add(ld);
                                    state.carrierReturn(ld);
                                    ld.end = span.end;
                                    lineStartAt = state.character;
                                    state.skipWhitespaces = true;
                                } else {
                                    Log.v(TAG, "inline image placement?");
                                }
                            }

                            int baseLineShift = 0;

                            if (span.next != null && !ImagePlacementHandler.isNewLineAfter(placement)) {
                                baseLineShift = (span.next.height - span.next.descent) / 2;
                            }

                            span.drawableScaledWidth = scale.x;
                            span.drawableScaledHeight = scale.y;
                            Drawable drawable = dds.getDrawable();
                            if (drawable != null) {
                                drawable.setBounds(paddings.left, paddings.top + baseLineShift, paddings.left + scale.x, paddings.top + scale.y + baseLineShift);
                            }

                        } else {
                            // dds is null, skip
                            Log.w(TAG, "dynamic drawable span are null");
                        }
                        state.character++;
                    } else {
                        // default inline scaling behavior
                        // there 'inline' scaling to fit available place example
                        float leftWidth = width - state.lineWidth;

                        if (span.width > leftWidth) {
                            float ratio = span.height / span.width;
                            float nH = leftWidth * ratio;
                            span.drawableScaledHeight = nH;
                            span.drawableScaledWidth = leftWidth;

                            if (height < 0) {
                                if (viewHeightLeft > 0) {
                                    // limited view height
                                    if (span.drawableScaledHeight > viewHeightLeft) {

                                    }
                                } else {
                                    // unlimited view height, does not correct
                                }
                            }

                            spanHeight = (int) span.drawableScaledHeight;
                        } else { // default images autoscale
                            if (height < 0) {
                                if (viewHeightLeft > 0) {
                                    // limited view height
                                    if (span.height > viewHeightLeft) {
                                        // do we need break here if scale too small ?

                                    } else {
                                        span.drawableScaledHeight = span.height;
                                        span.drawableScaledWidth = span.width;
                                    }
                                } else {
                                    // unlimited view height
                                    span.drawableScaledHeight = span.height;
                                    span.drawableScaledWidth = span.width;
                                }
                            }
                            spanHeight = span.height;
                        }
                        // deffered height calculation

                        Drawable drawable = dds.getDrawable();
                        if (drawable != null) {
                            drawable.setBounds(0, 0, (int) span.drawableScaledWidth, (int) span.drawableScaledHeight);
                        }

                        state.character++;
                    }
                    if (collectHeights) {
                        if (spanHeight > state.height) state.height = spanHeight;
                        if (spanLeading > state.leading) state.leading = spanLeading;
                        if (spanDescent > state.descent) state.descent = spanDescent;
                    }
                    // handle object replacement char
                    // state.increaseBreak(span.widths[state.character - span.start]);
                } else if (!lineBreaker.isLetter(text[state.character])) {
                    // handle non-letter characters (m.b. only spaces?)
                    state.nonLetterBreak(text[state.character] != ' ' || span.strong);
                } else if (text[state.character] == '\u200F') { // RTL MARK
                    Log.v(TAG, "rtl mark!");
                    /**
                     * make current span direction RTL (if not)
                     * and make state.direction = RTL
                     */
                    state.character++;
                } else if (text[state.character] == '\u200E') { // LTR mark
                    Log.v(TAG, "ltr mark!");
                    state.character++;
                } else {
                    // handle usual character
                    if (span.widths == null) {
                        Log.v(TAG, "span widths are null: " + span);
                    }
                    state.increaseBreak(span.widths[state.character - span.start]);
                    state.character++;
                }
            }
            // end handle span (

            if (state.lineWidth == lineMargin) // cancel margin if only margin at new line
                state.lineWidth = 0f;
            // handle span switch
            if (state.skipWhitespaces) {
                state.processedWidth = state.span.width;
            } else {

            }
            stack.add(0, state.finish());
            if (span.next == null) {
                Log.v(TAG, "break recursion at the end of chain");
                break recursion;
            }
            state = new ReflowState(state, span.next);
            span = span.next;

            if (span != null && span.breakFirst != null) {
                Log.v(TAG, "clean underflow breakFirst");
                span.breakFirst = null;
            }
        }
        // handle tail of line
        state.character--;
        TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);

        if (ld.end <= textEnd && (height < 1 || y + ld.height < height + 1)) {
            result.add(ld);
        } else
            state.height = 0;
        if (progress != null) {
            Log.v(TAG, "call on Finish()");
            progress.onFinish(result, (y + collectedHeight + state.height) > wrapEnd ? (y + state.height) : wrapEnd);
        }
    }


    /**
     * paint early prepared List&lt;LineDescription&gt; on canvas
     *
     * @param lines          - list of LineDescription
     * @param startLine
     * @param text           - text (array of chars)
     * @param canvas         - canvas for painting
     * @param paint          - textpaint
     * @param selectionStart
     * @param selectionEnd
     * @param selectionColor
     * @param highlightStart - begin of selection
     * @param highlightEnd   - end of selection
     * @param highlightColor - background color for selection
     */

    Paint greenPaint = new Paint();
    Paint redPaint = new Paint();
    Paint blackPaint = new Paint();
    Paint bluePaint = new Paint();
    TextPaint workPaint = new TextPaint();
    Paint highlightPaint = new Paint();
    Paint selectionPaint = new Paint();

    public int draw(List<TextLine> lines, int startLine, char[] text, Canvas canvas, float width, float height, TextPaint paint, int selectionStart, int selectionEnd, int selectionColor, int highlightStart, int highlightEnd, int highlightColor, boolean justification) {
        if (lines == null || lines.size() < 1) {
            Log.w(TAG, "lines==null || lines.size() < 1");
            return 0;
        }
        Rect clipRect = canvas.getClipBounds();

        greenPaint.setColor(Color.GREEN);
        greenPaint.setStyle(Paint.Style.STROKE);
        redPaint.setColor(Color.RED);
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStyle(Paint.Style.STROKE);
        blackPaint.setStrokeWidth(2f);
        blackPaint.setColor(Color.MAGENTA);
        // String textStr = new String(text);
        highlightPaint.setColor(highlightColor);
        selectionPaint.setColor(selectionColor);
        // selectionPaint.setXfermode(xorMode);
        int i = startLine;
        int y = 0;

        boolean highlight = false;
        boolean selection = false;
        TextLayout.TextLine line = lines.get(startLine);

        LeadingMarginSpan actualLeadingMargin = null;
        while ((y + line.height < clipRect.top) && (y + line.wrapHeight < clipRect.top)) {
            y += line.height;
            i++;
            if (i < lines.size())
                line = lines.get(i);
            else
                break;
        }

        if (selectionStart < selectionEnd)
            selection = true;
        if (highlightStart < highlightEnd)
            highlight = true;

        CharacterStyle[] styles = null;
        linesLoop:
        for (; i < lines.size() && y < clipRect.bottom; i++) {

            line = lines.get(i);
            if (height > 0 && y + line.height > clipRect.bottom && line.wrapHeight < 1) {
                break linesLoop;
            }
            if (line.span == null) { // special case uses for closing image wrap
                y += line.height;
                continue;
            }
            int drawStart = line.start;
            int drawStop = line.end;
            if (drawStop <= drawStart && line.height > 0 && line.span.get().drawableScaledWidth < 1) {
                y += line.height;
                if (line.span.get().isDrawable) {
                    Log.v(TAG, "skip line with drawable O_o");
                }
                continue;
            }


            float selectStartX = 0f;
            float selectEndX = 0f;

            boolean findSelectionStartX = false;
            boolean findSelectionEndX = false;

            float highlightStartX = 0f;
            float highlightEndX = 0f;

            boolean findHighlightStartX = false;
            boolean findHighlightEndX = false;

            boolean drawHighlight = false;
            boolean drawSelection = false;

            if (selection && (selectionStart < line.end && selectionEnd > line.start)) {
                if (line.start >= selectionStart && line.end < selectionEnd) {
                /* fill entire background */
                    selectEndX = line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (selectionStart < line.start && selectionEnd < line.end) {
                /* selection starts on previous line, but ends on current line */
                    findSelectionEndX = true;
                } else if (selectionStart >= line.start && selectionEnd > line.end) {
                /* selection starts on current line, but ends on next line or below */
                    findSelectionStartX = true;
                    selectEndX = line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (selectionStart >= line.start && selectionEnd < line.end) {
                /* selection starts and ends on current line */
                    findSelectionStartX = true;
                    findSelectionEndX = true;
                }
                drawSelection = true;
            }

            if (highlight && (highlightStart < line.end && highlightEnd > line.start)) {
                if (line.start >= highlightStart && line.end <= highlightEnd) {
                /* fill entire background */
                    highlightEndX = line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (highlightStart <= line.start && highlightEnd <= line.end) {
                /* highlight starts on previous line, but ends on current line */
                    findHighlightEndX = true;
                } else if (highlightStart >= line.start && highlightEnd > line.end) {
                /* highlight starts on current line, but ends on next line or below */
                    findHighlightStartX = true;
                    highlightEndX = line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (highlightStart >= line.start && highlightEnd <= line.end) {
                /* highlight starts and ends on current line */
                    findHighlightStartX = true;
                    findHighlightEndX = true;
                }
                // Log.v(TAG, "find highlight startX = " + findHighlightStartX + ", endX = " + findHighlightEndX + " (" + highlightStart + "," + highlightEnd + ") on line [" + line.start + "," + line.end + "]");
                drawHighlight = true;
            }

            int baseLine = y + line.height - line.descent;
            float tail = line.afterBreak == null ? 0f : line.afterBreak.get().tail;
            LineSpan span = line.span.get();
            LineSpanBreak lineSpanBreak = line.afterBreak == null ? span.breakFirst : (line.afterBreak.get().next == null ? null : line.afterBreak.get().next);
            float x = 0f;
            int skip = line.afterBreak == null ? 0 : line.afterBreak.get().skip;

            if (line.leadingMargin != null) {
                if (line.leadingMargin != actualLeadingMargin) {
                    actualLeadingMargin = line.leadingMargin;
                }
                try {
                    line.leadingMargin.drawLeadingMargin(canvas, workPaint, line.wrapMargin, 1, y, baseLine, baseLine + line.descent, new FakeSpanned(line.leadingMargin, drawStart), drawStart, drawStop, false, null);
                } catch (NullPointerException e) {
                    // avoid unsupported leading margins exceptions, caused layout==null
                }
            } else {
                actualLeadingMargin = null;
            }

            float align = 0f;

            if (line.gravity != Gravity.NO_GRAVITY) {
                if (line.gravity == Gravity.RIGHT) {
                    align = width - line.width;
                } else if (line.gravity == Gravity.CENTER_HORIZONTAL) {
                    align = (width / 2) - (line.width / 2);
                }
            }

            if (drawSelection) {
                if (findSelectionStartX)
                    selectStartX = calculateOffset(line.span.get(), line.start, selectionStart, (justification ? line.justifyArgument : 0)) + line.margin + align;
                if (findSelectionEndX)
                    selectEndX = calculateOffset(line.span.get(), line.start, selectionEnd, (justification ? line.justifyArgument : 0)) + line.margin + align;
                canvas.drawRect(selectStartX + align, y, selectEndX + align, y + line.height, selectionPaint);
            }

            x = line.margin + align;
            drawStart += skip;
            drawline:
            while (span != null && drawStart < line.end) {
                drawStop = span.end < line.end ? span.end : line.end;
                if (span.isDrawable) {

                    // TODO: rewrite to handle gravity and wrap
                    for (CharacterStyle style : span.spans)
                        if (style instanceof DynamicDrawableSpan) {
                            DynamicDrawableSpan dds = (DynamicDrawableSpan) style;
                            float drawableWidth = 0f;

                            if (span.gravity == Gravity.RIGHT) {
                                x = width - line.wrapWidth;
                            } else if (span.gravity == Gravity.CENTER_HORIZONTAL) {
                                x += (width - x) / 2 - span.drawableScaledWidth / 2;
                            }

                            if (span.drawableScaledWidth > 0f) {
                                dds.draw(canvas, null, span.start, span.end, x, y + span.baselineShift, (int) (y + span.baselineShift + span.drawableScaledHeight), (int) (y + span.baselineShift + span.drawableScaledHeight + span.leading), workPaint);
                                drawableWidth = span.drawableScaledWidth;
                            } else {
                                // drawable.setBounds(0, 0, (int) (span.width), (int) (span.height));
                                dds.draw(canvas, null, span.start, span.end, x, y + span.baselineShift, y + span.baselineShift + span.height, (int) (y + span.baselineShift + span.height + span.leading), workPaint);
                                drawableWidth = span.width;
                            }

                            x += drawableWidth;
                            break;
                        }
                    span = span.next;
                    lineSpanBreak = span.breakFirst;
                    drawStart = span == null ? 0 : span.start;
                    continue drawline;
                }

                if (span.spans != null && span.spans != styles) {
                    workPaint.set(paint);
                    for (CharacterStyle style : span.spans) {
                        style.updateDrawState(workPaint);
                    }
                    styles = span.spans;
                }

                while (lineSpanBreak != null) {
                    drawStop = lineSpanBreak.position + 1;
                    drawStop = drawStop > line.end ? line.end : drawStop;
                    if (drawStart < drawStop) {
                        canvas.drawText(text, drawStart, drawStop - drawStart, x, baseLine, workPaint);
                    } else {
                        // Log.v(TAG,"oops");
                    }
                    drawStart = drawStop;
                    x += lineSpanBreak.width;

                    if (!lineSpanBreak.strong && justification)
                        x += line.justifyArgument;

                    if (lineSpanBreak.carrierReturn) {
                        break drawline;
                    }

                    tail = lineSpanBreak.tail;
                    skip = lineSpanBreak.skip;

                    lineSpanBreak = lineSpanBreak.next;
                }

                if (tail > 0f) {
                    drawStop = line.end < span.end ? line.end : span.end;
                    if (drawStart < drawStop)
                        canvas.drawText(text, drawStart, drawStop - drawStart, x, baseLine, workPaint);
                    x += tail;
                }
                span = span.next;
                if (span != null) {
                    tail = span.width;
                    lineSpanBreak = span.breakFirst;
                    drawStart = span.start + skip;
                }
            }

            if (drawHighlight) {
                if (findHighlightStartX)
                    highlightStartX = calculateOffset(line.span.get(), line.start, highlightStart, (justification ? line.justifyArgument : 0)) + line.margin;
                if (findHighlightEndX)
                    highlightEndX = calculateOffset(line.span.get(), line.start, highlightEnd, (justification ? line.justifyArgument : 0)) + line.margin;
                canvas.drawRect(highlightStartX + align, y, highlightEndX + align, y + line.height, highlightPaint);
            }


            y += line.height;
            //if (y > clipRect.bottom) break linesLoop;
        }
        return 0;
    }

    /**
     * @param span
     * @param from
     * @param character
     * @param aa
     * @return correct offset in pixels from left border
     * <p/>
     * TODO: more work for bidirectional text support
     */

    private static float calculateOffset(LineSpan span, int from, int character, float aa) {
        return getCharacterOffset(span, from, character) + aa * getSoftBreaks(span, from, character);
    }

    /**
     * @param span
     * @param from
     * @param character
     * @return character offset from line start (without justification)
     */

    private static float getCharacterOffset(LineSpan span, int from, int character) {
        float result = 0f;
        for (int i = from; i < character; i++) {
            if (i > span.end - 1) {
                span = span.next;
                if (span == null) break;
            }
            result += span.widths[i - span.start];
        }
        return result;
    }

    /**
     * @param span
     * @param from
     * @param character
     * @return count of 'soft' breaks for line
     */

    private static int getSoftBreaks(LineSpan span, int from, int character) {
        LineSpanBreak lineBreak = span.breakFirst;
        int result = 0;
        while (lineBreak != null && lineBreak.position < from) lineBreak = lineBreak.next;
        while (span != null && span.start < character) {
            while (lineBreak != null && lineBreak.position < character) {
                if (!lineBreak.strong)
                    result++;
                if (lineBreak.carrierReturn)
                    return result;
                lineBreak = lineBreak.next;
            }

            span = span.next;
            if (span != null)
                lineBreak = span.breakFirst;
        }
        return result;
    }

    public static int getPrimaryHorizontal(TextLayout.TextLine line, int atX, float viewWidth) {
        // similar to DRAW, but no actual paint - just determine character position for given x coordinate

        int drawStart = line.start;
        int drawStop = line.end;
        int resultChar = line.start;

        if (line.afterBreak != null) {
            // assert(drawStart==line.afterBreak.position+line.afterBreak.skip);
        }

        float tail = line.afterBreak == null ? 0f : line.afterBreak.get().tail;
        LineSpan span;
        LineSpanBreak lineSpanBreak = null;
        if (line.span != null) {
            span = line.span.get();
            lineSpanBreak = line.afterBreak == null ? span.breakFirst : (line.afterBreak.get().next == null ? null : line.afterBreak.get().next);
        } else {
            Log.e(TAG, "empty weak ref!");
            return line.start;
        }
        float x = line.margin;
        int skip = line.afterBreak == null ? 0 : line.afterBreak.get().skip;

        // shift atX by gravity
        if (line.gravity != Gravity.NO_GRAVITY) {
            if (line.gravity == Gravity.RIGHT) {
                atX -= viewWidth - line.width;
            } else if (line.gravity == Gravity.CENTER_HORIZONTAL) {
                atX -= (viewWidth / 2) - (line.width / 2);
            }
        }

        if (x > atX)
            return line.start; // all space before first character belong to first character

        drawline:
        while (span != null && drawStart < line.end) {

            drawStop = span.end < line.end ? span.end : line.end;
            if (span.isDrawable) {
                if (atX > x && atX <= (x + span.width)) return span.start;
                x += span.drawableScaledWidth;
                span = span.next;
                tail = 0f;
                continue drawline;
            }

            while (lineSpanBreak != null) {
                drawStop = lineSpanBreak.position + 1;
                drawStop = drawStop > line.end ? line.end : drawStop;
                if (drawStart < drawStop) {
                    // walk trough span.widths ?
                    for (int i = drawStart; i < drawStop; i++) {
                        float width = span.widths[i - span.start];
                        if (x + width > atX) return i;
                        x += width;
                    }
                } else {
                    // Log.v(TAG,"oops");
                }
                drawStart = drawStop;
                resultChar = drawStart;
                // x += lineSpanBreak.width;

                if (!lineSpanBreak.strong) {
                    if (x + line.justifyArgument > atX)
                        return lineSpanBreak.position;
                    x += line.justifyArgument;
                }
                if (lineSpanBreak.carrierReturn) {
                    // finish lookup character at line
                    return line.end - 1; // return last character on line
                    // break drawline;
                }

                tail = lineSpanBreak.tail;
                skip = lineSpanBreak.skip;

                lineSpanBreak = lineSpanBreak.next;
            }

            if (tail > 0f) {
                drawStop = line.end < span.end ? line.end : span.end;
                if (drawStart < drawStop) {
                    for (int i = drawStart; i < drawStop; i++) {
                        float width = span.widths[i - span.start];
                        if (x + width > atX) return i;
                        x += width;
                    }
                }
                // x += tail;
            }
            span = span.next;
            if (span != null) {
                tail = span.width;
                lineSpanBreak = span.breakFirst;
                drawStart = span.start + skip;
                if (drawStart < span.start) {
                    Log.v(TAG, "wut?");
                }
                resultChar = drawStart;
            }
        }

        return resultChar;
    }

    public static int getCharacterOffsetX(TextLayout.TextLine textLine, int position, boolean justification, float viewWidth) {
        float align = 0f;
        if (textLine.gravity != Gravity.NO_GRAVITY) {
            if (textLine.gravity == Gravity.RIGHT) {
                align = viewWidth - textLine.width;
            } else if (textLine.gravity == Gravity.CENTER_HORIZONTAL) {
                align = (viewWidth / 2) - (textLine.width / 2);
            }
        }
        return (int) (align + calculateOffset(textLine.span.get(), textLine.start, position, justification ? textLine.justifyArgument : 0) + textLine.margin);
    }

    public class Options extends ContentView.Options {
        private ContentView.Options mParent;
    }
}
