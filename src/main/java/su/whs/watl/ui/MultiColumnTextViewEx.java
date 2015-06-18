package su.whs.watl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;

import su.whs.watl.text.TextLayout;
import su.whs.watl.text.TextLayoutListener;


/**
 * Created by igor n. boulliev on 18.02.15.
 */

public class MultiColumnTextViewEx extends TextViewEx implements TextLayoutListener {
    private static final String TAG = "MCTVE";
    /* with default value - it's no difference with TextViewEx (as planned) */
    private int mMaxColumns = 1;
    private int mMinColumnWidth = -1;
    private int mMaxColumnWidth = -1;
    private int mColumnWidth = 0;
    private int mColumnSpacing = 25;
    private int mColumnsCount = 1;
    private int mTextLayoutHeight = -1;
    private int[] mColumnsVerticalShifts = null;
    private int[] mLinesHeightsOnColumns = null;
    private int[] mColumnsLinesStarts = null;

    /* reflow events handling */
    private int mColumnsReady = 0;
    private int mFirstColumnLine = 0;
    private boolean mPreferMinColumnWidth = true;
    private int mDefaultColumnsCount = 1;
    private boolean mColumnsCountChanged = false;
    private boolean mTextReady = false;

    public MultiColumnTextViewEx(Context context) {
        this(context, null, 0);
    }

