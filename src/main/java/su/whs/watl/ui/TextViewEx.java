package su.whs.watl.ui;

/* copyright (c) 2014-2015 igor n. boulliev <igor@whs.su> */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import su.whs.watl.text.ContentView;
import su.whs.watl.text.DynamicDrawableInteractionListener;
import su.whs.watl.text.ImagePlacementHandler;
import su.whs.watl.text.TextLayout;
import su.whs.watl.text.TextLayoutListener;


/**
 * TextViewEx - drop-in replacement for TextView
 * additional features:
 * - supports LineBreaker for line breaks
 * - supports full justification for spanned strings
 * - supports OverflowListener
 * - supports two-tap click on urls
 * - supports hyphenation (see sample application)
 */

public class TextViewEx extends TextViewWS implements TextLayoutListener, ITextView {
    private static final String TAG = "TextViewEx";
    //private ContentView.Options mPendingOptions; // = new ContentView.Options();
    private boolean mDebug = false; // BuildConfig.DEBUG;
    private TextLayout mTextLayout;
    private boolean mHeightWrapContent = false;
    // private boolean mFallBackMode = false;
    private boolean mTextIsSelectable = true;
    // @Attribute
    private ClickableSpan mHighlightedSpan = null;
    // @Attribute
    private int mHighlightColor = Color.YELLOW;

    // @Attribute
    private int mHighlightAlpha = 70;
    // @Attribute

    private TextViewLayoutListener mLayoutListener = null;
    private ContentView.Options mOptions;
    private boolean mNeedTotalHeight = true;

    private ImagePlacementHandler mImagePlacementHandler = new ImagePlacementHandler.DefaultImagePlacementHandler();
    // default dynamicdrawablespan interaction listener - just make toast
    private DynamicDrawableInteractionListener mDynamicDrawableInteractionListenerDefault = new DynamicDrawableInteractionListener() {
        @Override
        public void onClicked(DynamicDrawableSpan span, Rect bounds, View view) {
//            Toast.makeText(getContext(), "clicked " + span.getDrawable(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onLongClick(DynamicDrawableSpan span, RectF bounds, View view) {
            // TODO: implement calls
        }
    };

    private DynamicDrawableInteractionListener mDynamicDrawableInteractionListener = mDynamicDrawableInteractionListenerDefault;

    public TextViewEx(Context context) {
        this(context, null, 0);
    }

    public TextViewEx(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextViewEx(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        getOptions().fromAttributes(context, attrs, defStyle, 0);
    }

    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    @Override
    public void setTextSize(int unit, float size) {
        float value = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, getResources().getDisplayMetrics());
        mTextLayout.setTextSize(value);
    }

    public void setTextViewLayoutListener(TextViewLayoutListener l) {
        mLayoutListener = l;
    }

    @Override
    public ContentView.Options getOptions() {
        return mOptions==null ? makeOptions() : mOptions;
    }

    protected ContentView.Options makeOptions() {
        if (mOptions==null) {
            mOptions = new ContentView.Options();
            if (mImagePlacementHandler!=null)
                mOptions.setImagePlacementHandler(mImagePlacementHandler);
        }
        return mOptions;
    }

    @Override
    public void setText(CharSequence _text, BufferType type) {
        if (isInEditMode()) {
            super.setText(_text, type);
            return;
        }
        CharSequence text = _text instanceof Spanned ? _text : new SpannableString(_text);

        super.setText("", BufferType.NORMAL);
        if (_text == null || _text.length() < 1) return;

        if (mTextLayout!=null)
            mOptions = mTextLayout.getOptions();

        if (mOptions==null) {
            mOptions = makeOptions();
        }
        mTextLayout = new TextLayout((Spanned) text, 0, text.length(), getPaint(),
                mOptions,
                this);
        if (mHeightWrapContent) {
            requestLayout();
        } else
            invalidate();
    }

    /**
     * WARNING: local options overriden with TextLayout.getOptions()
     * @param textLayout
     */

    public void setTextLayout(TextLayout textLayout) {
        if (textLayout == null) throw new IllegalArgumentException("textLayout must be not null");
        if (mTextLayout!=null) mTextLayout.release();
        mTextLayout = textLayout;
        mOptions = mTextLayout.getOptions();
        mTextLayout.setInvalidateListener(this);
        postInvalidate();
    }

    @Override
    public void setHighlightColor(int color) {
        mHighlightColor = color;
    }

    private int gMeasuredWithWMS = -1;
    private int gMeasuredWithHMS = -1;
    private int gMeasuredWidth = -1;
    private int gMeasuredHeight = -1;
    /**
     * @param wms - width measurement specs
     * @param hms - height measurement specs
     */

