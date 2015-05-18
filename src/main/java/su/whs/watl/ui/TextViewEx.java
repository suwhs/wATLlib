package su.whs.watl.ui;

/* copyright (c) 2014-2015 igor n. boulliev <igor@whs.su> */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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

import su.whs.watl.text.DynamicDrawableInteractionListener;
import su.whs.watl.text.ImagePlacementHandler;
import su.whs.watl.text.ContentView;
import su.whs.watl.text.TextInfoInvalidateListener;
import su.whs.watl.text.TextLayout;


// import android.support.v7.view.ActionMode;

/**
 * TextViewEx - drop-in replacement for TextView
 * additional features:
 * - supports LineBreaker for line breaks
 * - supports full justification for spanned strings
 * - supports OverflowListener
 * - supports two-tap click on urls
 * - supports hyphenation (see sample application)
 */

public class TextViewEx extends TextViewWS implements TextInfoInvalidateListener, ITextView {
    private static final String TAG = "TextViewEx";
    private TextLayout mTextLayout;

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
        //   Log.v(TAG, "create " + this);
    }

    @Override
    public void setTextSize(float size) {
        // if (!mFallBackMode && mTextLayout != null)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    @Override
    public void setTextSize(int unit, float size) {
        float value = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, getResources().getDisplayMetrics());
        mTextLayout.setTextSize(value);
    }

    @Override
    public ContentView.Options getOptions() {
        return mTextLayout.getOptions();
    }

    /*
    public void setJustificationEnabled(boolean enabled) {
        if (mFallBackMode) return;
        if (mTextLayout != null) mTextLayout.getOptions().enableJustification(enabled);
        // enableJustification(enabled);
        //  mJustification = enabled;
        postInvalidate();
    } */

    @Override
    public void setText(CharSequence _text, BufferType type) {
        if (isInEditMode()) {
            super.setText(_text, type);
            return;
        }
        CharSequence text = _text instanceof Spanned ? _text : new SpannableString(_text);

        super.setText("", BufferType.NORMAL);
        if (_text == null || _text.length() < 1) return;
        if (mTextLayout != null) {
            mTextLayout.release();
        }
        mTextLayout = new TextLayout((Spanned) text, 0, text.length(), getPaint(), new ContentView.Options(), this);
        mTextLayout.getOptions().setImagePlacementHandler(mImagePlacementHandler);
        // mTextLayout.setImagePlacementHandler(mImagePlacementHandler);
        // if (mLineBreaker != null)
        //    mTextLayout.getOptions().setLineBreaker(mLineBreaker);
        requestLayout();
    }

    /*
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mTextLayout != null)
            mTextLayout.release();
    }
    */

    private int mSetLayoutCounter = 0;

    public void setTextLayout(TextLayout textLayout) {
        if (textLayout == null) throw new IllegalArgumentException("textLayout must be not null");
        mSetLayoutCounter++;
        mTextLayout = textLayout;
        mTextLayout.setPaint(getPaint());
        mTextLayout.setInvalidateListener(this);
        requestLayout();
    }

    /* MOVED TO Options
    /**
     * set or remove line breaker
     *
     * @param lineBreaker
     *


    public TextViewEx setLineBreaker(LineBreaker lineBreaker) {
        // mLineBreaker = lineBreaker;
        if (mTextLayout != null) {
            mTextLayout.getOptions().setLineBreaker(lineBreaker);
        }
        return this;
    } */

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
            if (!mTextLayout.isLayouted()) {
                prepareLayout(want, -1);
                // mTextLayout.setSize(want, -1);
                height = -1;
            } else if (mNeedTotalHeight) {
                height = mTextLayout.getHeight();
            }
        } else {
            mNeedTotalHeight = false;
        }
        // mTextLayout.setSize(want, height > 0 ? (height - cpT - cpB) : -1);

        // if (height < 0) // if we need layouted text height
        //    height = mTextLayout.getHeight();

        setMeasuredDimension(width, height);
    }

    protected void prepareLayout(int textLayoutWidth, int textLayoutHeight) {
        /* set geometry for layout */
        mTextLayout.setSize(textLayoutWidth, textLayoutHeight);
    }

    private boolean attemptToDrawNullLayout = false;
    /**
     * draw text content on canvas
     *
     * @param canvas
     */
    @Override
    public void drawText(Canvas canvas) {
        // actually, background already painted, and drawables also painted
        // Log.v(TAG,""+this+"drawText()");
        if (mTextLayout == null) {
            Log.w(TAG, "mTextLayout are null for = " + this + " setLayoutCounter=" + mSetLayoutCounter);
            return;
        }
        if (!mTextLayout.isLayouted()) {
            int cpT = getCompoundPaddingTop();
            int cpB = getCompoundPaddingBottom();
            int cpL = getCompoundPaddingLeft();
            int cpR = getCompoundPaddingRight();
            /**
             * TODO: need small changes to supports MultiColumnTextView draw
             */
            /*
            if (mTextLayout == null) {
                Log.v(TAG, "pending layout reset?");
                return;
            } */
            Log.v(TAG, "prepare layout, no draw? (" + mTextLayout + ")");
            prepareLayout(getMeasuredWidth() - (cpL + cpR), getMeasuredHeight() - (cpT + cpB));
        }
        int left = getCompoundPaddingLeft();
        int right = getWidth() - getCompoundPaddingRight();
        int top = getCompoundPaddingTop();
        int bottom = getHeight() - getCompoundPaddingBottom();
        // Log.v(TAG, String.format("onDraw() left= %d, top = %d, right = %d, bottom = %d", left, top, right, bottom));
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
    public CharSequence getText() {
        if (isInEditMode())
            return super.getText();
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
    public void setSelection(int start, int end, int color) {
        getTextLayout().setSelection(start, end, color);
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

    @Override
    protected int getOffsetForCoordinates(float x, float y, int startLine) {
        return getTextLayout().getOffsetForCoordinates(this, x, y, startLine);
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


    /*
    @Override
    public boolean isTextSelectable() {
        return true;
    }
    */
    /*
    @Override
    public void setTextIsSelectable(boolean selectable) {
        if (mFallBackMode) {
            super.setTextIsSelectable(selectable);
        } else if (mTextIsSelectable != selectable) {
            if (mTextIsSelectable) {
                onSelectionModeEnds();
            }
            mTextIsSelectable = selectable;
        }
    } */


    /**
     * @return number of lines, holded by TextLayout
     */

    @Override
    public int getLineCount() {
        return mTextLayout.getLinesCount();
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
        Log.v(TAG, "onTextInfoInvalidated()");
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("onTextInfoInvalidated must be called from UI Thread!");
        }
        setSelection(0, 0, 0);
        if (mNeedTotalHeight)
            requestLayout();
        else
            invalidate();
    }

    @Override
    public void onTextReady() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("onTextReady() must be called from UI Thread!");
        }
        Log.v(TAG, "onTextReady");
        int lastLine = mTextLayout.getLinesCount();
        if (lastLine < 1) return;
        int nextCharacter = mTextLayout.getLineEnd(lastLine - 1);
        int firstCharacter = mTextLayout.getLineStart(lastLine - 1);
        String lastLineStr = getText().subSequence(firstCharacter, nextCharacter).toString();
        Log.v(TAG, "last line: '" + lastLineStr + "'");
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutFinished(nextCharacter);
        }
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
        else invalidate();
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

    protected void onDrawableClicked(Drawable drawable, int position, DynamicDrawableSpan dynamicDrawableSpan) {
        Log.v(TAG, "clicked on drawable: '" + drawable + "'");
    }

    public class Options extends ContentView.Options {

    }

    @Override
    public int getLineBounds(int line, Rect outBounds) {
        TextLayout l = getTextLayout();
        outBounds.top = l.getLineTop(line);
        outBounds.left = (int) l.getPrimaryHorizontal(line, l.getLineStart(line), getWidth());
        outBounds.right = (int) l.getPrimaryHorizontal(line, l.getLineEnd(line), getWidth());
        outBounds.bottom = l.getLineBottom(line);
        return (int) (outBounds.bottom - l.getLineDescent(line));
    }

    @Override
    protected float getPrimaryHorizontal(int line, int postionAtLine, int viewWidth) {
        return getTextLayout().getPrimaryHorizontal(line, postionAtLine, getWidth());
    }

    /* we need completely disable original TextView call to assumeLayout() from onPreDraw() */
    @Override
    public boolean onPreDraw() {
        if (isInEditMode()) return super.onPreDraw(); // required for ide 'rendering errors'
        /* suppress original TextView onPreDraw() */
        return true;
    }
}