    public MultiColumnTextViewEx(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */

    public MultiColumnTextViewEx(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void prepareLayout(int textLayoutWidth, int textLayoutHeight) {
        mTextLayoutHeight = textLayoutHeight;
        if (mMinColumnWidth>-1 || mMaxColumnWidth > -1) {
            calculateColumns(textLayoutWidth);
        } else if (mColumnWidth==0) {
            setColumnsCount(mColumnsCount);
            calculateColumns(getMeasuredWidth()-getCompoundPaddingLeft()-getCompoundPaddingRight());
        }
        getTextLayout().setSize(mColumnWidth, -1, textLayoutHeight);
    }

    /**
     * @param canvas
     */

    @Override
    public void drawText(Canvas canvas) {

            int left = getCompoundPaddingLeft();
            int right = getWidth() - getCompoundPaddingRight();
            int top = getCompoundPaddingTop();
            int bottom = getHeight() - getCompoundPaddingBottom();
            int columnShift = mColumnWidth + mColumnSpacing;
            /*
            if (!mRenderDataLogged) {
                Log.v(TAG,"columnShift="+columnShift);
                log_column_lines(0);
            } */

            for (int i = 0; i < mColumnsReady; i++) {
                /* if (!mRenderDataLogged) {
                    Log.v(TAG, "--draw x=" + (left + columnShift * i) + " startLine=" + mColumnsLinesStarts[i]);
                    log_column_lines(i);
                } */
                getTextLayout().draw(canvas, left + columnShift * i,
                        mColumnsVerticalShifts[i] + top,
                        right + columnShift * i,
                        mColumnsVerticalShifts[i] + bottom, mColumnsLinesStarts[i], i+1<mColumnsCount ? mColumnsLinesStarts[i+1] : getLineCount());
            }
            mRenderDataLogged = true;
        canvas.drawRect(debugClickedLineBound,debugPaint);
     }

    private void log_column_lines(int column) {
        int start = mColumnsLinesStarts[column];
        int end = column < mColumnsCount-1 ? mColumnsLinesStarts[column+1] : getLineCount();

        float height = 0f;
        for (int i=start; i<end; i++) {
            log_single_line(i);
            height += getTextLayout().getLineHeight(i);
        }
        Log.d(TAG, "column [" + column + "] lines [" + start + "-" + end + "] height=" + height);
    }

    private void log_single_line(int line) {
        int ls = getTextLayout().getLineStart(line);
        int le = getTextLayout().getLineEnd(line);
        Log.v(TAG, "line[" + line + "] '" + getText().subSequence(ls, le) + "'");
    }

    @Override
    protected void drawAllSelectionCursors(Canvas canvas) {
        super.drawAllSelectionCursors(canvas);
    }

    @Override
    public void onMeasure(int wms, int hms) {
        super.onMeasure(wms, hms);
    }

    @Override
    protected boolean moveCursor(float x, float y, int zero) {
        PointF p = new PointF();
        int startLine = translateCoordinates(x, y, p);
        return super.moveCursor(p.x, p.y, startLine);
    }

    @Override
    protected void jumpSelectionCursor(float x, float y, int zero) {
        PointF p = new PointF();
        int startLine = translateCoordinates(x, y, p);
        super.jumpSelectionCursor(p.x, p.y, startLine);
    }

    /* may be, implement all cursor and drawing methods with 'startWithLine' argument ? so we don't need complex translation */
    @Override
    protected void processTouchAt(float x, float y, boolean longTap, int zero) {
        PointF p = new PointF();
        int startLine = translateCoordinates(x, y, p);
        super.processTouchAt(p.x, p.y, longTap, startLine);
    }

    /*
        translate coordinates to virtual single column
     */

    private int translateCoordinates(float x, float y, PointF translated) {
        int left = getCompoundPaddingLeft();
        int top = getCompoundPaddingTop();
        int startLine = 0;
        int actualColumnWidth = mColumnWidth + mColumnSpacing;
        if (x > (left + actualColumnWidth)) {
            int atColumn = (int) ((x - left) / actualColumnWidth);
            translated.x = ((x - left) % actualColumnWidth);
            translated.y = y;
            for (int i = 0; i < atColumn; i++) {
                // translated.y += mLinesHeightsOnColumns[i];
            }
            startLine = mColumnsCount > 1 ? mColumnsLinesStarts[atColumn] : 0;
        } else {
            translated.x = x;
            translated.y = y;
        }
        return startLine;
    }

    /*


     */

    public void setColumnsCount(int forcedColumnCount) {
        // Log.v(TAG,""+this+" setColumnsCount " + forcedColumnCount);
        mColumnsCount = forcedColumnCount;
        mColumnsCountChanged = true;
    }

    private void calculateColumns2(int width) {
        int fits = mColumnsCount;
        if (mMaxColumnWidth > -1 && mMaxColumnWidth < width) {
            fits = 1 + (width-mColumnSpacing) / (mMaxColumnWidth+mColumnSpacing);
        } else if (mMinColumnWidth > -1) {
            fits = (width-mColumnSpacing) / mMinColumnWidth;
            if (fits<1) fits = 1;
        }

        if (fits!=mColumnsCount) {
            mColumnsCountChanged = false;
            if (mColumnsLinesStarts!=null) {
                // Log.w(TAG,"re-initialization required");
            } else {
                mColumnsVerticalShifts = new int[mColumnsCount];
                mLinesHeightsOnColumns = new int[mColumnsCount];
                mColumnsLinesStarts = new int[mColumnsCount];
            }
            int q = (width / fits);
            if (q > 1) {
                Rect textPaddings = getOptions().getTextPaddings();
                int spacingSum = (mColumnSpacing+textPaddings.left+textPaddings.right) * (mColumnsCount - 1);
                mColumnWidth = (fits - spacingSum) / mColumnsCount;
            } else {
                mColumnWidth = q; // q = 1, so no spacing
            }
        }
    }

    private void calculateColumns(int textLayoutWidth) {
        if (mTextReady)
            Log.e(TAG,"calculate columns _after_ onTextReady()");
        if (mColumnsCount==0) {
            Log.e(TAG,"error - columns count are zero");
        }
        mColumnsCountChanged = false;

        if (mColumnsLinesStarts!=null) {
            // Log.w(TAG,"re-initialization required");
        } else {
            mColumnsVerticalShifts = new int[mColumnsCount];
            mLinesHeightsOnColumns = new int[mColumnsCount];
            mColumnsLinesStarts = new int[mColumnsCount];
        }
        int q = (textLayoutWidth / mColumnsCount);
        if (q > 1) {
            Rect textPaddings = getOptions().getTextPaddings();
            int spacingSum = (mColumnSpacing+textPaddings.left+textPaddings.right) * (mColumnsCount - 1);
            mColumnWidth = (textLayoutWidth - spacingSum) / mColumnsCount;
        } else {
            mColumnWidth = q; // q = 1, so no spacing
        }
    }

    /* used to autmatic split text's to columns */

    public void setColumnLimits(int minWidth, int maxWidth) {
        mMinColumnWidth = minWidth;
        mMaxColumnWidth = maxWidth;
        mColumnsCountChanged = true;
    }

    @Override
    public void onTextReady() {
        mTextReady = true;
        // Log.v(TAG,""+this+" onTextReady");
        super.onTextReady();
        // TODO: need to calculate individual columns vertical shift to make text more accuracy
        // arrange by most frequent horizontals
        // we need actual heights (sum of line.height for each column
        if (getLineCount() > 0) {
            int accumulator = 0;
            // TODO: at least mLinesHeightsOnColumns required for translate coordinates
        }
    }


    @Override
    public boolean onHeightExceed(int collectedHeight) {
        if (mTextReady)
            Log.e(TAG,"error - onHeightExceed received _after_ onTextReady()");
        //Log.v(TAG,""+this+" onHeightExceed");
        synchronized (this) {
            if (mColumnsReady < mColumnsCount) {
                mColumnsLinesStarts[mColumnsReady] = mFirstColumnLine;
                //Log.v(TAG,"column "+mColumnsReady+" line starts = " + mFirstColumnLine);
                if (mColumnsReady > 0 && mFirstColumnLine == 0)
                    throw new RuntimeException("!!!!!");
                /*
                int ln = getLineCount() - 1;
                int ls = getTextLayout().getLineStart(ln);
                int le = getTextLayout().getLineEnd(ln);
                try {
                    Log.v(TAG, "last line = '" + getText().subSequence(ls, le) + "'");
                } catch (StringIndexOutOfBoundsException e) {
                    Log.e(TAG,"invalid values");
                } */
                mFirstColumnLine = getLineCount();
                mLinesHeightsOnColumns[mColumnsReady] = collectedHeight;
                mColumnsReady++;
            }
        }
        return mColumnsReady < mColumnsCount; // continue process
    }

    @Override
    public void setTextLayout(TextLayout textLayout) {
        super.setTextLayout(textLayout);
    }

    /**
     *
     */

    protected void drawSelectionCursor(Canvas canvas, float x, float y, float lineHeight, boolean start) {
        /*
        Rect paddings = getOptions().getTextPaddings();
        int height = getMeasuredHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
        int shift = (int) (y / height);
        int mod = (int)y % height;
        int columnWidthWithSpacing = mColumnWidth + mColumnSpacing;
        */
        super.drawSelectionCursor(canvas, x /* + columnWidthWithSpacing * shift */, y /* mod */, lineHeight, start);
    }

    private int storedWidth = 0;

    @Override
    public void onTextInfoInvalidated() {
        mTextReady = false;
        // Log.v(TAG,""+this+" onTextInfoInvalidated");
        synchronized (this) {
            mColumnsReady = 0;
            mFirstColumnLine = 0;
            if (storedWidth==0) // FIXME: dirty hack
                storedWidth = getMeasuredWidth()-getCompoundPaddingRight()-getCompoundPaddingLeft();
            if (storedWidth==0)
                storedWidth = getTextLayout().getWidth() * mColumnsCount + (mColumnSpacing*(mColumnsCount-1));
            calculateColumns(storedWidth);
        }
        super.onTextInfoInvalidated();
    }

    @Override
    protected float getPrimaryHorizontal(int line, int postionAtLine, int viewWidth) {
        float primary = super.getPrimaryHorizontal(line,postionAtLine, mColumnWidth);
        int shift = 0;
        for (int i=1; i < mColumnsCount; i++) {
            if (line<mColumnsLinesStarts[i]) {
                break;
            }
            shift++;
        }
        return primary + (mColumnWidth+mColumnSpacing) * shift;
    }

    @Override
    public int getLineBounds(int line, Rect bounds) {
        int baseLine = super.getLineBounds(line, bounds);
        int paddingTop = getOptions().getTextPaddings().top;
        int deltaY = 0;
        int deltaX = 0;
        int height = getMeasuredHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mColumnsCount > 1) {
            int column = 1;
            while (column < mColumnsCount && line > mColumnsLinesStarts[column]) {
                column++;
            }

            for (int i = 1; i < column; i++) {
                deltaY += mLinesHeightsOnColumns[i] - paddingTop;
                deltaX += mColumnWidth + mColumnSpacing;
            }
        }
        bounds.left += deltaX;
        bounds.right += deltaX;
        bounds.top -= deltaY;
        bounds.bottom -= deltaY;
        return baseLine - deltaY;
    }

    /*
    private void calculateColumns(int width) {

        int fits = mDefaultColumnsCount;
        if (mMaxColumnWidth > -1 && mMaxColumnWidth < width) {
            fits = 1 + width / mMaxColumnWidth;
            if (mMinColumnWidth > -1) {
                int columnWidth = (width-mColumnSpacing*fits) / fits;
                if (columnWidth<mMinColumnWidth) {
                    if (mPreferMinColumnWidth) {
                        fits = width / mMinColumnWidth;
                    }
                }

            } else {
                // only mMaxColumnWidth defined and it's less than width, so at least 2 columns

            }
        } else {
            if (mMinColumnWidth > -1) {
                fits = width / mMinColumnWidth;
            }

        }
        setColumnsCount(fits);
        calculateColumns();
    } */

    private boolean mRenderDataLogged = false;

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRenderDataLogged = false;
    }

}