    @Override
    public void onMeasure(int wms, int hms) {
        if (isInEditMode()) {
            super.onMeasure(wms, hms);
            return;
        }
        if (gMeasuredWithWMS==wms && gMeasuredWithHMS==hms) {
            setMeasuredDimension(gMeasuredWidth,gMeasuredHeight);
            gMeasuredWithHMS = hms;
            gMeasuredWithWMS = wms;
            return;
        }
        int widthSpec = MeasureSpec.getMode(wms);
        int heightSpec = MeasureSpec.getMode(hms);
        int width = -1;
        int height = -1;
        int widthSize = MeasureSpec.getSize(wms);
        int heightSize = MeasureSpec.getSize(hms);

        // mDisplay = getText();

        if (widthSpec == MeasureSpec.EXACTLY) {
            // Must be this size
            width = Math.max(getLayoutParams().width, widthSize);
        } else if (widthSpec == MeasureSpec.AT_MOST) {
            // Can't be bigger than...
            width = widthSize;
        } else {
            // Be whatever you want
            // width = desiredWidth;
            // width = 150;
        }

        if (heightSpec == MeasureSpec.EXACTLY) {
            // Must be this size
            height = Math.max(getLayoutParams().height, heightSize);

            // throw new RuntimeException("height exactly: "+height);
        } else if (heightSpec == MeasureSpec.AT_MOST) {
            // Can't be bigger than...
            height = Math.min(getLayoutParams().height, heightSize);
            // throw new RuntimeException("height: "+height);
        } else {
            // throw new RuntimeException("height  unspecified ?");
            // Be whatever you want
            // width = desiredWidth;
        }

        int cpT = getCompoundPaddingTop();
        int cpB = getCompoundPaddingBottom();
        int cpL = getCompoundPaddingLeft();
        int cpR = getCompoundPaddingRight();

        int want = width - (cpL + cpR);

        if (height < 0) { // if WRAP_CONTENT - start layout process immediately
            mHeightWrapContent = true;
            if (mTextLayout != null) {
                if (!mTextLayout.isLayouted()) { // if we need height, and layout not invalidated yet - force invalidation
                    prepareLayout(want, -1);
                    // // mTextLayout.setSize(want, -1);
                    height = -1;
                } else if (mNeedTotalHeight) {
                    height = mTextLayout.getHeight();
                }
            } else {
                // no text layout - wait for first draw() call
            }
        } else {
            mNeedTotalHeight = false;
            if (getVisibility()==View.INVISIBLE) {
                prepareLayout(want,height-cpT-cpB);
            }
        }

        gMeasuredWidth = width;
        gMeasuredHeight = height;

        setMeasuredDimension(width, height);
    }

