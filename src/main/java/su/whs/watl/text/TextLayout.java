package su.whs.watl.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import su.whs.lazydrawable.parent.LazyDrawable;

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

public class TextLayout implements ITextLayout, ContentView.OptionsChangeListener {
    /* */
    private boolean debugDraw = false;
    private boolean debug = false;
    boolean debugDrawRtl = false;
    private boolean mFailedDrawAttempt = false;
    private static char[] mHyphenChar = new char[]{'-'};
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
    private boolean mCompatDrawableCallback = Build.VERSION.SDK_INT < 11;
    private int mMaxLines = -1;
    /* selection handling vars */
    private int mSelectionStart = 0;
    private int mSelectionEnd = 0;


    /* highlight handling vars */
    private int mHighlightStart = 0;
    private int mHighlightEnd = 0;
    private int mHighlightColor = Color.YELLOW;

    public int getDynamicDrawablePosition(DynamicDrawableSpan span) {
        Drawable dr = span.getDrawable();
        if (dr!=null) {
            DrawableInfo di = mDrawableInfos.get(dr);
            if (di!=null) {
                return di.position;
            }
        }
        return -1;
    }

    public void append(CharSequence text, int start, int end) {
        throw new RuntimeException("append not supported yet.");
//        stopReflowIfNeed();
//        SpannableStringBuilder ssb = new SpannableStringBuilder();
//        if (TextUtils.isEmpty(mText)) {
//            ssb.append(text,start,end);
//        } else {
//            ssb.append(mText);
//            ssb.append(text,start,end);
//        }
//        mText = ssb;
//        if (this.lines!=null)
//            this.lines.clear();
//        lineSpan = null;
//        prepare(mText,0,mText.length());
//        notifyTextInfoInvalidated();
    }

    /* */
    private class DrawableInfo {
        public DynamicDrawableSpan span;
        public int position;
        public int placement;
        public int width;
        public int height;
    }

    /* forward drawables support */
    private SparseArray<DynamicDrawableSpan> mDynamicDrawableSpanSparseArray = new SparseArray<DynamicDrawableSpan>();
    private WeakHashMap<Drawable, DrawableInfo> mDrawableInfos = new WeakHashMap<Drawable, DrawableInfo>();

    public synchronized boolean isLayouted() {
        return mIsLayouted;
    }

    protected synchronized void setIsLayoutedInternal(boolean value) {
        mIsLayouted = value;
    }

