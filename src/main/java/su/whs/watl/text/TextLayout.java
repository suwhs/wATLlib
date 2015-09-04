package su.whs.watl.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import su.whs.watl.experimental.LazyDrawable;

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
    /* */
    private boolean debugDraw = false;
    private boolean debug = false;
    private static char[] mHyphenChar = new char[] { '-' };
    private Spanned mText;
    private int mParagraphStartMargin = 0;
    private int mParagraphTopMargin = 0;
    private int mJustificationTreshold = 1;
    /* time interval, used in LineSpan.reflow() for call onProgress()  */

    private static final String TAG = "TextLayout";
    protected char[] chars; /* for performance - we need direct access to text as array of chars */
    protected LineSpan lineSpan;
    protected int width;              //
    protected int reflowedWidth = -1;
    protected int height = -1;
    protected int reflowedHeight = -1;
    protected int requestedHeight = -1;
    private float reflowedTextSize = -1;

    protected TextLayoutListener listener = null;
    protected boolean justification = true;
    protected int viewHeight = -1;
    protected List<TextLine> lines;
    private TextPaint paint;
    private final TextPaint reflowPaint = new TextPaint();
    private Paint backgroundPaint = new Paint();
    private ContentView.Options mOptions = null;
    private int mStart;
    private int mEnd;
    protected int mViewsCount = 0;
    private boolean mNeedTotalHeight = false;
    private boolean mReflowFinished = false;
    private boolean mIsLayouted = false;
    private boolean mCompatDrawableCallback = Build.VERSION.SDK_INT<11;
    /* selection handling vars */
    private int mSelectionStart = 0;
    private int mSelectionEnd = 0;
    private int mSelectionColor = Color.GRAY;

    /* highlight handling vars */
    private int mHighlightStart = 0;
    private int mHighlightEnd = 0;
    private int mHighlightColor = Color.YELLOW;

    /* forward drawables support */
    private SparseArray<DynamicDrawableSpan> mDynamicDrawableSpanSparseArray = new SparseArray<DynamicDrawableSpan>();
    public boolean isLayouted() {
        return mIsLayouted;
    }

    protected void setIsLayoutedInternal(boolean value) {
        mIsLayouted = false;
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
        mIsLayouted = false;
        if (listener != null)
            listener.onTextInfoInvalidated();
    }

    protected void notifyTextReady() {
        mIsLayouted = true;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onTextHeightChanged();
                }
            }
        });
        listener.onTextReady();
    }

    /**
     *
     * @param y
     * @param startLine
     * @return 0 if y above startLine, getLinesCount()-1 if y below lastLine
     */

    public int getLineForVertical(float y, int startLine) {
        int bottom = 0;
        if (lines == null) {
            Log.e(TAG, "need reflow!");
            return -1;
        }
        for (int j = startLine; j < lines.size(); j++ /* LineSpan.LineDescription line : lines */) {
            TextLine line = lines.get(j);
            if (y > bottom && y < bottom + line.height) {
                return j;
            }
            bottom += line.height;
        }
        return lines.size()-1;
    }

    /**
     *
     * @param y-coordinate on canvas
     * @return number of line for y-coordinate
     */

    public int getLineForVertical(float y) {
        return getLineForVertical(y, 0);
    }

    public void setInvalidateListener(TextLayoutListener listener) {
        this.listener = listener;
    }

    public int getWidth() {
        return width;
    }

    public LinesIterator getLinesIterator() {
        return new LinesIterator();
    }

    public String getState() {
        return "";
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

    public TextLayout(Spanned text, int start, int end, TextPaint paint, TextLayoutListener invalidateListener) {
        this(text, start, end, paint, new ContentView.Options(), invalidateListener);
    }

    /**
     * @param text               - Spanned instance
     * @param start              - begin of text whe want to layout
     * @param end                - end of text
     * @param paint              - default paint
     * @param invalidateListener
     */

    public TextLayout(Spanned text, int start, int end, TextPaint paint, ContentView.Options options, TextLayoutListener invalidateListener) {
        listener = invalidateListener;
        mStart = start;
        mEnd = end;
        mViewsCount = 0;
        chars = new char[end - start];
        mText = text;
        TextUtils.getChars(text, start, end, chars, 0);
        mOptions = options;
        mOptions.setChangeListener(this);

        if (mOptions.getLineBreaker() == null) {
            mOptions.setLineBreaker(new DefaultLineBreaker());
        }

        this.paint = paint;
        lineSpan = LineSpan.prepare(text, start, end, mParagraphStartMargin, mParagraphTopMargin,mDynamicDrawableSpanSparseArray);
    }

    public TextLayout() {
        /* stub */
    }

    public TextLayout(Spanned text, TextPaint paint) {
        this(text, 0, text.length(), paint, null);
    }

    public TextLayout(Spanned text, TextPaint paint, TextLayoutListener invalidateListener) {
        this(text, 0, text.length(), paint, invalidateListener);
    }

    /**
     * @param view           - view we attached to
     * @param x              - x coordinate
     * @param y              - y coordinate
     * @param startsFromLine
     * @return clicked character offset
     *
     * WARNING: getOffsetForCoordinates() does not use paddings! (yet)
     */

    public int getOffsetForCoordinates(View view, float x, float y, int startsFromLine) {
        int bottom = 0;
        if (lines == null) {
            Log.e(TAG, "need reflow!");
            return 0;
        }
        for (int j = startsFromLine; j < lines.size(); j++) {
            TextLine line = lines.get(j);
            LineSpan span = line.span.get();
            if (line.wrapHeight > 0 && span != null && span.isDrawable) {
                if (y < bottom || y > (bottom + line.wrapHeight))
                    continue;
                if (span.gravity == Gravity.LEFT) {
                    if (span.drawableScaledWidth > x)
                        return span.start;
                } else if (span.gravity == Gravity.RIGHT) {
                    if (x > width - span.drawableScaledWidth) {
                        return span.start;
                    }
                } else {
                    return span.start;
                }
                continue;
            } else if (y > bottom && y < bottom + line.height) {
                int i = getOffsetForHorizontal(line, (int) x);
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
        if (line<0 || line+1>lines.size()) return -1;
        if (lines != null)
            return lines.get(line).start;
        return -1;
    }

    public int getLineEnd(int line) {
        if (line<0 || line+1>lines.size()) return -1;
        if (lines != null)
            return lines.get(line).end;
        return -1;
    }

    /*

     */
    public int getLineTop(int line) {
        if (lines == null || line > lines.size()-1) return 0;
        int y = 0;
        for (int i = 0; i < line; i++) {
            TextLine l = lines.get(i);
            y += lines.get(i).height;
        }
        return y;
    }

    public int getLineBottom(int line) {
       if (lines == null || line > lines.size()-1) return 0;
        int top = getLineTop(line);
        return top + lines.get(line).height;
    }

    /**
     * calculate horizontal offset of character with index 'position'
     *
     * @param line
     * @param position
     * @return
     */

    public int getPrimaryHorizontal(int line, int position) {
        return (int) getOffsetXLtr(lines.get(line), position);
    }

    /**
     * @return
     */

    public int getHeight() {
        return this.height;
    }

    @Override
    public TextPaint getTextPaint() { return this.paint; }

    /**
     * @param size
     */

    public void setTextSize(float size) {
        float oldSize = paint.getTextSize();
        if (oldSize == size || lineSpan == null) {
           return;
        }
        paint.setTextSize(size);
        invalidateMeasurement();
    }

    /**
     * @param paint
     */

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
        mIsLayouted = false;
        doReflowInBackground();
        mIsLayouted = true;
        reflowedWidth = width;
        reflowedTextSize = getPaint().getTextSize();
    }

    protected void setPaint(TextPaint paint) {
        this.paint = paint;
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
        if (getOptions().isAsyncReflow()) {
            mReflowBackgroundTask = new Thread(new ReflowBackgroundTask());
            mIsLayouted = true;
            mReflowBackgroundTask.start();
        } else {
            new ReflowBackgroundTask().run();
        }
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

    public void draw(Canvas canvas, int left, int top, int right, int bottom, int startLine, int endLine) {
        if (endLine<=startLine) return;
        workPaint.set(paint);
        int state = canvas.save();
        int width = right - left;
        if (reflowedWidth < width)
            width = reflowedWidth;
        canvas.translate(left, top);
        canvas.clipRect(0, 0, right - left, bottom - top);

        if (getLineSpan() != null) {
            if (isReflowBackgroundTaskRunning()) {
                draw(lines, startLine, endLine, getChars(), canvas, width, viewHeight, getPaint(), 0, 0, 0, 0, 0, 0, getOptions().isJustification());
            } else {
                draw(lines, startLine, endLine, getChars(), canvas, width, viewHeight, getPaint(), getSelectionStarts(), getSelectionEnds(), getSelectionColor(), mHighlightStart, mHighlightEnd, mHighlightColor, getOptions().isJustification());
            }
        } else {
            // Log.w(TAG, "could not draw() getLines() returns null");
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
        draw(canvas, left, top, right, bottom, 0, getLinesCount());
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
        addHighlight(start,end,color);
    }

    /**
     *
     * @param start
     * @param end
     * @param color
     */

    public void addHighlight(int start, int end, int color) {

    }

    /**
     *
     * @param start
     * @param end
     */

    public void removeHighlight(int start, int end) {

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

    /**
     *
     */

    public class TextLine {
        int whitespaces = 0;
        private int descent;
        float width; // real line width (with margin, but without justification)
        private float justifyArgument = 0f;
        private LeadingMarginSpan leadingMargin;
        private boolean hyphen = false;
        private WeakReference<LineSpan> span;
        private int start;
        int end;
        private int height;
        private WeakReference<LineSpanBreak> afterBreak = null;
        private int margin = 0;
        private int direction = Layout.DIR_LEFT_TO_RIGHT;
        int gravity = Gravity.NO_GRAVITY;
        private int wrapHeight = 0;
        private int wrapWidth = 0;
        private int wrapMargin = 0;

        public TextLine(ReflowState state, int lineStartAt, LeadingMarginSpan leadingMarginSpan) {
            this.whitespaces = state.whitespaces;
            this.width = state.lineWidth;
            LineSpan span = state.carrierReturnSpan; // FIXME: after carrier return on span.ends state must eliminate carrierReturnSpan ?
            this.span = new WeakReference<LineSpan>(span);
            this.afterBreak = state.carrierReturnBreak == null ? null : new WeakReference<LineSpanBreak>(state.carrierReturnBreak);
            this.start = lineStartAt;
            this.height = state.height; // (int) (state.height + state.descent + state.leading * lineSpacingMultiplier + lineSpacingAdd);
            this.descent = state.descent;
            this.end = state.character + 1;
            if (debug && start==end) {
                Log.e(TAG,"start==end");
            }
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
            if (debug && start==end) {
                Log.e(TAG,"start==end");
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
                if (debug && start==end) {
                    Log.e(TAG,"start==end");
                }
            } // else
              //  throw new RuntimeException();
        }

        /**
         * for debug - dumps span info
         * @param str - reference to text
         * @return
         */
        public String dump(CharSequence str, boolean dumpBreaks) {
            // String str = new String(chars);
            if (span.get()==null) return "NULL";
            return "ss=" + span.get().start + ",se=" + span.get().end + " [w/h=" + width + "/" + height + ",w=" + whitespaces + ",(s=" + start + ",e=" + end + "j=" + justifyArgument + ")]: '" + str.subSequence(start, end) + "'\n" + dumpSpans(chars, dumpBreaks);
        }

        public String toString() {
            return this.toString(false);
        }

        public String toString(boolean dumpBreaks) {
            return dump(TextLayout.this.getText(), dumpBreaks);
        }

        /**
         *
         * @param chars
         * @return
         */

        private String dumpSpans(char[] chars, boolean dumpBreaks) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            LineSpan current = this.span.get();
            while (current != null && current.start < end) {
                ssb.append(current.toString(chars,dumpBreaks) + "\n");
                current = current.next;
            }
            return ssb.toString();
        }

        /**
         *
         * @param width
         */

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

        @Override
        public void run() {
            if (lineSpan == null) {
                return;
            }
            setReflowBackgroundTaskRunning(true);
            setReflowBackgroundTaskCancelled(false);
            setReflowFinished(false);
            /* default reflow listener supports multiviews reflow (with same width)*/

            lines = null;
            reflowPaint.set(paint);
            synchronized (this) {
                if (lineSpan != null)
                    lineSpan.clearCache(false);
            }


            if (listener != null)
                listener.onTextInfoInvalidated();
                reflow(chars, mStart, mEnd, lineSpan,
                        0, width, 0, requestedHeight, viewHeight,
                        reflowPaint,
                        getOptions());
            Log.v(TAG,"reflow finished");

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
                result = i;
            }
        return result;
    }

    /**
     * @param line
     * @return line height (bottom - top)
     */

    public float getLineHeight(int line) {
        return lines.get(line).height;
    }


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
        int rW = reflowedWidth;
        int rH = reflowedHeight;
        invalidateMeasurementInternal();
        setSize(rW, rH, viewHeight);
    }

    /**
     * called when layout geometry was changed, or some handlers replaced
     */

    @Override
    public void invalidateLines() {
        invalidateMeasurement();
    }

    /**
     *
     */

    private void invalidateLinesInternal() {
        mIsLayouted = false;
        if (this.lines != null)
            lines.clear();
        this.lines = null;
    }

    /**
     *
     */

    protected void invalidateMeasurementInternal() {
        invalidateLinesInternal();
        if (lineSpan != null)
            LineSpan.clearMeasurementData(lineSpan);
        mIsLayouted = false;
        reflowedWidth = -1;
        reflowedHeight = -1;
    }

    /**
     * stop background thread, if running
     */

    protected void stopReflowIfNeed() {
        if (isReflowBackgroundTaskRunning()) {
            setReflowBackgroundTaskCancelled(true);
            if (mReflowBackgroundTask != null)
                try {
                    mReflowBackgroundTask.join(getOptions().getReflowQuantize());
                    if (isReflowBackgroundTaskRunning())
                        mReflowBackgroundTask.interrupt();
                } catch (InterruptedException e) {
                    Log.w(TAG, "reflow background task join was interrupted");
                }
            Log.w(TAG, "reflow() task stopped");
        }
    }

    /**
     * default implementation does not supports geometry change supports
     * @param geometry
     * @return
     */

    protected boolean updateGeometry(int[] geometry) {
        return false;
    }

    protected String log_single_line(int line) {
        int ls = getLineStart(line);
        int le = getLineEnd(line);
        return getText().subSequence(ls, le).toString();
    }

    /**
     * method where handles interaction with TextLayoutListener
     * when overriden - onProgress() must increments mViewsCount on every viewHeightExceed==true
     *
     * @param lines
     * @param collectedHeight
     * @param viewHeightExceed
     * @return
     */

    protected boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed) {
        this.lines = lines;
        if (mNeedTotalHeight && listener != null) {
            this.height += collectedHeight;
            notifyTextHeightChanged();
        }
        /* */
        boolean finish = false;

        if (viewHeightExceed) {
            mViewsCount++;
            finish = !listener.onHeightExceed(height);
        }
        if (isReflowBackgroundTaskCancelled()) {
            finish = true;
        }
        if (finish) {
            setReflowBackgroundTaskRunning(false);
            setReflowBackgroundTaskCancelled(false);
            return false;
        }
        return true;

    }

    /**
     * method, where handles layout finish
     *
     * @param lines
     * @param height
     */

    protected void onFinish(List<TextLine> lines, int height) {
        this.lines = lines;
        if (debug) {
            int ii = 0;
            int th = 0;
            for (TextLine line : lines) {
                if (line.start == line.end) {
                    Log.e(TAG, "line.start==line.end " + ii);
                }
                th += line.height;
                ii++;
            }
        }
        setReflowBackgroundTaskCancelled(false);
        setReflowBackgroundTaskRunning(false);
        setReflowFinished(true);
        if (mNeedTotalHeight && listener != null) {
            this.height = height + text_paddings_height();
            notifyTextHeightChanged();
            notifyTextReady();
        } else if (listener != null) {
            notifyTextReady();
        }
    }

    private int text_paddings_height() {
        Rect h = getOptions().getTextPaddings();
        return h.top + h.bottom;
    }
    // guard for reflow()
    // WARNING: if make reflow() synchronized on TextLayout.this - we receive deadlock on TextLayoutEx
    private Object reflowLock = new Object();

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
                          ContentView.Options options) {
        // extract options
        if (debug)
            Log.v(TAG,"reflow with font size:"+paint.getTextSize());

        synchronized (reflowLock) {
            ReflowContext ctx = new ReflowContext(text, lineStartAt, textEnd, _startSpan, x, width, height, viewHeight, paint);

            /**
             * reflow() supports "defer" image to next view
             */

            ctx.process(text);
        }
    }


    /**
     * paint early prepared List&lt;LineDescription&gt; on canvas
     *
     * @param lines          - list of LineDescription
     * @param startLine
     * @param endLine
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

    TextPaint workPaint = new TextPaint();
    Paint highlightPaint = new Paint();
    Paint selectionPaint = new Paint();
    Rect drawablePaddings = new Rect();

    Set<Drawable> visibleDrawables = new HashSet<Drawable>();
    Set<Drawable> processedDrawables = new HashSet<Drawable>();
    Map<Drawable,Point> visibleDrawableOffsets = new HashMap<Drawable, Point>(); // use for handling 'invalidateSelf'
    Map<Drawable,Rect> visibleDrawableBounds = new HashMap<Drawable,Rect>();

    public int draw(List<TextLine> lines, int startLine, int endLine, char[] text, Canvas canvas, float width, float height, TextPaint paint, int selectionStart, int selectionEnd, int selectionColor, int highlightStart, int highlightEnd, int highlightColor, boolean justification) {
        if (lines == null || lines.size() < 1) {
            // Log.w(TAG, "lines==null || lines.size() < 1");
            return 0;
        }
        Rect clipRect = canvas.getClipBounds();
        Rect textPaddings = getOptions().getTextPaddings();
        getOptions().getDrawablePaddings(drawablePaddings);

        processedDrawables.clear();
        processedDrawables.addAll(visibleDrawables);

        if (debugDraw) {
            backgroundPaint.setStyle(Paint.Style.STROKE);
            backgroundPaint.setColor(Color.BLUE);

            canvas.drawRect(clipRect, backgroundPaint);
            backgroundPaint.setColor(Color.GREEN);
            canvas.drawRect(clipRect.left + textPaddings.left, clipRect.top + textPaddings.top, clipRect.right - textPaddings.right, clipRect.bottom - textPaddings.bottom, backgroundPaint);
            backgroundPaint.setColor(Color.RED);
            backgroundPaint.setStrokeWidth(5);
        }

        int leftOffset = textPaddings.left;
        int topOffset = textPaddings.top;
        int drawablePaddingWidth = drawablePaddings.left+drawablePaddings.right;
        int drawablePaddingHeight = drawablePaddings.top+drawablePaddings.bottom;

        highlightPaint.setColor(highlightColor);
        selectionPaint.setColor(selectionColor);

        int i = startLine;
        int y = topOffset;

        boolean highlight = false;
        boolean selection = false;
        TextLayout.TextLine line = lines.get(startLine);
        // DBG (issue with WeakRef to textLayout lines)
        if (debug && line==null) {
            Log.e(TAG,"line are null");
            return 0;
        }
        LeadingMarginSpan actualLeadingMargin = null;
        int count = 0;
        while ((y + line.height < clipRect.top) && (y + line.wrapHeight < clipRect.top)) {
            y += line.height;
            // Log.v("DDD","line "+i+" height="+line.height+" total="+y+ "(1)");
            i++;
            count++;
            if (i < endLine)
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
        for (; i < endLine && y < clipRect.bottom; i++) {

            line = lines.get(i);
            if (height > 0 && (y + line.height > clipRect.bottom) && line.wrapHeight < 1) {
                // Log.v("DDD","line "+i+" height="+line.height+" total="+y+" finish (3)");

                break linesLoop;
            }
            if (line.span == null) { // special case uses for closing image wrap
                y += line.height;
                // Log.v("DDD","line "+i+" height="+line.height+" total="+y+" (2)");
                continue;
            }
            int drawStart = line.start;
            int drawStop = line.end;
            if (drawStop <= drawStart && line.height > 0 && line.span.get().drawableScaledWidth < 1) {
                y += line.height; // line.span.get().height;
                // Log.v("DDD","line "+i+" height="+line.height+" total="+y+" skip (4)");
                if (line.span.get().isDrawable) {
                    // Log.v(TAG, "skip line with drawable O_o");
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
                    selectEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (selectionStart < line.start && selectionEnd < line.end) {
                /* selection starts on previous line, but ends on current line */
                    findSelectionEndX = true;
                } else if (selectionStart >= line.start && selectionEnd > line.end) {
                /* selection starts on current line, but ends on next line or below */
                    findSelectionStartX = true;
                    selectEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
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
                    highlightEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (highlightStart <= line.start && highlightEnd <= line.end) {
                /* highlight starts on previous line, but ends on current line */
                    findHighlightEndX = true;
                } else if (highlightStart >= line.start && highlightEnd > line.end) {
                /* highlight starts on current line, but ends on next line or below */
                    findHighlightStartX = true;
                    highlightEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                } else if (highlightStart >= line.start && highlightEnd <= line.end) {
                /* highlight starts and ends on current line */
                    findHighlightStartX = true;
                    findHighlightEndX = true;
                }
                // Log.v(TAG, "find highlight startX = " + findHighlightStartX + ", endX = " + findHighlightEndX + " (" + highlightStart + "," + highlightEnd + ") on line [" + line.start + "," + line.end + "]");
                drawHighlight = true;
            }

            int baseLine = y + line.height - line.descent;
            float tail = (line.afterBreak == null || line.afterBreak.get()==null) ? 0f : line.afterBreak.get().tail;
            LineSpan span = line.span.get();

            float x;
            int skip = line.afterBreak == null ? 0 : line.afterBreak.get().skip;

            if (line.leadingMargin != null) {
                workPaint.set(paint);
                if (line.leadingMargin != actualLeadingMargin) {
                    actualLeadingMargin = line.leadingMargin;
                }

                try {
                    line.leadingMargin.drawLeadingMargin(canvas, workPaint, line.wrapMargin + leftOffset, 1, y, baseLine, baseLine + line.descent, new FakeSpanned(line.leadingMargin, drawStart), drawStart, drawStop, false, null);
                } catch (NullPointerException e) {
                    // avoid unsupported leading margins exceptions, caused layout==null
                }
            } else {
                actualLeadingMargin = null;
            }

            float align = leftOffset;

            if (line.gravity != Gravity.NO_GRAVITY) {
                if (line.gravity == Gravity.RIGHT) {
                    align = width - line.width - line.wrapMargin;
                } else if (line.gravity == Gravity.CENTER_HORIZONTAL) {
                    align = ((width - line.margin)/ 2) - (line.width / 2);
                }
            }

            if (drawSelection && span!=null) {
                if (findSelectionStartX)
                    selectStartX = getOffsetXLtr(line,selectionStart); // calculateOffset(line.span.get(), line.start, selectionStart, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + align;
                if (findSelectionEndX)
                    selectEndX = getOffsetXLtr(line,selectionEnd); // calculateOffset(line.span.get(), line.start, selectionEnd+1, (justification ? line.justifyArgument : 0), getOptions()) + line.margin +line.wrapMargin + align;
                canvas.drawRect(selectStartX + align - leftOffset, y, selectEndX + align - leftOffset, y + line.height, selectionPaint);
            }

            LineSpanBreak lineSpanBreak = span==null ? null : (line.afterBreak == null ? span.breakFirst : (line.afterBreak.get().next == null ? null : line.afterBreak.get().next));
            // LineSpanBreak lineSpanBreak = span == null ? null :
            //        (line.afterBreak.get()==null ? span.breakFirst :
                            // (line.afterBreak.get()));
            x = line.margin + align;
            drawStart += skip;
            drawline:
            while (span != null && drawStart < line.end) {
                // draw leading drawable only
                if (span.isDrawable && span.end>line.start) {
                    lookupdrawable:
                    for (CharacterStyle style : span.spans) {
                        if (style instanceof DynamicDrawableSpan) {

                            DynamicDrawableSpan dds = (DynamicDrawableSpan) style;
                            final float drawableWidth;

                            if (span.gravity == Gravity.RIGHT) {
                                x = width - line.wrapWidth - textPaddings.right;
                            } else if (span.gravity == Gravity.CENTER_HORIZONTAL) {
                                x = width / 2 - (span.drawableScaledWidth) / 2 - drawablePaddings.left;
                            } else if (line.start == span.start) {
                                x -= textPaddings.left; // eliminate textPadding, if drawable are first character on line
                            }
                            int sY = y + span.baselineShift;
                            // need to avoid applying all 'character style' to paint before draw drawable
                            int corrector = dds.getVerticalAlignment()==DynamicDrawableSpan.ALIGN_BASELINE ? paint.getFontMetricsInt().descent : 0;

                            /**
                             * DynamicDrawableSpan.draw(canvas,text,start,end,x,top,y,bottom,paint)
                             * TODO:
                             */
                            Drawable dr = dds.getDrawable();
                            canvas.save();
                            canvas.translate(x + drawablePaddings.left, sY + drawablePaddings.top);
                            dr.draw(canvas);
                            canvas.restore();
                            // TODO: profile this
                            if (!visibleDrawables.contains(dr)) {
                                visibleDrawables.add(dr);
                                visibleDrawableOffsets.put(dr, new Point((int) x, sY));
                                visibleDrawableBounds.put(dr, new Rect(dr.getBounds()));
                                if (mCompatDrawableCallback && dr instanceof LazyDrawable)
                                    ((LazyDrawable)dr).setCallbackCompat(mDrawableCallback);
                                else
                                    dr.setCallback(mDrawableCallback);
                            } else {
                                processedDrawables.remove(dr);
                            } // TODO: end profile

                            if (debugDraw) {
                                backgroundPaint.setColor(Color.RED);
                                canvas.drawRect(
                                        x + 5 + drawablePaddings.left, sY + 5 + drawablePaddings.top,
                                        x - 5
                                                + span.drawableScaledWidth
                                                + drawablePaddingWidth - drawablePaddings.right,
                                        sY - 5
                                                + span.drawableScaledHeight
                                                + drawablePaddingHeight - drawablePaddings.bottom,
                                        backgroundPaint);
                                backgroundPaint.setColor(Color.YELLOW);
                                canvas.drawRect(
                                        x + drawablePaddings.left, sY + drawablePaddings.top,
                                        x
                                                + span.drawableScaledWidth
                                                + drawablePaddingWidth - drawablePaddings.right,
                                        sY
                                                + span.drawableScaledHeight
                                                + drawablePaddingHeight - drawablePaddings.bottom,
                                        backgroundPaint
                                );
                            }

                            if (span.drawableScaledWidth > 0f) {
                                drawableWidth = span.drawableScaledWidth + drawablePaddings.left + drawablePaddings.right;
                            } else {
                                drawableWidth = span.width;
                            }
                            // TODO: check correct paddings elimination (may be we need handle this in reflow() function)
                            x += drawableWidth;

                            break lookupdrawable;
                        }
                    }
                    // span = span.next;
                    // lineSpanBreak = span.breakFirst;
                    // drawStart = span == null ? 0 : span.start;
                    if (span.end==line.end) {
                        y += line.height;
                        continue linesLoop;
                    }
                    // drawStart ++;
                    span = span.next; // span with drawable always has length == 1
                    continue drawline;
                }

                boolean backgroundColorSpan = false;
                int backgroundColor = Color.WHITE;

                if (span.spans != null && span.spans != styles) {
                    workPaint.set(paint);
                    for (CharacterStyle style : span.spans) {
                        style.updateDrawState(workPaint);
                        // TODO: move to 'prepare'
                        if (style instanceof BackgroundColorSpan) {
                            backgroundColor = ((BackgroundColorSpan)style).getBackgroundColor();
                        }
                    }
                    styles = span.spans;
                }
                backgroundPaint.setColor(backgroundColor);

                while (lineSpanBreak != null) {
                    drawStop = lineSpanBreak.position + 1;
                    drawStop = drawStop > line.end ? line.end : drawStop;
                    if (drawStart < drawStop && drawStart > line.start - 1) {
                        if (backgroundColorSpan)
                            canvas.drawRect(x,y,x+span.width,y+span.height,backgroundPaint);
                        canvas.drawText(text, drawStart, drawStop - drawStart, x, baseLine, workPaint);
                        x += lineSpanBreak.width; // TODO: \n empty line has width ?
                    } else {
                        // Log.v(TAG,"oops");
                    }
                    drawStart = drawStop;


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

                    if (drawStart < drawStop) {
                        canvas.drawText(text, drawStart, drawStop - drawStart, x, baseLine, workPaint);
                        x += tail;
                    }
                }
                span = span.next;
                if (span != null) {
                    tail = span.width;
                    lineSpanBreak = span.breakFirst;
                    drawStart = span.start + skip;
                }
            } // end drawline: loop

            if (drawHighlight) {
                if (findHighlightStartX)
                    highlightStartX = getOffsetXLtr(line,highlightStart); // calculateOffset(line.span.get(), line.start, highlightStart, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + textPaddings.left;
                if (findHighlightEndX)
                    highlightEndX = getOffsetXLtr(line,highlightEnd); // calculateOffset(line.span.get(), line.start, highlightEnd+1, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + textPaddings.left;
                canvas.drawRect(highlightStartX + align, y, highlightEndX+align, y + line.height, highlightPaint);
            }

            if (line.hyphen) {
                canvas.drawText(TextLayout.mHyphenChar,0,1,x,baseLine,workPaint);
            }

            y += line.height;
        }
        // cleanup visible drawables
        for (Drawable unprocessed: processedDrawables) {
            visibleDrawables.remove(unprocessed);
            visibleDrawableOffsets.remove(unprocessed);
            visibleDrawableBounds.remove(unprocessed);
        }
        return 0;
    }

    private Drawable.Callback mDrawableCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            // TODO: drawableList builds on reflow() with save 'bounds'
            //      - check here, if bounds changes, and call reflow() process again, if drawable size changed
            //      - use flag 'trackDrawableBounds'
            if (!visibleDrawables.contains(who)) return;
            Point p = visibleDrawableOffsets.get(who);
            Rect bounds = who.getBounds();
            listener.invalidate(p.x,p.y,p.x+bounds.width(),p.y+bounds.height());
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            new Handler().postAtTime(what,when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            new Handler().removeCallbacks(what,who);
        }
    };

    private void drawLineRtl(Canvas canvas, float y, TextLine line) {

    }

    private void drawLineLtr(Canvas canvas, float y, TextLine line) {

    }

    private float getOffsetXRtl(TextLine line, int positionAtLine) {
        return 0;
    }

    /**
     * WARNING: this function must works on draw() algorythm
     * @param line
     * @param positionAtLine
     * @return
     */

    private float getOffsetXLtr(TextLine line, int positionAtLine) {
        ContentView.Options opts = getOptions();
        Rect textPaddings = opts.getTextPaddings();
        Rect drawablePaddings = new Rect();
        opts.getDrawablePaddings(drawablePaddings);
        float leftOffset = textPaddings.left;
        int drawStart = line.start;
        int drawStop = line.end;
        if (drawStop <= drawStart && line.height > 0 && line.span.get().drawableScaledWidth < 1) {
            return 0f + getOptions().getTextPaddings().left;
        }

        float tail = (line.afterBreak == null || line.afterBreak.get()==null) ? 0f : line.afterBreak.get().tail;
        LineSpan span = line.span.get();

        float x;
        int skip = line.afterBreak == null ? 0 : line.afterBreak.get().skip;

        float align = leftOffset;

        if (line.gravity != Gravity.NO_GRAVITY) {
            if (line.gravity == Gravity.RIGHT) {
                align = width - line.width - line.wrapMargin;
            } else if (line.gravity == Gravity.CENTER_HORIZONTAL) {
                align = ((width - line.margin)/ 2) - (line.width / 2);
            }
        }

        LineSpanBreak lineSpanBreak = span==null ? null : (line.afterBreak == null ? span.breakFirst : (line.afterBreak.get().next == null ? null : line.afterBreak.get().next));
        x = line.margin + align;
        drawStart += skip;
        if (positionAtLine<=drawStart)
            return x;

        drawline:
        while (span != null && drawStart < line.end) {
            // draw leading drawable only
            if (span.isDrawable && span.end>line.start) {
                float drawableWidth = span.width;

                if (span.gravity == Gravity.RIGHT) {
                    x = width - line.wrapWidth - textPaddings.right;
                } else if (span.gravity == Gravity.CENTER_HORIZONTAL) {
                    x = width / 2 - (span.drawableScaledWidth) / 2 - drawablePaddings.left;
                } else if (line.start == span.start) {
                    x -= textPaddings.left; // eliminate textPadding, if drawable are first character on line
                }

                if (span.drawableScaledWidth > 0f) {
                    drawableWidth = span.drawableScaledWidth + drawablePaddings.left + drawablePaddings.right;
                } else {
                    drawableWidth = span.width;
                }
                x += drawableWidth;

                if (span.end==line.end) {
                    return line.end;
                }
                // drawStart ++;
                span = span.next; // span with drawable always has length == 1
                continue drawline;
            }

            while (lineSpanBreak != null) {
                drawStop = lineSpanBreak.position + 1;
                drawStop = drawStop > line.end ? line.end : drawStop;
                if (drawStart < drawStop && drawStart > line.start - 1) {
                    if (positionAtLine<drawStop) {
                        for (;drawStart<positionAtLine;x+=span.widths[drawStart-span.start],drawStart++);
                        return x;
                    }
                    x += lineSpanBreak.width; // TODO: \n empty line has width ?
                }
                drawStart = drawStop;


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

                if (drawStart < drawStop) {
                    if (positionAtLine<drawStop) {
                        for (;drawStart<positionAtLine;x+=span.widths[drawStart-span.start],drawStart++);
                        return x;
                    }
                    x += tail;
                }
            }
            span = span.next;
            if (span != null) {
                tail = span.width;
                lineSpanBreak = span.breakFirst;
                drawStart = span.start + skip;
            }
        } // end drawline: loop
        return x;
    }

    /**
     * @param span
     * @param from
     * @param character
     * @return character offset from line start (without justification)
     */
    @Deprecated
    private static float getCharacterOffset(LineSpan span, int from, int character, ContentView.Options opts) {
        float result = 0f;
        for (int i = from; i < character; i++) {
            if (i - span.start + 1 > span.end - span.start) {
                span = span.next;
                if (span == null) break;
                if (span.isDrawable) {
                    if (span.drawableScaledWidth>0f) {
                        result += span.drawableScaledWidth + drawable_paddings_width(opts);
                        if (i<character-1) {
                            // TODO: solve correct wrapimage paddings
                        } else {
                            break;
                        }
                        span = span.next;
                    }
                }
                continue;
            }
            result += span.widths[i - span.start];
        }
        return result;
    }

    private static int drawable_paddings_width(ContentView.Options options) {
        Rect r = new Rect();
        options.getDrawablePaddings(r);
        return r.left + r.right;
    }

    /**
     * @param span
     * @param from
     * @param character
     * @return count of 'soft' breaks for line
     */
    @Deprecated
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

    /**
     *
     * @param line - number of line (<getLinesCount())
     * @param atX - x-coordinate from line starts
     * @return
     */

    public final int getOffsetForHorizontal(TextLayout.TextLine line, int atX) {
        // similar to DRAW, but no actual paint - just determine character position for given x coordinate
        // TODO: use code from getOffsetXLtr()
        int drawStart = line.start;
        int drawStop = line.end;
        int resultChar = line.start;
        int viewWidth = reflowedWidth;

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
            // Log.e(TAG, "empty weak ref!");
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
            // if (span.widths==null)
            //    LineSpan.measure(span,getChars(),measurePaint,true);
            // drawStop = span.end < line.end ? span.end : line.end;
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
                    for (int i = drawStart < span.start ? span.start : drawStart; i < drawStop; i++) {
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
                    // Log.v(TAG, "wut?");
                }
                resultChar = drawStart;
            }
        }

        return resultChar;
    }

    // returns x coordinate without padding applied

    private int getCharacterOffsetX(TextLayout.TextLine textLine, int position, boolean justification, float viewWidth, ContentView.Options opts) {
        return (int) getOffsetXLtr(textLine,position);
    }


    public class Options extends ContentView.Options {
        private ContentView.Options mParent;
    }

    private static class LinesList extends ArrayList<TextLine> {
        // public boolean add(TextLine line) {
        //    return super.add(line);
        // }
    }

    private class ReflowContext {
        private List<LineSpan> deffered = new ArrayList<LineSpan>();
        private ImagePlacementHandler imagePlacementHandler;
        private int viewHeightLeft;
        private int viewHeightDec;
        private Rect textPaddings;
        private Rect drawablePaddings = new Rect();
        private LineSpan wrappedSpan = null;
        private Point scale = new Point();
        private int wrapHeight = 0;
        private int wrapMargin = 0;
        private int wrapEnd = 0;
        private int y =0;
        private int collectedHeight = 0;
        private int lineWidthDec = 0;
        private int wrapWidth = 0;
        private LinesList result = new LinesList();
        private int lineStartAt = 0;
        private List<ReflowState> stack = new ArrayList<ReflowState>();
        private ReflowState state;
        private LineSpan span;
        private int lineMargin;
        private LineBreaker lineBreaker;
        private float lineSpacingMultiplier = 1f;
        private int lineSpacingAdd = 0;
        private boolean forceBreak = false;
        private int[] geometry = new int[2];
        private int leadingMargin = 0;
        private boolean carrierReturn = false;
        private boolean lineStartsWithRtl = false;
        private boolean lineContainsRtlSpans = false;
        private int currentDirection = Layout.DIR_LEFT_TO_RIGHT;
        private int forcedParagraphLeftMargin = 0;
        private int forcedParagraphTopMargin = 0;
        private int linesAddedInParagraph = 0;
        private LeadingMarginSpan leadingMarginSpan = null;
        private ContentView.Options options;
        private long timeQuantStart = System.currentTimeMillis();
        private LineSpan lastParagraphStarts = null;
        private ParagraphStyle[] paragraphStyles;
        private LeadingMarginSpan actualLeadingMarginSpan = null;
        private int lineBreakVal = -1;
        private int spanHeight = 0;
        private int spanLeading = 0;
        private int spanDescent = 0;
        private long steplimit = 150;
        private int textEnd;
        private int height = -1;
        private int width;
        private TextPaint paint;
        private int viewHeight = -1;

        public ReflowContext(char[] text, int lineStartAt, int textEnd, LineSpan _startSpan, float x, int width, int height, int viewHeight, TextPaint paint) {
            this.textEnd = textEnd;
            this.width = width;
            this.height = height;
            this.viewHeight = viewHeight;
            this.paint = paint;
            this.span = _startSpan;
            options = getOptions();
            lineBreaker = options.getLineBreaker();
             imagePlacementHandler = options.getImagePlacementHandler();
            if (imagePlacementHandler==null)
                imagePlacementHandler = new ImagePlacementHandler.DefaultImagePlacementHandler();
            textPaddings = options.getTextPaddings();
            options.getDrawablePaddings(drawablePaddings);
            lineWidthDec = textPaddings.left+textPaddings.right;
            viewHeightDec = textPaddings.top+textPaddings.bottom;
            viewHeightLeft = viewHeight - viewHeightDec;
            wrapWidth = width - lineWidthDec;
            wrapEnd = y;
            this.lineStartAt = lineStartAt;
            lineSpacingMultiplier = options.getLineSpacingMultiplier();
            lineSpacingAdd = options.getLineSpacingAdd();
            justification = options.isJustification();
            if (width < 1) {
                if (updateGeometry(geometry)) {
                    this.width = geometry[0];
                    this.viewHeight = geometry[1];
                } else {
                    throw new IllegalArgumentException("reflow called with argument 'width' < 1");
                }
            }
            state = new ReflowState(span, x);
            state.startSpan = span;
            state.carrierReturnSpan = span;
            if (span.start < lineStartAt) {
                if (debug && span.end <= lineStartAt) {
                    Log.e(TAG, "incorrect start span");
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
            forcedParagraphLeftMargin = options.getNewLineLeftMargin();
            forcedParagraphTopMargin = options.getNewLineTopMargin();
            steplimit = options.getReflowQuantize();
        }

        public void deferDrawable(LineSpan span) {
            deffered.add(span);
        }

        public boolean handleDefferedImages() {
            if (deffered.size()>0) {
                LineSpan defferedSpan = deffered.remove(0);
                DynamicDrawableSpan defferedDrawableSpan = defferedSpan.getDrawable();
                return handleImage(defferedSpan, defferedDrawableSpan, false);
            }
            return true;
        }

        private boolean checkViewHeightExceed() {
            if (viewHeight > -1 && !onProgress(result, y, true)) {
                return false;
            } else if (viewHeight > -1 && updateGeometry(geometry)) {
                                /* special case - ask geometry for next portion */
                width = geometry[0];
                viewHeight = geometry[1];
            }

            if (viewHeight > -1) { // TODO: reorganize conditions
                collectedHeight += y;
                y = 0;  // y zeroed only if viewHeight greater than -1
                viewHeightLeft = viewHeight - viewHeightDec;
                wrapWidth = width - lineWidthDec;
                wrapHeight = 0;
                wrapMargin = 0;
            }
            return true;
        }

        public boolean handleImage(LineSpan span, DynamicDrawableSpan dds,boolean allowDefer) {
            int placement = imagePlacementHandler.place(dds,
                    viewHeightLeft,
                    viewHeight,
                    wrapWidth,
                    width,
                    state.character,
                    scale,
                    options,
                    allowDefer);

            boolean collectHeights = false;
            if (debug)
                Log.v(TAG,"handleImage:"+span);
            if (placement==ImagePlacementHandler.DEFER) {
                deffered.add(span);
                if (state.character==lineStartAt) {
                    lineStartAt++;
                } // use __finishLine and return
                state.character++;
                return true;
            } else if (placement==ImagePlacementHandler.PLACEHOLDER) {
                span.noDraw = true;
                state.character++;
                return true;
            }

            int gravity = LineSpan.imageAlignmentToGravity(ImagePlacementHandler.getAlignment(placement));

            boolean finishLine = false;

            if (lineStartAt < state.character &&
                    (imagePlacementHandler.isNewLineBefore(placement) ||
                    (imagePlacementHandler.isWrapText(placement) && wrapHeight>0))) {
                if (debug) Log.v(TAG,"newLineBefore()");
                finishLine = true;
            }


            if (imagePlacementHandler.isWrapText(placement) && state.gravity != Gravity.CENTER_HORIZONTAL) {
                if (debug) Log.v(TAG,"wrapText");
                if (wrapHeight>0) {
                    Log.e(TAG,"close wrap null span");
                    result.add(new TextLine(null,0,wrapHeight));
                    wrapMargin = 0;
                }

                if (finishLine && !__finishLine()) return false;


                wrappedSpan = span;
                wrapHeight = scale.y + drawablePaddings.top + drawablePaddings.bottom;
                /* need to store y+wrapHeight as minimum height for layout */
                wrapEnd = collectedHeight + y + wrapHeight;

                int paddingWidth = drawablePaddings.right + drawablePaddings.left;
                wrapWidth = width - scale.x - paddingWidth;
                // TODO: use largest margin to split drawable and text (if wrapMargin==0 text left border==textPaddings.left)
                if (gravity == Gravity.LEFT) { // TODO:  if drawablePadding.right==0 - use textPadding.left instead
                    // wrapMargin = scale.x + paddingWidth - textPaddings.left; // width - wrapWidth - paddingWidth - textPaddings.left;
                    wrapMargin = width - (wrapWidth+textPaddings.left);
                    wrapWidth -= textPaddings.left;
                } else if (gravity == Gravity.RIGHT) {
                    wrapWidth -= textPaddings.right;
                    wrapMargin = 0;
                }
                collectHeights = true;
                TextLine ld = new TextLine(span, scale.y, scale.x + paddingWidth, gravity);
                state.skipWhitespaces=true;
                result.add(ld);
                /* add image break */
                LineSpanBreak br = span.newBreak();
                br.position = state.character;
                br.strong = true;
                br.carrierReturn = false;
                state.span.breakFirst = br;
                state.prevBreak = state.lastBreak;
                state.carrierReturnSpan = state.span;
                state.lastBreak = br;
                lineStartAt = state.character + 1;
            } else if (imagePlacementHandler.isNewLineAfter(placement) || state.gravity == Gravity.CENTER_HORIZONTAL) {
                if (debug) Log.v(TAG,"newLineAfter");
                if (finishLine && !__finishLine()) return false;
                TextLine ld;

                if (state.height < (scale.y + drawablePaddings.bottom + drawablePaddings.bottom)) {
                    state.height = scale.y + drawablePaddings.top + drawablePaddings.bottom;
                }

                if (viewHeightLeft < state.height) {
                    if (!checkViewHeightExceed()) return false;
                }
                y += state.height;
                viewHeightLeft -= state.height;
                span.gravity = gravity;

                if (height > -1 && y > height - 1) {
                    // Log.v(TAG, "9 break recursion height=" + height + ", y=" + y);
                    return false;
                }

                ld = new TextLine(span, scale.x, state.height);
                ld.margin = lineMargin;
                ld.gravity = state.gravity;
                result.add(ld); // first call here
                state.breakLineAfterImage();
                // state.breakLine(false,ld);
                collectHeights = false;
                lineStartAt = state.span.end;
                carrierReturn = true;
            } else {
                // inline/or placeholder
                spanHeight = scale.y + drawablePaddings.top+drawablePaddings.bottom;
                Log.e(TAG, "unsupported placement for image: " + placement);
            }
            span.drawableScaledWidth = scale.x;
            span.drawableScaledHeight = scale.y;
            Drawable dr = dds.getDrawable();
            if (dr != null) {
                dr.setBounds(0, 0, scale.x, scale.y);
            } else {
                Log.e(TAG, "no drawable on DynamicDrawableSpan: " + span);
            }

            if (collectHeights) {
                if (spanHeight > state.height) state.height = spanHeight;
                if (spanLeading > state.leading) state.leading = spanLeading;
                if (spanDescent > state.descent) state.descent = spanDescent;
            }
            state.skipWhitespaces = true;
            state.character++;

            return true; // switchSpan();
        }

        private void handleImageOld() {

        }

        private boolean __finishLine() {
            if (debug) Log.v(TAG,"finishLine()");
            if (state.character<1) return true;
            if (lineStartAt < state.character) {
                TextLine ld;
                y += state.height;
                viewHeightLeft -= state.height;

                if (height > -1 && y > height - 1) {
                    if (debug) Log.v(TAG, "4 break recursion while finish line");
                    return false;
                }

                ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                ld.margin = lineMargin + wrapMargin;
                ld.wrapMargin = wrapMargin;
                ld.end--;

                state.breakLine(false,ld); // carrierReturn(ld);
                result.add(ld);
                // state.character--; // correct shifted by carrierReturn() position;
                // WARN: do not execute justification on line before images
                lineStartAt = state.character;
                                /* handle wrap ends */
                wrapHeight -= ld.height;
                state.skipWhitespaces = true;
            } else if (lineStartAt == state.character) {
                if (wrapHeight > 0) {
                    result.add(new TextLine(null, 0, wrapHeight));   // TODO: need correctly handle in draw()
                    wrapHeight = 0;
                    wrapMargin = 0;
                }
                state.carrierReturnSpan = state.span; //
            }
            return true;
        }

        public void handleCarrierReturn() {
            // handle force carrier return
            try {
                state.processedWidth += span.widths[state.character - span.start];
            } catch (IndexOutOfBoundsException e) {
                // FIXME: dirty hack with try/catch
                // TODO: fix \n immediately after drawable span
                // Log.v(TAG,"text length = "+text.length+" span.widths.length="+span.widths.length);
            }
                    /* eliminate empty line only if non-empty-lines-count > threshold */
            if (options.isFilterEmptyLines() && state.character == lineStartAt && linesAddedInParagraph < options.getEmptyLinesThreshold()) {
                TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                state.carrierReturn(ld);
                linesAddedInParagraph = 0;
            } else { // filterEmptyLines disabled, or state.character > lineStartAt, or linesAddedInParagraph >= emptyLinesTreshold
                TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
                if (options.isFilterEmptyLines() && state.character == lineStartAt) {
                    linesAddedInParagraph = 0;
                    ld.height = options.getEmptyLineHeightLimit();
                } else
                    linesAddedInParagraph++;
                y += ld.height;
                /* handle exceed view height */
                viewHeightLeft -= ld.height;
                result.add(ld);

                ld.margin = lineMargin + wrapMargin;
                ld.wrapMargin = wrapMargin;

                /* handle wrap ends */
                wrapHeight -= ld.height; // maybe after carrier return wrap margin must be resets?
                // state.carrierReturns ZEROES state.height
                state.carrierReturn(ld);

                if (wrapWidth < (width-lineWidthDec) && wrapHeight < 1) {
                    if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                        int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                        wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight - textPaddings.top);
                        wrappedSpan = null;
                    }

                    wrapWidth = width - lineWidthDec;
                    wrapMargin = 0;
                    wrapHeight = height > viewHeight ? height : viewHeight;
                            /* FIXME: adjust vertical wrapped image position here ? */
                }
            }

            if (lineMargin > 0 || span.paragraphStart) {
                state.lineWidth = lineMargin;
            }

            carrierReturn = true;
            state.lineWidth += forcedParagraphLeftMargin;
            lineStartAt = state.character;
            state.skipWhitespaces = true;
        }

        private boolean handleBreakLine(char[] text, int breakPosition, boolean hyphen) {
            boolean isWhitespace = text[breakPosition] == ' ';
            TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
            ld.margin = lineMargin + wrapMargin;
            ld.wrapMargin = wrapMargin;
            result.add(ld); //secondCallHere
            ld.hyphen = hyphen;
//                    ld.width += hyphen ? span.hyphenWidth : 0;
            state.breakLine(isWhitespace, ld); // nb: breakLine does not increment state.character

            // if this line starts after '\n' - threat it as new paragraph starts
            if (carrierReturn) {
                ld.height += forcedParagraphTopMargin;
                y += forcedParagraphTopMargin;
                if (leadingMarginSpan==null) { // apply paragraph margins only to lines without leading margin
                    ld.margin += forcedParagraphLeftMargin;
                    ld.width += forcedParagraphLeftMargin;
                }
                leadingMarginSpan = null;
                carrierReturn = false;
            }

            if (justification && ld.whitespaces > mJustificationTreshold)
                ld.justify(wrapWidth);

                    /* handle exceed view height */
            y += ld.height; // was state.height at 1368 -- DEBUG
            viewHeightLeft -= ld.height;
                    /* handle wrap ends */
            wrapHeight -= ld.height;
                    /* check if wrap around drawable finished */
            if (wrapWidth < (width-lineWidthDec) && wrapHeight < 1) {
                        /* adjust vertical wrapped image position here */
                if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                    int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                    wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight);
                    wrappedSpan = null;
                }

                wrapWidth = width - lineWidthDec;
                wrapMargin = 0;
                wrapHeight = (height > viewHeight ? height : viewHeight);
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
            return true;
        }

        public boolean nextSpan() {
            if (span.next==null) return false;
            span = span.next;
            /* clear span-depended fields */
            return true;
        }

        public boolean nextLine() {
            /* clear line-depended fields */
            return true;
        }

        public void process(char[] text) {
            /* recursion function port */
            recursion:
            while (span != null) {
                span.clearCache(true);
                long currentTime = System.currentTimeMillis();
                long timeSpent = currentTime - timeQuantStart;
            /* if we spent more times, than steplimit - execute callback */
                if (timeSpent > steplimit) {
                /* note - if viewHeightExceed == false - break loop if onProgress() returns false */
                    if (!onProgress(result, y + collectedHeight, false)) {
                        return;
                    }
                    timeQuantStart = currentTime;
                }

                leadingMargin = span.margin;

                workPaint.set(paint);

                if (span.isDrawable) {
                    if (span.width == 0)
                        LineSpan.measure(span, text, workPaint);
                } else if (span.widths == null) {
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
                    break recursion;
                }

                lineMargin = leadingMargin;

                if (state.lineWidth == 0f && (leadingMargin > 0 || span.paragraphStart)) {
                    // lineMargin = leadingMargin;
                    if (span.paragraphStart && span != lastParagraphStarts) {
                        lastParagraphStarts = span;
                        lineMargin += span.paragraphStartMargin;
                        state.height += span.paragraphTopMargin;
                    }

                }


            /* flag */
                boolean drawableScaleBreak = false;

                // begin handle span
            /* forceBreak required if we revert char sequence due to hyphenation/linebreak algorygthm */
                processing:
                while (state.character < span.end || forceBreak) {
                    if (state.skipWhitespaces) { // handle skip whitespaces
                        if (carrierReturn)
                            state.lineWidth = lineMargin;
                        state.doSkipWhitespaces(text);
                        if (lineStartAt < state.character) // skipWhitespaces must works only at lineStart, so this code execute after line ends
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
                            // check if new line exceed view height (height defined and collected height exceed)
                            if (height > -1 && collectedHeight + y + state.height + (carrierReturn ? forcedParagraphTopMargin : 0) > height) {
                                break recursion;
                            }
                            if (viewHeightLeft - state.height < 0 && !span.isDrawable) {
                                if (viewHeight > -1 && !onProgress(result, y, true)) {
                                    break recursion;
                                } else if (viewHeight > -1 && updateGeometry(geometry)) {
                                /* special case - ask geometry for next portion */
                                    width = geometry[0];
                                    viewHeight = geometry[1];
                                }

                                if (viewHeight > -1) { // TODO: reorganize conditions
                                    collectedHeight += y;
                                    y = 0;  // y zeroed only if viewHeight greater than -1
                                    viewHeightLeft = viewHeight - viewHeightDec;
                                    wrapWidth = width - lineWidthDec;
                                    wrapHeight = 0;
                                    wrapMargin = 0;
                                }

                            /* handle deffered images */
                                if (deffered.size() > 0) {
                                    if (!handleDefferedImages())
                                        break recursion;
                                } // END HANDLING DEFFERED IMAGES
                            } else if (viewHeightLeft - state.height < 0) {
                                // span are drawable, but no sufficient height here
                                if (debug) Log.e(TAG,"no sufficient height for drawable");
                            }
                        }
                        continue processing;
                    } // end handle whitespaces block


                    // view line width exceed or force break
                    if ((forceBreak || state.lineWidth + span.widths[state.character - span.start] > wrapWidth) && !drawableScaleBreak) {
                        // TODO: while wrap image - we need to check - if too less chars fit to image side
                        // (wrong image placement handler may cause wrap too big image
                        if (span.isDrawable) {
                            drawableScaleBreak = true; // drawable does not fit left width at all
                            continue processing;
                        }
                        if (state.character <= lineStartAt) {
                            if (debug) Log.w(TAG, "line exceed at first character");
                            if (wrapHeight>0) {
                                // Log.e(TAG,"we must cancel wrapping!");
                                Log.e(TAG,"close wrap null span");
                                result.add(new TextLine(null, 0, wrapHeight));
                                wrapMargin = 0;
                                viewHeightLeft -= wrapHeight;
                                wrapHeight = 0;
                                wrapWidth = width - lineWidthDec;
                                continue;
                            }
                            state.character++;
                        }

                        if (!forceBreak) // if forceBreak - lineBreakVal ALREADY contains position
                            lineBreakVal = lineBreaker.nearestLineBreak(text, state.lastWhitespace, state.character - 1, span.end);

                        // int lineBreakVal = forceBreak ? breakPosition : lineBreaker.nearestLineBreak(text, state.lastWhitespace, state.character - 1, span.end);
                        int breakPosition = LineBreaker.getPosition(lineBreakVal);
                        if (state.character == state.lastWhitespace) {
                            // Log.w(TAG, "first character on line does not fit to given width");
                            breakPosition = state.character;

                        }
                        boolean hyphen = LineBreaker.isHyphen(lineBreakVal);
//                        if (breakPosition < state.lastWhitespace) {
//                            // Log.e(TAG, "possible crash in future: error in lineBreaker:(" + lineBreaker + ") - nearestLineBreak(text,start,end,limit) must returns value, that greater or equal start");
//                        }
                        if (breakPosition <= lineStartAt) { // in some cases breakPosition<lineStartAt
                            breakPosition = state.character - 1;
                            // Log.e(TAG, "lineBreaker (" + lineBreaker + ") returns breakPosition before lineStarts");
                        }
                        forceBreak = false; // cancel force break
                    /* check if breakPosition < span.start (rollback to previous state) */
                        if (breakPosition < span.start) {
                            span.breakFirst = null;
                            // crash here if width too small
                            state = stack.remove(0);
                            span = state.span;
                            spanHeight = state.height;
                            forceBreak = true;
                            continue processing;
                        }

                    /* force close line code - test, if we can move it to procedure */
                        // local variables used :(

                        state.rollback(breakPosition);

                        if (hyphen) {
                            // check if text[breakPosition] + hyphen_width fit line
                            if (state.lineWidth + span.hyphenWidth > wrapWidth) {
                                if (debug) Log.v(TAG,"hyphen character exceed line width");
                                lineBreakVal = lineBreaker.nearestLineBreak(text,state.lastWhitespace,breakPosition-1,span.end);
                                forceBreak = true;
                                continue processing;
                            }
                            state.lineWidth += span.hyphenWidth;
                        }

                        handleBreakLine(text,breakPosition,hyphen);

                    } else if (text[state.character] == '\n') {
                        handleCarrierReturn();
                    } else if (text[state.character] == 65532) { // 65532 - OBJECT REPLACEMENT CHAR
                        if (!handleImage(span,span.getDrawable(),true)) break recursion;

                    } else if (!lineBreaker.isLetter(text[state.character])) {
                        // handle non-letter characters (m.b. only spaces?)
                        state.nonLetterBreak(text[state.character] != ' ' || span.strong);
// commented out
//                    }  else if (text[state.character] == '\u200F') { // RTL MARK
//                        if (debug) Log.v(TAG, "rtl mark!");
//                        /**
//                         * make current span direction RTL (if not)
//                         * and make state.direction = RTL
//                         */
//                        if (state.character==lineStartAt) {
//                            lineStartsWithRtl = true;
//                        }
//                        lineContainsRtlSpans = true;
//                        currentDirection = Layout.DIR_RIGHT_TO_LEFT;
//                        span.direction = Layout.DIR_RIGHT_TO_LEFT;
//                        state.character++;
//                    } else if (text[state.character] == '\u200E') { // LTR mark
//                        if (debug) Log.v(TAG, "ltr mark!");
//                        if (state.character==lineStartAt) {
//
//                        }
//                        currentDirection = Layout.DIR_LEFT_TO_RIGHT;
//
//                        state.character++;
                    } else {
                        // handle usual character
                        if (span.widths == null) {
                            if (debug) Log.v(TAG, "span widths are null: " + span);
                        }
                        state.increaseBreak(span.widths[state.character - span.start]);
                        state.character++;
                    }

                }
                // end handle span (
                if (!switchSpan())
                    break recursion;
            }
            // handle tail of line
            state.character--;
            TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);

            if (ld.end <= textEnd && (height < 1 || y + ld.height < height + 1)) {
                result.add(ld);
            } else
                state.height = 0;
            onFinish(result, (y + collectedHeight + state.height) > wrapEnd ? (y + collectedHeight + state.height) : wrapEnd);
        }

        private boolean switchSpan() {
            if (state.lineWidth == lineMargin) // cancel margin if only margin at new line
                state.lineWidth = 0f;
            // handle span switch
            if (state.skipWhitespaces) {
                state.processedWidth = state.span.width;
            } else {

            }
            stack.add(0, state.finish());
            if (span.next == null) {
                // Log.v(TAG, "break recursion at the end of chain");
                return false;
            }

            /**
             * TODO: release span.widths (if memory free required)
             *
             */
            if (span.next.breakFirst != null) {
                // Log.v(TAG, "clean underflow breakFirst");
                span.next.breakFirst = null;
            }

            state = new ReflowState(state, span.next);
            span = span.next;


            return true;
        }
    }



    /**
     * iterator to quick access to lines properties
     * developed to support MultiColumnTextViewEx's height==MeasureSpec.UNSPECIFIED
     */

    public class LinesIterator implements Iterator<TextLine> {
        private int mCurrent = 0;
        private TextLine current = null;
        int mSize = 0;

        public LinesIterator() {
            mSize = TextLayout.this.lines.size();
            if (mSize>0) {
                next();
            }
        }

        @Override
        public boolean hasNext() {
            return mCurrent < mSize;
        }

        @Override
        public TextLine next() {
            current = TextLayout.this.lines.get(mCurrent);
            mCurrent++;
            return current;
        }

        @Override
        public void remove() {

        }

        public int getHeight() {
            return current.height;
        }

        public int getStart() {
            return current.start;
        }

        public int getEnd() {
            return current.end;
        }

        public int getSize() {
            return mSize;
        }
    }

    /* */
    private RtlCache mRtlCache = new RtlCache();
    private class RtlCache {
        private SparseArray<char[]> mItems = new SparseArray<char[]>();
        public boolean contains(int position, int length) {
            char[] r = mItems.get(position);
            return r!=null && r.length == length;
        }
        public void put(int position, char[] r) {
            mItems.put(position,r);
        }
        @Override
        public void finalize() {
            try {
                super.finalize();
            } catch (Throwable throwable) {
                // suppress
            }
            if (mItems!=null)
                mItems.clear();
            mItems = null;
        }
    }

    {

    }
}
