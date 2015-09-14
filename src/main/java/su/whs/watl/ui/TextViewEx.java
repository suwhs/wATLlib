package su.whs.watl.ui;

/* copyright (c) 2014-2015 igor n. boulliev <igor@whs.su> */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
import android.widget.Toast;

import su.whs.watl.BuildConfig;
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
    private ContentView.Options mPendingOptions = new ContentView.Options();
    private boolean mDebug = BuildConfig.DEBUG;

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

    private boolean mNeedTotalHeight = true;

    private ImagePlacementHandler mImagePlacementHandler = new ImagePlacementHandler.DefaultImagePlacementHandler();
    // default dynamicdrawablespan interaction listener - just make toast
    private DynamicDrawableInteractionListener mDynamicDrawableInteractionListenerDefault = new DynamicDrawableInteractionListener() {
        @Override
        public void onClicked(DynamicDrawableSpan span, RectF bounds, View view) {
            Toast.makeText(getContext(), "clicked " + span.getDrawable(), Toast.LENGTH_LONG).show();
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
        mPendingOptions.fromAttributes(context,attrs,defStyle,0);
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
        return mTextLayout==null ? mPendingOptions : mTextLayout.getOptions();
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

        if (mTextLayout==null && mPendingOptions == null) {
                mPendingOptions = new ContentView.Options();
        }

        mTextLayout = new TextLayout((Spanned) text, 0, text.length(), getPaint(),
                getOptions() ,
                this);
        mTextLayout.getOptions().setImagePlacementHandler(mImagePlacementHandler);
        if (mHeightWrapContent)
            requestLayout();
        else
            invalidate();
    }

    /**
     *
     * @param textLayout
     */

    public void setTextLayout(TextLayout textLayout) {
        if (textLayout == null) throw new IllegalArgumentException("textLayout must be not null");
        mTextLayout = textLayout;
        mTextLayout.setInvalidateListener(this);
        postInvalidate();
    }

    @Override
    public void setHighlightColor(int color) {
        mHighlightColor = color;
    }

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

        if (height < 0) { // if WRAP_CONTENT - sttart layout process immediately
            mHeightWrapContent = true;
            if (mTextLayout != null) {
                if (!mTextLayout.isLayouted()) {
                    prepareLayout(want, -1);
                    // mTextLayout.setSize(want, -1);
                    height = -1;
                } else if (mNeedTotalHeight) {
                    height = mTextLayout.getHeight();
                }
            } else {

            }
        } else {
            mNeedTotalHeight = false;
            if (getVisibility()==View.INVISIBLE) {
                prepareLayout(want,height-cpT-cpB);
            }
        }
        setMeasuredDimension(width, height);
    }

    protected void prepareLayout(int textLayoutWidth, int textLayoutHeight) {
        /* set geometry for layout */
        mTextLayout.setSize(textLayoutWidth, textLayoutHeight);
    }

    public void prepareLayout() {
        int cpT = getCompoundPaddingTop();
        int cpB = getCompoundPaddingBottom();
        int cpL = getCompoundPaddingLeft();
        int cpR = getCompoundPaddingRight();
        prepareLayout(getMeasuredWidth() - (cpL + cpR), getMeasuredHeight() - (cpT + cpB));
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

    /**
     * draw text content on canvas
     *
     * @param canvas
     */
    @Override
    public void drawText(Canvas canvas) {
        int left = getCompoundPaddingLeft();
        int right = getWidth() - getCompoundPaddingRight();
        int top = getCompoundPaddingTop();
        int bottom = getHeight() - getCompoundPaddingBottom();
        mTextLayout.draw(canvas, left, top, right, bottom);
    }

    @Override
    protected void drawOverlay(Canvas canvas) {
        if (isSelectModeActive())
            super.drawAllSelectionCursors(canvas);
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
     *
     * @param x         - horizontal coordinate
     * @param y         - vertical coordinate
     * @param startLine - count from line number (usually 0)
     * @return
     */

    @Override
    protected int getOffsetForCoordinates(float x, float y, int startLine) {
        Rect paddings = getOptions().getTextPaddings();
        int offset = getTextLayout().getOffsetForCoordinates(this, x - paddings.left, y - paddings.top, startLine);
        // int line = getTextLayout().getLineForVertical((int)y);
        // Log.v(TAG,"x,y = ("+x+","+y+") for line="+line);
        // if (line>-1) getLineBounds(line,debugClickedLineBound);
        return offset;
    }

    @Override
    protected int getLineForPosition(int position) {
        return getTextLayout().getLineForPosition(position);
    }

    @Override
    protected void onUrlClicked(String url, int position, ClickableSpan span) {
        if (mHighlightedSpan == span) {
            Log.v(TAG, "clicked on url: '" + url + "'");
            try {
                span.onClick(this);
            } catch (Exception e) {
                Toast.makeText(getContext(), "could not find '" + getUrl(span) + "' handler", Toast.LENGTH_LONG).show();
            }
            mTextLayout.resetHighlight();
            mHighlightedSpan = null;
            invalidate();
        } else {
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
     * @return true, if 'full text justification' enabled
     */


    /**
     * @return number of lines from TextLayout
     */

    @Override
    public int getLineCount() {
        return mTextLayout.getLinesCount();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (!selected) {
            mTextLayout.setSelection(0,0);
        }
    }

    protected void resetState() {
        setSelection(0, 0);
        setSelected(false);
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
        // Log.v(TAG, "onTextInfoInvalidated()");
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

    @Override
    public void onTextReady() {
        // Log.v(TAG, "onTextReady");

        int lastLine = mTextLayout.getLinesCount();
        if (lastLine < 1) return;
        int nextCharacter = mTextLayout.getLineEnd(lastLine - 1);
        /*
        int firstCharacter = mTextLayout.getLineStart(lastLine - 1);
        String lastLineStr = getText().subSequence(firstCharacter, nextCharacter).toString();
        Log.v(TAG, "last line: '" + lastLineStr + "'");
        */
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutFinished(nextCharacter);
        }
    }

    /**
     * called fron non-ui thread
     * @param collectedHeight
     * @return
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
        else
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

    @Override
    protected void onDrawableClicked(Drawable drawable, int position, DynamicDrawableSpan dynamicDrawableSpan) {
        // Log.v(TAG, "clicked on drawable: '" + drawable + "'");
        RectF bounds = null;
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

    public class Options extends ContentView.Options {

    }

    @Override
    public int getLineBounds(int line, Rect outBounds) {
        TextLayout l = getTextLayout();

        Rect p = getOptions().getTextPaddings();
        outBounds.top = l.getLineTop(line);
        outBounds.left = (int) l.getPrimaryHorizontal(line, l.getLineStart(line));
        outBounds.right = (int) l.getPrimaryHorizontal(line, l.getLineEnd(line));
        outBounds.bottom = l.getLineBottom(line);
        return (int) (outBounds.bottom - l.getLineDescent(line));
    }


    @Override
    protected float getPrimaryHorizontal(int line, int postionAtLine, int viewWidth) {
        return getTextLayout().getPrimaryHorizontal(line, postionAtLine);
    }

    /* we need completely disable original TextView call to assumeLayout() from onPreDraw() */

    @Override
    public boolean onPreDraw() {
        if (isInEditMode()) return super.onPreDraw(); // required for ide 'rendering errors'
        /* suppress original TextView onPreDraw() */
        if (mTextLayout == null) {;
            return false;
        }
        if (!mTextLayout.isLayouted()) {
            prepareLayout();
        }
        return true;
    }

    @Override
    protected void processTouchAt(float X, float Y, boolean longTap, int startsFromLine) {
        super.processTouchAt(X,Y,longTap,startsFromLine);
    }

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