    public void notifyTextHeightChanged() {
        if (debug) Log.v(TAG, "notifyTextHeightChanged");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                setIsLayoutedInternal(true);
                if (listener != null)
                    listener.onTextHeightChanged();
            }
        });
    }

    protected void notifyTextInfoInvalidated() {
        if (debug) Log.v(TAG, "notifyTextInfoInvalidated");
        setIsLayoutedInternal(false);
        if (listener != null)
            listener.onTextInfoInvalidated();
    }

    protected void notifyTextReady() {
        if (debug) Log.v(TAG, "notifyTextReady");
        setIsLayoutedInternal(true);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onTextHeightChanged();
                    listener.onTextReady();
                }
            }
        });

    }

    /**
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
                if (line.wrapHeight > 0) return -1;
                return j;
            }
            bottom += line.height;
        }
        return lines.size() - 1;
    }

    public boolean isLineAreWrap(int line) {
        TextLine tl = lines.get(line);
        return tl.wrapHeight > 0;
    }

    /**
     * @param y-coordinate on canvas
     * @return number of line for y-coordinate
     */

    public int getLineForVertical(float y) {
        return getLineForVertical(y, 0);
    }

    public void setInvalidateListener(TextLayoutListener listener) {
        if (debug) Log.v(TAG, "setInvalidateListener:" + listener);
        this.listener = listener;
    }

    public TextLayoutListener getInvalidateListener() {
        return this.listener;
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

    private boolean mReleased  = false;

    public void release() {
        mText = null;
        mReleased = true;
        if (lines != null)
            lines.clear();
        lineSpan = null;
        chars = null;
        for (int i = 0; i < mDynamicDrawableSpanSparseArray.size(); i++) {
            int key = mDynamicDrawableSpanSparseArray.keyAt(i);
            DynamicDrawableSpan span = mDynamicDrawableSpanSparseArray.get(key);
            Drawable dr = span.getDrawable();
            if (dr != null && dr instanceof LazyDrawable) {
                ((LazyDrawable) dr).Unload();
            }
        }
        mDynamicDrawableSpanSparseArray.clear();
    }

    /**
     * default LineBreaker implementation
     */

    public static class DefaultLineBreaker extends LineBreaker {
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
        prepare(text,start,end);
    }

    protected void prepare(Spanned text, int start, int end) {
        lineSpan = LineSpan.prepare(text, start, end, mParagraphStartMargin, mParagraphTopMargin, mDynamicDrawableSpanSparseArray);
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
     * <p/>
     * WARNING: getOffsetForCoordinates() does not use paddings! (yet)
     */

    public int getOffsetForCoordinates(View view, float x, float y, int startsFromLine) {
        int bottom = 0;
        if (lines == null) {
            Log.e(TAG, "need reflow!");
            return 0;
        }
        y += getOptions().getTextPaddings().top;
        for (int j = startsFromLine; j < lines.size(); j++) {
            TextLine line = lines.get(j);
            LineSpan span = line.span == null ? null : line.span.get();
            if (span == null) {
                continue;
            }
            if (line.wrapHeight > 0 && span != null && span.isDrawable) {
                if (y < bottom || y > (bottom + line.wrapHeight)) {
                    if (j + 1 < lines.size()) {
                        TextLine fwd = lines.get(j + 1);
                        if (fwd.span == null) // handle cancelled wrap
                            bottom += fwd.height;
                    }
                    continue;
                }
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
                if (debug) {
                    Log.v(TAG, "x,y=(" + x + "," + y + ") for line=" + j + " offset=" + i + " (from line start= " + (i - line.start) + " )");
                    Log.v(TAG, "clicked char='" + getChars()[i] + "'");
                }
                return i;
            }
            bottom += line.height;
        }
        return -1;
    }

    /**
     * @param dds - DynamicDrawableSpan
     * @param out - Rect instance
     * @return true, if dds are visible on layout
     */

    public boolean getDynamicDrawableSpanRect(DynamicDrawableSpan dds, Rect out) {
        Drawable dr = dds.getDrawable();
        if (dr != null && visibleDrawables.contains(dr)) {
            Point offset = visibleDrawableOffsets.get(dr);
            Rect bounds = visibleDrawableBounds.get(dr);
            if (out != null)
                out.set(offset.x, offset.y, offset.x + bounds.width(), offset.y + bounds.height());
            return true;
        }
        return false;
    }

    @Deprecated
    public int getLinesCount() {
        return lines == null ? 0 : lines.size();
    }

    public int getLineCount() {
        return lines == null ? 0 : lines.size();
    }

    public int getLineStart(int line) {
        if (line < 0 || line + 1 > lines.size()) return -1;
        if (lines != null)
            return lines.get(line).start;
        return -1;
    }

    public int getLineEnd(int line) {
        if (line < 0 || line + 1 > lines.size()) return -1;
        if (lines != null)
            return lines.get(line).end;
        return -1;
    }

    /*

     */
    public int getLineTop(int line) {
        if (lines == null || line > lines.size() - 1) return 0;
        int y = 0;
        for (int i = 0; i < line; i++) {
            TextLine l = lines.get(i);
            y += lines.get(i).height;
        }
        return y;
    }

    public int getLineBottom(int line) {
        if (lines == null || line > lines.size() - 1) return 0;
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
        return (int) getOffsetX(lines.get(line), position);
    }

    /**
     * @return
     */

    public int getHeight() {
        return this.height;
    }

    @Override
    public TextPaint getTextPaint() {
        return this.paint;
    }

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
        else if (debug) {
            Log.d(TAG, "already layouted, no invalidate required");
        }
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
        setIsLayoutedInternal(false);
        doReflowInBackground();
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
        setIsLayoutedInternal(true);
        if (getOptions().isAsyncReflow()) { //
            mReflowBackgroundTask = new Thread(new ReflowBackgroundTask());
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
    private Rect mClipRect = new Rect();
    private boolean DEV_STUB = false;
    public void draw(Canvas canvas, int left, int top, int right, int bottom, int startLine, int endLine) {
        if (mReleased) throw new IllegalStateException("attemt to draw released layout");
        if (endLine <= startLine) {
            if (!isReflowBackgroundTaskRunning() && getOptions().isAsyncReflow()) {
               if (isLayouted()) {
                   Log.e(TAG,"background task not running, lines are null, and isLayouted flag set");
                   if (!TextUtils.isEmpty(getText())) {
                       doReflowInBackground();
                   }
               }
            }
            // mFailedDrawAttempt = true;
            return;
        }
        List<TextLine> lines_copy;
        synchronized (this) { lines_copy = lines; }
        workPaint.set(paint);
        int state = canvas.save();
        int width = right - left;
        if (reflowedWidth < width)
            width = reflowedWidth;
        canvas.translate(left, 0);
        // canvas.clipRect(left, top, right, bottom);
        mClipRect.set(0, top, width, bottom);
        mFailedDrawAttempt = false;
        if (getLineSpan() != null) {
            if (isReflowBackgroundTaskRunning()) {
                draw(lines_copy, startLine, endLine, getChars(), canvas, mClipRect, width, viewHeight, getPaint(), 0, 0, 0, DEV_STUB, getOptions().isJustification());
            } else {
                draw(lines_copy, startLine, endLine, getChars(), canvas, mClipRect, width, viewHeight, getPaint(), getSelectionStarts(), getSelectionEnds(), getSelectionColor(), DEV_STUB, getOptions().isJustification());
            }
        } else {
            // Log.w(TAG, "could not draw() getLines() returns null");
            mFailedDrawAttempt = true;
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
        addHighlight(start, end, color);
    }

    /**
     * @param start
     * @param end
     * @param color
     */

    public void addHighlight(int start, int end, int color) {

    }

    /**
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
     */

    public void setSelection(int start, int end) {
        mSelectionStart = start;
        mSelectionEnd = end;
    }

    protected int getSelectionColor() {
        return getOptions().getSelectionColor();
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

            // synchronized(this) { lines = null; }
            reflowPaint.set(paint);
            synchronized (this) {
                if (lineSpan != null)
                    lineSpan.clearCache(false);
            }


            if (listener != null) new Handler(Looper.getMainLooper())
                    .post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTextInfoInvalidated();
                        }
                    });
            reflow(chars, mStart, mEnd, lineSpan,
                    0, width, 0, requestedHeight, viewHeight,
                    reflowPaint,
                    getOptions());
            setReflowBackgroundTaskRunning(false);
            if (debug)
                Log.v(TAG, "reflow finished");

        }
    }

    /**
     * @param line
     * @return line descent (actually = bottom - baseline)
     */

    public float getLineDescent(int line) {
        return lines.get(line).descent;
    }

    /**
     * find line for character position
     * @param position - character position in text
     * @return number of line
     */
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
        if (mText==null) return "";
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
        setIsLayoutedInternal(true);
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
        //int rW = reflowedWidth;
        //int rH = reflowedHeight;
        invalidateMeasurementInternal();
        // setSize(rW, rH, viewHeight);
        if (listener != null) listener.onTextInfoInvalidated();
    }

    public void invalidateMeasurementFrom(int position) {
        invalidateMeasurement();
        /*
        int lineNo = getLineForPosition(position);
        TextLine line = this.lines.get(lineNo);
        LineSpan span = line.span.get();
        stopReflowIfNeed();
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new RuntimeException("invalidateMeasurement() must be called from UI Thread");
        invalidateMeasurementInternal(lineNo,span);
        if (listener!=null)
            listener.onTextInfoInvalidated();
            */
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

    public void invalidateLinesFrom(int position) {
        if (position>-1)
            invalidateLines();
    }

    private void invalidateLinesInternal() {
        setIsLayoutedInternal(false);
        if (this.lines != null) {
            // lines.clear();
        }
        // this.lines = null;
    }

    /**
     *
     */

    protected void invalidateMeasurementInternal() {
        invalidateLinesInternal();
        if (lineSpan != null)
            LineSpan.clearMeasurementData(lineSpan);
        setIsLayoutedInternal(false);
        reflowedWidth = -1;
        reflowedHeight = -1;
    }

    protected void invalidateMeasurementInternal(int line, LineSpan span) {
        // invalidateLinesInternal(line);
    }

    /**
     * stop background thread, if running
     */

    protected void stopReflowIfNeed() {
        if (isReflowBackgroundTaskRunning()) {
            setReflowBackgroundTaskCancelled(true);
            if (mReflowBackgroundTask != null)
                try {
                    mReflowBackgroundTask.join(getOptions().getReflowQuantize() * 4);
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
     *
     * @param geometry
     * @return
     */

    @Override
    public boolean updateGeometry(int[] geometry) {
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

    @Override
    public boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed) {
        synchronized (this) { this.lines = lines; }
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

    @Override
    public void registerDrawable(DynamicDrawableSpan dds, int placement, int position) {
        Drawable dr = dds.getDrawable();
        DrawableInfo di = new DrawableInfo();
        di.span = dds;
        di.width = dr.getIntrinsicWidth();
        di.height = dr.getIntrinsicHeight();
        di.placement = placement;
        di.position = position;
        mDrawableInfos.put(dr, di);
    }

    Drawable.Callback mPlaceholderCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            int width = who.getIntrinsicWidth();
            int height = who.getIntrinsicHeight();

            if (mDrawableInfos.containsKey(who)) {
                final DrawableInfo di = mDrawableInfos.get(who);
                if (di.width!=width || di.height!=height) {
                    final int line = getLineForPosition(di.position);
                    if (di.width < 0 && di.height < 0) {
                        // drawable calculate it's bound first time
                        di.width = width;
                        di.height = height;
                        who.setCallback(getDrawableCallbacks());
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                handleDrawableResizeOnLine(line,di.span);
                            }
                        });

                    } else {
                        throw new RuntimeException("placeholder callback called for drawable with defined geomety");
                    }
                }
            } else {
                throw new RuntimeException("callback set, but no DrawableInfo provided");
            }
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {

        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {

        }
    };

    private void handleDrawableResizeOnLine(int line, DynamicDrawableSpan span) {
        invalidateLines();
    }

    @Override
    public Drawable.Callback getPlaceholderCallbacks() {
        return mPlaceholderCallback;
    }

    @Override
    public Drawable.Callback getDrawableCallbacks() {
        return mDrawableCallback;
    }

    /**
     * method, where handles layout finish
     *
     * @param lines
     * @param height
     */

    @Override
    public void onFinish(List<TextLine> lines, int height) {
        synchronized (this) { this.lines = lines; }
        if (debug) {
            Log.d(TAG, "onFinish() height:"+height);
        }
        setReflowBackgroundTaskCancelled(false);
        setReflowBackgroundTaskRunning(false);
        setReflowFinished(true);
        if (mNeedTotalHeight && listener != null) {
            this.height = height + text_paddings_height();
            if (debug) {
                Log.d(TAG, "height report required, height=" + this.height);
            }
            notifyTextHeightChanged();
            notifyTextReady();
        } else if (listener != null) {
            if (debug) Log.d(TAG, "height report not required");
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
            Log.v(TAG, "reflow with font size:" + paint.getTextSize());

        synchronized (reflowLock) {
            ReflowContext ctx = new ReflowContext(text, lineStartAt, textEnd, _startSpan, x, width, height, viewHeight, mMaxLines, paint, this);

            /**
             * reflow() supports "defer" image to next view
             */

            try {
                ctx.process(text);
            } catch (NullPointerException e) {
                if (!isReflowBackgroundTaskCancelled()) {
                    Log.e(TAG, "FATAL: exception on non-canceled reflow():" + e.toString());
                    e.printStackTrace();
                    setReflowBackgroundTaskRunning(false);
                }
            }
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
    Paint debugPaint = new Paint();
    Rect drawablePaddings = new Rect();

    Set<Drawable> visibleDrawables = new HashSet<Drawable>();
    Set<Drawable> processedDrawables = new HashSet<Drawable>();
    Set<Animatable> visibleDrawableAnimations = new HashSet<Animatable>();
    Map<Drawable, Point> visibleDrawableOffsets = new HashMap<Drawable, Point>(); // use for handling 'invalidateSelf'
    Map<Drawable, Rect> visibleDrawableBounds = new HashMap<Drawable, Rect>();
    // List<Float> innerRtlStack = new ArrayList<Float>();

    public int draw(List<TextLine> lines, int startLine, int endLine, char[] text, Canvas canvas, Rect clipRect, float width, float height, TextPaint paint, int selectionStart, int selectionEnd, int selectionColor, boolean STUB_HIGHLIGHTS_OBJECT, boolean justification) {
        if (lines == null || lines.size() < 1) {
            // Log.w(TAG, "lines==null || lines.size() < 1");
            return 0;
        }
        // Rect clipRect = canvas.getClipBounds();
        Rect textPaddings = getOptions().getTextPaddings();
        getOptions().getDrawablePaddings(drawablePaddings);

        processedDrawables.clear();
        processedDrawables.addAll(visibleDrawables);

        if (debugDraw||debugDrawRtl) {
            backgroundPaint.setStyle(Paint.Style.STROKE);
            backgroundPaint.setColor(Color.BLUE);
            if (debugDraw) {
                canvas.drawRect(clipRect, backgroundPaint);
                backgroundPaint.setColor(Color.GREEN);
                canvas.drawRect(clipRect.left + textPaddings.left, clipRect.top + textPaddings.top, clipRect.right - textPaddings.right, clipRect.bottom - textPaddings.bottom, backgroundPaint);
            }
            backgroundPaint.setColor(Color.RED);
            backgroundPaint.setStrokeWidth(1);
            debugPaint.setColor(Color.RED);
            debugPaint.setStrokeWidth(2f);
        }

        final int leftOffset = textPaddings.left;
        int topOffset = textPaddings.top;
        int drawablePaddingWidth = drawablePaddings.left + drawablePaddings.right;
        int drawablePaddingHeight = drawablePaddings.top + drawablePaddings.bottom;

//        highlightPaint.setColor(highlightColor);
        selectionPaint.setColor(selectionColor);

        int i = startLine;
        int y = topOffset;

        boolean highlight = false;
        boolean selection = false;
        TextLine line = lines.get(startLine);
        // DBG (issue with WeakRef to textLayout lines)
        if (debug && line == null) {
            Log.e(TAG, "line are null");
            return 0;
        }
        LeadingMarginSpan actualLeadingMargin = null;

        while ((y + line.height < clipRect.top) && (y + line.wrapHeight < clipRect.top)) {
            y += line.height;
            i++;
            if (i < endLine)
                line = lines.get(i);
            else
                break;
        }

        if (selectionStart < selectionEnd)
            selection = true;
//        if (highlightStart < highlightEnd)
//            highlight = true;

        CharacterStyle[] styles = null;
        linesLoop:
        for (; i < endLine && y < clipRect.bottom; i++) {

            line = lines.get(i);
            boolean isLineRtl = false;
            if (LineSpan.isBidiEnabled()) {
                isLineRtl = (line.direction < 0);
                if (isLineRtl && debugDrawRtl) {
                    canvas.drawLine(leftOffset,y,leftOffset,y+line.height,debugPaint);
                }
            }
            if (height > 0 && (y + line.height > clipRect.bottom) && line.wrapHeight < 1) {
                // Log.v("DDD","line "+i+" height="+line.height+" total="+y+" finish (3)");
                break linesLoop;
            }

            if (line.span == null) { // special case uses for closing image wrap
                y += line.height;
                continue;
            }
            int drawStart = line.start;
            int drawStop = line.end;
            if (drawStop <= drawStart && line.height > 0 && line.span.get().drawableScaledWidth < 1) {
                y += line.height; // line.span.get().height;
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

            /* begin selections */
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
                } else if (selectionStart > line.start && selectionEnd < line.end) {
                /* selection starts and ends on current line */
                    findSelectionStartX = true;
                    findSelectionEndX = true;
                } else if (selectionEnd == line.end) {
                    selectEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
                }
                drawSelection = true;
            }

            /* begin highlights */
//            if (line.highlighted && highlight && (highlightStart < line.end && highlightEnd > line.start)) {
//                if (line.start >= highlightStart && line.end <= highlightEnd) {
//                /* fill entire background */
//                    highlightEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
//                } else if (highlightStart <= line.start && highlightEnd <= line.end) {
//                /* highlight starts on previous line, but ends on current line */
//                    findHighlightEndX = true;
//                } else if (highlightStart >= line.start && highlightEnd > line.end) {
//                /* highlight starts on current line, but ends on next line or below */
//                    findHighlightStartX = true;
//                    highlightEndX = textPaddings.left + line.wrapMargin + line.width + (justification ? line.justifyArgument * line.whitespaces : 0);
//                } else if (highlightStart >= line.start && highlightEnd <= line.end) {
//                /* highlight starts and ends on current line */
//                    findHighlightStartX = true;
//                    findHighlightEndX = true;
//                }
//                // Log.v(TAG, "find highlight startX = " + findHighlightStartX + ", endX = " + findHighlightEndX + " (" + highlightStart + "," + highlightEnd + ") on line [" + line.start + "," + line.end + "]");
//                drawHighlight = true;
//            }

            int baseLine = y + line.height - line.descent;
            float tail = (line.afterBreak == null || line.afterBreak.get() == null) ? 0f : line.afterBreak.get().tail;
            LineSpan span = line.span.get();

            float x;
            int skip = 0;
            if (line.afterBreak != null) {
                skip = line.afterBreak.get() == null ? 0 : line.afterBreak.get().skip;
            }

            float align = leftOffset;

            if (line.gravity != Gravity.NO_GRAVITY) {
                if (line.gravity == Gravity.RIGHT) {
                    align = width - line.width - line.wrapMargin;
                } else if (line.gravity == Gravity.CENTER_HORIZONTAL) {
                    align = ((width - line.margin) / 2) - (line.width / 2);
                }
            }

            if (drawSelection && span != null) {
                if (findSelectionStartX)
                    selectStartX = getOffsetX(line, selectionStart); // calculateOffset(line.span.get(), line.start, selectionStart, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + align;
                if (findSelectionEndX)
                    selectEndX = getOffsetX(line, selectionEnd); // calculateOffset(line.span.get(), line.start, selectionEnd+1, (justification ? line.justifyArgument : 0), getOptions()) + line.margin +line.wrapMargin + align;
                canvas.drawRect(selectStartX + align, y, selectEndX + align, y + line.height, selectionPaint);
            }

            LineSpanBreak lineSpanBreak = span == null ?
                    null :
                    (line.afterBreak == null ?
                            span.breakFirst :
                            (line.afterBreak.get() == null ?
                                    null : line.afterBreak.get().next));
            x = line.margin + align;

            if (line.leadingMargin != null) { // draw leading margin span (bullet, etc)
                workPaint.set(paint);
                if (line.leadingMargin != actualLeadingMargin) {
                    actualLeadingMargin = line.leadingMargin;
                }

                try {
                    line.leadingMargin.drawLeadingMargin(canvas, workPaint, (int) x, 1, y + line.descent, baseLine, baseLine + line.descent, new FakeSpanned(line.leadingMargin, drawStart), drawStart, drawStop, false, null);
                } catch (NullPointerException e) {
                    // avoid unsupported leading margins exceptions, caused layout==null
                }
                x += line.leadingMargin.getLeadingMargin(true);
            } else {
                actualLeadingMargin = null;
            }

            drawStart += skip;
            // innerRtlStack.clear(); // clear stack
            float ltrRun = 0f;
            drawline:
            while (span != null && drawStart < line.end) {
                // draw leading drawable only
                boolean isSpanRtl = span.direction <0;
                if (span.isDrawable && span.end > line.start) {
                    if (span.width == 0) {
                        span = span.next;
                        continue drawline;
                    }
                    lookupdrawable:
                    for (CharacterStyle style : span.spans) {
                        if (style instanceof DynamicDrawableSpan) {

                            DynamicDrawableSpan dds = (DynamicDrawableSpan) style;
                            final float drawableWidth;
                            if (span.start == line.start) x = 0;
                            if (span.gravity == Gravity.RIGHT) {
                                x = width - line.wrapWidth - textPaddings.right;
                            } else if (span.gravity == Gravity.CENTER_HORIZONTAL) {
                                x = width / 2 - (span.drawableScaledWidth) / 2 - drawablePaddings.left;
                            } else if (line.start == span.start) {
                                x -= textPaddings.left; // eliminate textPadding, if drawable are first character on line
                            }

                            int sY = y + span.baselineShift;
                            // need to avoid applying all 'character style' to paint before draw drawable
                            int corrector = dds.getVerticalAlignment() == DynamicDrawableSpan.ALIGN_BASELINE ? paint.getFontMetricsInt().descent : 0;

                            /**
                             * DynamicDrawableSpan.draw(canvas,text,start,end,x,top,y,bottom,paint)
                             * TODO:
                             */
                            Drawable dr = dds.getDrawable();
                            if (span.drawableScaledWidth > 0f) {
                                drawableWidth = span.drawableScaledWidth + drawablePaddingWidth;
                            } else {
                                drawableWidth = span.width + drawablePaddingWidth;
                            }
                            canvas.save();
                            if (isLineRtl) {
                                canvas.translate(width - x - drawableWidth + drawablePaddings.left, sY + drawablePaddings.top);
                            } else {
                                canvas.translate(x + drawablePaddings.left, sY + drawablePaddings.top);
                            }
                            dr.draw(canvas);
                            canvas.restore();
                            // TODO: profile this
                            if (!visibleDrawables.contains(dr)) {
                                visibleDrawables.add(dr);
                                visibleDrawableOffsets.put(dr,
                                        isLineRtl ?
                                                new Point((int) (width - x - drawableWidth + drawablePaddings.left), sY + drawablePaddings.top)
                                                : new Point((int) x, sY)
                                );
                                visibleDrawableBounds.put(dr, new Rect(dr.getBounds()));
                                if (mCompatDrawableCallback) {
                                    if (dr instanceof LazyDrawable) {
                                        ((LazyDrawable) dr).onVisibilityChanged(true);
                                    }
                                } // else
                                  //  dr.setCallback(mDrawableCallback);
                                // restore animations, if need
                                if (dr instanceof Animatable && visibleDrawableAnimations.contains(dr)) {
                                    visibleDrawableAnimations.remove(dr);
                                    ((Animatable) dr).start();
                                }
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


                            // TODO: check correct paddings elimination (may be we need handle this in reflow() function)
                            x += drawableWidth;

                            break lookupdrawable;
                        }
                    }

                    if (span.end == line.end) {
                        y += line.height;
                        continue linesLoop;
                    }

                    span = span.next; // span with drawable always has length == 1
                    continue drawline;
                }

                boolean backgroundColorSpan = false;
                int backgroundColor = Color.WHITE;
                float ltrX = x; // origin point for draw ltr spans on rtl line
                if (span.spans != null && span.spans != styles) {
                    workPaint.set(paint);
                    for (CharacterStyle style : span.spans) {
                        style.updateDrawState(workPaint);
                        // TODO: move to 'prepare'
                        if (style instanceof BackgroundColorSpan) {
                            backgroundColor = ((BackgroundColorSpan) style).getBackgroundColor();
                        }
                    }
                    styles = span.spans;
                }

                backgroundPaint.setColor(backgroundColor);
                if (isLineRtl) { // RTL supports require more CPU time

                    while (lineSpanBreak != null) { // loop over lineBreaks in RTL line
                        drawStop = lineSpanBreak.position + 1;
                        drawStop = drawStop > line.end ? line.end : drawStop;
                        if (drawStart < drawStop && drawStart > line.start - 1) {
                            if (isSpanRtl) {
                                float rtlWidth = lineSpanBreak.width + (lineSpanBreak.strong || !justification ? 0f : line.justifyArgument);

                                if (backgroundColorSpan)
                                    canvas.drawRect(width - x - rtlWidth, y, width - x, y + span.height, backgroundPaint);
                                if (span.reversed == null)
                                    canvas.drawText(text, drawStart, drawStop - drawStart, width - x - rtlWidth, baseLine, workPaint);
                                else
                                    canvas.drawText(span.reversed, 0, drawStop - drawStart, width - x - rtlWidth, baseLine, workPaint);
                                if (debugDraw) {
                                    backgroundPaint.setColor(getCycleColor());
                                    backgroundPaint.setStyle(Paint.Style.STROKE);
                                    float vY = mRandom.nextFloat() * 10 - 5;
                                    canvas.drawRect(width - x - rtlWidth, y + vY, width - x, baseLine + vY, backgroundPaint);
                                }
                            } else {
                                if (ltrRun < 1) { // check if we met inner LTR span, and innerRtlStack does not calculated yet
                                    // on first switch from RLT span - scan line spans forward to calculate correct span/breaks offsets
                                    // else - pop offset from stack and draw ltr span in correct order
                                    ltrX = x; // store current x as origin
                                    LineSpan ltrSpan = span;
                                    float tailX = 0f;
                                    innerLtrScanLoop:
                                    // TODO: need correct visual order with next line
                                    // at runtime - this loop executed once for each [ltr,ltr,ltr] sequence on line
                                    while (ltrSpan != null && ltrSpan.direction == Layout.DIR_LEFT_TO_RIGHT && ltrSpan.start < line.end) {
                                        LineSpanBreak ltrBreak = lineSpanBreak;
                                        if (ltrBreak == null) {
                                            // correct previous added offsets
                                            ltrRun += ltrSpan.width;
                                        } else
                                            while (ltrBreak != null) {
                                                tailX = ltrBreak.tail;
                                                // correct previous added offsets
                                                ltrRun += ltrBreak.width + (ltrBreak.strong ? 0f : line.justifyArgument);
                                                if (ltrBreak.carrierReturn) // we met end of line, so break loop
                                                    break innerLtrScanLoop; // TODO: we need to store tail!
                                                ltrBreak = ltrBreak.next;
                                            }
                                        ltrSpan = ltrSpan.next;
                                    }
                                    if (tailX > 0f)
                                        ltrRun += tailX;
                                }
                                if (backgroundColorSpan)
                                    canvas.drawRect(width - ltrX - ltrRun, y, width - ltrX - ltrRun, y + span.height, backgroundPaint);
                                canvas.drawText(text, drawStart, drawStop - drawStart, width - ltrX - ltrRun, baseLine, workPaint);
                                if (debugDraw) {
                                    backgroundPaint.setColor(getCycleColor());
                                    backgroundPaint.setStyle(Paint.Style.STROKE);
                                    float vY = mRandom.nextFloat() * 10 - 5;
                                    canvas.drawRect(width - ltrX - ltrRun, y + vY, width - ltrX, baseLine + vY, backgroundPaint);
                                }
                                ltrRun -= lineSpanBreak.width + (lineSpanBreak.strong || !justification ? 0f : line.justifyArgument);
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

                } else { // cycle over LTR line spans
                    while (lineSpanBreak != null) {
                        drawStop = lineSpanBreak.position + 1;
                        drawStop = drawStop > line.end ? line.end : drawStop;
                        if (drawStart < drawStop && drawStart > line.start - 1) {
                            if (backgroundColorSpan)
                                canvas.drawRect(x, y, x + span.width, y + span.height, backgroundPaint);
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
                } // end cycle over LTR line
                if (tail > 0f) {
                    drawStop = line.end < span.end ? line.end : span.end;

                    if (drawStart < drawStop) {
                        if (isLineRtl) {
                            if (isSpanRtl) {
                                if (span.reversed == null)
                                    canvas.drawText(text, drawStart, drawStop - drawStart, width - x - tail, baseLine, workPaint);
                                else
                                    canvas.drawText(span.reversed, 0, drawStop - drawStart, width - tail, baseLine, workPaint);
                            } else {
                                canvas.drawText(text, drawStart, drawStop - drawStart, width - ltrX - tail, baseLine, workPaint);
                            }
                        } else
                            canvas.drawText(text, drawStart, drawStop - drawStart, x, baseLine, workPaint);
                        x += tail;
                        ltrRun -= tail;
                    }
                }
                span = span.next;
                if (span != null) {
                    tail = span.width;
                    lineSpanBreak = span.breakFirst;
                    drawStart = span.start + skip;
                }
            } // end drawline: loop

//            if (drawHighlight) {
//                if (findHighlightStartX)
//                    highlightStartX = getOffsetX(line, highlightStart); // calculateOffset(line.span.get(), line.start, highlightStart, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + textPaddings.left;
//                if (findHighlightEndX)
//                    highlightEndX = getOffsetX(line, highlightEnd); // calculateOffset(line.span.get(), line.start, highlightEnd+1, (justification ? line.justifyArgument : 0), getOptions()) + line.margin + line.wrapMargin + textPaddings.left;
//                canvas.drawRect(highlightStartX + align, y, highlightEndX + align, y + line.height, highlightPaint);
//            }

            if (line.hyphen) {
                if (isLineRtl)
                    canvas.drawText(TextLayout.mHyphenChar, 0, 1, 0, baseLine, workPaint);
                else
                    canvas.drawText(TextLayout.mHyphenChar, 0, 1, x, baseLine, workPaint);
            }

            y += line.height;
        }
        // cleanup visible drawables
        for (Drawable unprocessed : processedDrawables) {
            if (unprocessed instanceof LazyDrawable) {
                // ((LazyDrawable)unprocessed).stopIfLoading();
                ((LazyDrawable) unprocessed).onVisibilityChanged(false);
            }
            if (unprocessed instanceof Animatable) {
                if (((Animatable) unprocessed).isRunning()) {
                    // keep 'running state'
                    visibleDrawableAnimations.add((Animatable) unprocessed);
                    ((Animatable) unprocessed).stop();
                }
            }
            visibleDrawables.remove(unprocessed);
            visibleDrawableOffsets.remove(unprocessed);
            visibleDrawableBounds.remove(unprocessed);
        }
        return 0;
    }

    private boolean isBoundsChanged(Drawable drawable) {
        if (mDrawableInfos.containsKey(drawable)) {
            int w = drawable.getIntrinsicWidth();
            int h = drawable.getIntrinsicHeight();
            DrawableInfo di = mDrawableInfos.get(drawable);
            if (di.width != w || di.height != h) return true;
            return false;
        }
        return true;
    }

    private boolean isExclusiveLine(Drawable drawable) {
        if (mDrawableInfos.containsKey(drawable)) {
            DrawableInfo di = mDrawableInfos.get(drawable);
            return ImagePlacementHandler.DefaultImagePlacementHandler.isNewLineBefore(di.placement) & ImagePlacementHandler.DefaultImagePlacementHandler.isNewLineAfter(di.placement);
        }
        return true;
    }

    private int getDrawablePosition(Drawable drawable) {
        if (mDrawableInfos.containsKey(drawable)) {
            DrawableInfo di = mDrawableInfos.get(drawable);
            return di.position;
        }
        return -1;
    }

    private boolean mLocalSetBounds = false;

    @Override
    public synchronized void setDrawableBounds(Drawable d, int left, int top, int right, int bottom) {
        mLocalSetBounds = true;
        d.setBounds(left,top,right,bottom);
        mLocalSetBounds = false;
    }

    @Override
    public void setMaxLines(int maxLines) {
        mMaxLines = maxLines;
    }

    private synchronized boolean isLocalSetBounds() { return mLocalSetBounds; }

    private Drawable.Callback mDrawableCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            if (isLocalSetBounds()) return;
            // if (Looper.getMainLooper().getThread() != Thread.currentThread()) return; // called from handleImage->setBound
//            if (isBoundsChanged(who)) {
//                if (isExclusiveLine(who))
//                    invalidateLinesFrom(getDrawablePosition(who));
//                else
//                    invalidateMeasurementFrom(getDrawablePosition(who));
//            }
            if ( // if drawable are invisible,
                    !visibleDrawables.contains(who)
                            // || // or current thread are not main thread - return
                            ) return;
            Point p = visibleDrawableOffsets.get(who);
            Rect bounds = who.getBounds();
            listener.invalidate(p.x, p.y, p.x + bounds.width(), p.y + bounds.height());
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            new Handler().postAtTime(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            new Handler().removeCallbacks(what, who);
        }
    };

    /**
     * WARNING: this function must works similar draw() algorythm
     *
     * @param line
     * @param index
     * @return
     */

    private float getOffsetX(TextLine line, int index) {
        Rect textPaddings = getOptions().getTextPaddings();
        Rect drawablePaddings = getOptions().getDrawablePaddings();
        return Utils.runLineSpanToIndex(
                getChars(),
                workPaint,
                line.span.get(),
                line.afterBreak == null ? null :
                        line.afterBreak.get(),
                line.start,
                index,
                line.margin,
                line.direction,
                getOptions().isJustification() ? line.justifyArgument : 0f,
                reflowedWidth,
                textPaddings,
                drawablePaddings);
    }

    /**
     * @param line - number of line (<getLinesCount())
     * @param x    - x-coordinate from line starts
     * @return
     */

    public final int getOffsetForHorizontal(TextLine line, int x) {
        return Utils.runLineSpanToX(getChars(), workPaint, line.span.get(),
                line.afterBreak == null ? null :
                        (line.afterBreak.get().position < line.start ? line.afterBreak.get() : null),
                line.start,
                x, line.margin, line.direction, line.justifyArgument, reflowedWidth, getOptions().getTextPaddings(), getOptions().getDrawablePaddings());
    }

    public class Options extends ContentView.Options {
        private ContentView.Options mParent;
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
            if (mSize > 0) {
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


    private static final int[] sColors = new int[]{
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.MAGENTA
    };

    Random mRandom = new Random();

    int mColorIdx = 0;

    private int getCycleColor() {
        int result = sColors[mColorIdx];
        mColorIdx++;
        if (mColorIdx > sColors.length - 1) mColorIdx = 0;
        return result;
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        release();
    }

    private class RenderContext {
        private List<TextLine> mLines;
        private Map<Drawable,DrawableInfo> mDrawableInfos;

        public RenderContext(List<TextLine> lines, Map<Drawable,DrawableInfo> drawableInfos) {
            mLines = lines;
            mDrawableInfos = drawableInfos;
        }
    }
}