    protected void prepareLayout(int textLayoutWidth, int textLayoutHeight) {
        /* set geometry for layout */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (getMaxLines()>0)
                mTextLayout.setMaxLines(getMaxLines());
        }
        mTextLayout.setSize(textLayoutWidth, textLayoutHeight);
    }

    public void prepareLayout() {
        int cpT = getCompoundPaddingTop();
        int cpB = getCompoundPaddingBottom();
        int cpL = getCompoundPaddingLeft();
        int cpR = getCompoundPaddingRight();
        prepareLayout(getMeasuredWidth() - (cpL + cpR), mHeightWrapContent ? -1 : getMeasuredHeight() - (cpT + cpB));
    }

    protected void drawSelectionCursor(Canvas canvas, float x, float y, float lineHeight, boolean start) {
        Rect paddings = getOptions().getTextPaddings();
        super.drawSelectionCursor(canvas,x+paddings.left,y+paddings.top,lineHeight,start);
    }

    protected Paint debugPaint = new Paint();
    {
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setColor(Color.GREEN);
    }

    private Rect mScreenRect = new Rect();
    int[] locationOnScreen = new int[2];
    /**
     * draw text content on canvas
     *
     * @param canvas
     */

    @Override
    public void drawText(Canvas canvas) {
        Rect bounds = canvas.getClipBounds();
        getLocalVisibleRect(bounds);
        getLocationOnScreen(locationOnScreen);
        mTextLayout.draw(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    @Override
    protected void drawOverlay(Canvas canvas) {
        if (isSelectModeActive())
            super.drawAllSelectionCursors(canvas);
    }

    @Override
    protected boolean isSelectModeActive() {
        int ss = getTextLayout().getSelectionStarts();
        int se = getTextLayout().getSelectionEnds();
        if (se>ss) return true;
        return false;
    }

    /**
     * @return TextLayout instance
     */

    public TextLayout getTextLayout() {
        return mTextLayout;
    }

    /**
     * @return text content
     */

    @Override
    public CharSequence getText() { // marshmallow - called before TextLayout created
        if (isInEditMode())
            return super.getText();
        if (getTextLayout()==null) return super.getText();
        return getTextLayout().getText();
    }


    /**
     * @return selection start
     */

    @Override
    public int getSelectionStart() {
        if (mTextLayout != null)
            return mTextLayout.getSelectionStarts();
        return -1;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    @Override
    public void setSelection(int start, int end) {
        // super.setSelectionColor(getOptions().getSelectionColor());
        getTextLayout().setSelection(start, end);
    }

    /**
     * @return TextLayout selection range
     */

    @Override
    public int getSelectionEnd() {
        if (mTextLayout != null)
            return mTextLayout.getSelectionEnds();
        return -1;
    }

    /**
     * returns character position for given coordinates on layout
     * @param x         - horizontal coordinate
     * @param y         - vertical coordinate
     * @param startLine - count from line number (usually 0)
     * @return
     */

    @Override
    protected int getOffsetForCoordinates(float x, float y, int startLine) {
        Rect paddings = getOptions().getTextPaddings();
        int offset = getTextLayout().getOffsetForCoordinates(this, x - paddings.left, y - paddings.top, startLine);
        return offset;
    }

    /**
     *
     * @param position - character index in text
     * @return line number
     */
    @Override
    protected int getLineForPosition(int position) {
        return getTextLayout().getLineForPosition(position);
    }

    /**
     * called when urlspan clicked
     * @param url
     * @param position
     * @param span
     */
    @Override
    protected void onUrlClicked(String url, int position, ClickableSpan span) {
        mCatchUrl = false;
        if (mHighlightedSpan == span) {
            mCatchUrl = true;
            try {
                span.onClick(this);
            } catch (Exception e) {
//                Toast.makeText(getContext(), "could not find '" + getUrl(span) + "' handler", Toast.LENGTH_LONG).show();
            }
            mTextLayout.resetHighlight();
            mHighlightedSpan = null;
            invalidate();
        } else {
            mCatchUrl = true;
            mHighlightedSpan = span;
            Spanned text = (Spanned) mTextLayout.getText();
            int r = Color.red(mHighlightColor);
            int g = Color.green(mHighlightColor);
            int b = Color.blue(mHighlightColor);
            mTextLayout.setHighlight(text.getSpanStart(span), text.getSpanEnd(span), Color.argb(mHighlightAlpha, r, g, b));
            invalidate();
        }
    }

    /**
     * @return number of lines from TextLayout
     */

    @Override
    public int getLineCount() {
        if (isInEditMode()||mTextLayout==null) return super.getLineCount();
        return mTextLayout.getLinesCount();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (!selected) {
            mTextLayout.setSelection(0, 0);
        }
    }

    /**
     * reset state (currently - only selections)
     */
    protected void resetState() {
        setSelection(0, 0);
        setSelected(false);
        gMeasuredWithWMS = -1;
        gMeasuredWithHMS = -1;
        super.invalidateContent();
    }

    /**
     * called by TextLayout when layout invalidated
     *  (base font size changed)
     *  (lineBreaker changed)
     *  (imagePlacementHandler changed)
     *  (requested geometry changed)
     */

    @Override
    public void onTextInfoInvalidated() {
        resetState();
        if (mNeedTotalHeight) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    requestLayout();
                }
            });

        } else
            postInvalidate();
    }

    /**
     * called by TextLayout when reflow() process finished
     */
    @Override
    public void onTextReady() {
        if (isInEditMode() || mTextLayout==null) return;
        int lastLine = mTextLayout.getLinesCount();
        if (lastLine < 1) return;
        int nextCharacter = mTextLayout.getLineEnd(lastLine - 1);
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutFinished(nextCharacter);
        }

    }

    /**
     * called when given height exceed
     * WARN: called fron non-ui thread
     * @param collectedHeight
     * @return false, if no more lines required for this view
     */

    @Override
    public boolean onHeightExceed(int collectedHeight) {
        return false;
    }

    /**
     * called  by TextLayout.reflow() -> on text height changed
     */

    @Override
    public void onTextHeightChanged() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("onTextHeightChanged must be called from UI Thread");
        }
        if (mNeedTotalHeight)
            requestLayout();
        invalidate();
    }

    @Override
    public URLSpan[] getUrls() {
        Spanned text = (Spanned) getTextLayout().getText();
        return text.getSpans(0, text.length(), URLSpan.class);

    }

    private CharSequence getUrl(ClickableSpan span) {
        if (span instanceof URLSpan) {
            return ((URLSpan) span).getURL();
        } else {
            Spanned text = (Spanned) getText();
            return text.subSequence(text.getSpanStart(span), text.getSpanEnd(span));
        }
    }

    /**
     * called by touch handler, if touched on dynamicdrawablespan
     * used internally for call DynamicDrawableInteractionListener
     * @param drawable
     * @param position
     * @param dynamicDrawableSpan
     */

    @Override
    protected void onDrawableClicked(Drawable drawable, int position, DynamicDrawableSpan dynamicDrawableSpan) {
        Rect bounds = new Rect();
        if (getTextLayout()!=null && getTextLayout().getDynamicDrawableSpanRect(dynamicDrawableSpan,bounds)) {
            Log.v(TAG, "clicked on drawable: '" + drawable + "' [" + bounds + "]");
        } else {
            Log.e(TAG, "clicked on invisible drawable: " + position);
        }
        mDynamicDrawableInteractionListener.onClicked(dynamicDrawableSpan,bounds,this);
    }

    /**
     * register listener for clicks on Drawables
     * @param listener
     */

    public void setDynamicDrawableInteractionListener(DynamicDrawableInteractionListener listener) {
        if (listener==null) mDynamicDrawableInteractionListener = mDynamicDrawableInteractionListenerDefault;
        mDynamicDrawableInteractionListener = listener;
    }

    /**
     * calculate geometric bounds for line on layout
     * @param line
     * @param outBounds
     * @return
     */

    @Override
    public int getLineBounds(int line, Rect outBounds) {
        TextLayout l = getTextLayout();

        Rect p = getOptions().getTextPaddings();
        outBounds.top = l.getLineTop(line);
        outBounds.left = l.getPrimaryHorizontal(line, l.getLineStart(line));
        outBounds.right = l.getPrimaryHorizontal(line, l.getLineEnd(line));
        outBounds.bottom = l.getLineBottom(line);
        return (int) (outBounds.bottom - l.getLineDescent(line));
    }

    /**
     *
     * @param line          - lineNumber
     * @param postionAtLine - character index (in getText()). poasitionAtLine >= getLineStart(line) && positionAtLine < getLineEnd(line)
     * @param viewWidth     - current view width (required for calculating offset correctly, with justification and alignment)
     * @return
     */

    @Override
    protected float getPrimaryHorizontal(int line, int postionAtLine, int viewWidth) {
        return getTextLayout().getPrimaryHorizontal(line, postionAtLine);
    }

    /* we need completely disable original TextView call to assumeLayout() from onPreDraw() */

    @Override
    public boolean onPreDraw() {
        if (isInEditMode()) return super.onPreDraw(); // required for ide 'rendering errors'
        /* suppress original TextView onPreDraw() */
        if (mTextLayout == null) {
            return false;
        }
        if (!mTextLayout.isLayouted()) {
            prepareLayout();
        }
        return true;
    }

    private boolean mCatchUrl = false;

    @Override
    protected boolean processTouchAt(float X, float Y, boolean longTap, int startsFromLine) {
        boolean expected = mHighlightedSpan != null;
        mCatchUrl = false;
        boolean result = super.processTouchAt(X,Y,longTap,startsFromLine);
        if (expected && !mCatchUrl) { // must reset if second click misses UrlSpan
            mTextLayout.resetHighlight();
            mHighlightedSpan = null;
            invalidate();
        }
        return result;
    }

//    /* support partially drawing */
//    private boolean mIsContanerAreScrollView = false;
//    private Scroller mScroller = null;
//
//    @Override
//    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
//        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
//    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
       getViewTreeObserver().addOnScrollChangedListener(mOnScrollChangedListener);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnScrollChangedListener(mOnScrollChangedListener);
        if (mTextLayout!=null) {
            mTextLayout.release();
        }
    }

    private ViewTreeObserver.OnScrollChangedListener mOnScrollChangedListener = new ViewTreeObserver.OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
            invalidate();
        }
    };
    /*
    private void dump_lines_heights(TextLayout l) {
        int total = 0;
        for (int i=0; i<30 && i<l.getLinesCount(); i++) {
            int h = (int) l.getLineHeight(i);
            total += h;
            Log.v("LLL:","line "+i+" height="+h+" total height = "+total);
        }
        Log.v("LLL:","last line '"+lastLine(l)+"'");
    }

    private String lastLine(TextLayout l) {
        int ln = l.getLinesCount() - 1;
        int ls = l.getLineStart(ln);
        int le = l.getLineEnd(ln);
        try {
            String line = (String) getText().subSequence(ls, le).toString();
            // Log.v(TAG, "PL:"+mPosition+" last line ("+ln+") = '" + line + "'");
            return line;
        } catch (StringIndexOutOfBoundsException e) {
            // Log.e(TAG,"invalid values");
            return e.toString();
        }

    }
    */
}
