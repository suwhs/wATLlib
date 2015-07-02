package su.whs.watl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
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
    private boolean mRecalculateHeightOnFinish = false;
    private boolean mAutoHeightCalculated = false;
    private int mAutoHeight = 0;

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
        if (mColumnWidth<1) {
            if (mColumnsCount==1 && (mMinColumnWidth>-1 || mMaxColumnWidth > -1)) {
                mColumnsCount = determineColumnsCount(mMinColumnWidth,mMaxColumnWidth,textLayoutWidth);
                setColumnsCount(mColumnsCount);
                calculateColumns(textLayoutWidth);
            } else if (mColumnWidth==0) {
                setColumnsCount(mColumnsCount);
                calculateColumns(getMeasuredWidth()-getCompoundPaddingLeft()-getCompoundPaddingRight());
            }
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

            for (int i = 0; i < mColumnsReady; i++) {
                getTextLayout().draw(canvas, left + columnShift * i,
                        mColumnsVerticalShifts[i] + top,
                        right + columnShift * i,
                        mColumnsVerticalShifts[i] + bottom, mColumnsLinesStarts[i], i+1<mColumnsCount ? mColumnsLinesStarts[i+1] : getLineCount());
            }
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
        // Log.d(TAG, "column [" + column + "] lines [" + start + "-" + end + "] height=" + height);
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
        if (mAutoHeightCalculated) {
            hms = MeasureSpec.makeMeasureSpec(mAutoHeight,MeasureSpec.EXACTLY);
        }
        super.onMeasure(wms, hms);
        int heightSpec = MeasureSpec.getMode(hms);
        if (heightSpec == MeasureSpec.UNSPECIFIED) {
            mRecalculateHeightOnFinish = true;
        }
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
        // FIXME: ERROR HERE - wrong line number returned

        // we need to lookup correct columns first, than substract lines height for previous columns
        // and increment y/decrement x carefully
        int layoutWidth = mColumnWidth+mColumnSpacing;
        int viewWidth = getMeasuredWidth() - getCompoundPaddingLeft() - getCompoundPaddingTop();
        int viewHeight = getTextLayout().getHeight();
        int column = 0;
        if (x>0) {
            column = (int) (x / layoutWidth);
        }
        int startLine = column < mColumnsCount ? mColumnsLinesStarts[column] : mColumnsCount-1;

        translated.x = x - (column*layoutWidth);
        translated.y = y;
        for (int i=0; i< column; i++, translated.y+=mLinesHeightsOnColumns[i]);

        return startLine;
    }

    /*


     */

    public void setColumnsCount(int forcedColumnCount) {
        // Log.v(TAG,""+this+" setColumnsCount " + forcedColumnCount);
        mColumnsCount = forcedColumnCount;
        mColumnsCountChanged = true;
    }

    private int determineColumnsCount(int minColumnWidth, int maxColumnWidth, int viewWidth) {
        if (maxColumnWidth<0) {
            if (minColumnWidth<0) {
                Log.w(TAG,"columns size limits < 0, determineColumnsCount() should be called only if at least one argument > 0");
                return 1;
            }
            if (minColumnWidth<viewWidth/2)
                return viewWidth / minColumnWidth;
            return 1;
        }
        if (maxColumnWidth<viewWidth/2)
            return viewWidth / maxColumnWidth;

        if (minColumnWidth>0 && minColumnWidth<viewWidth/2)
            return viewWidth / minColumnWidth;

        return 1;
    }

    private void calculateColumns(int textLayoutWidth) {
        // textLayoutWidth == measured width - compound paddings
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
        super.onTextReady();
        // TODO: need to calculate individual columns vertical shift to make text more accuracy
        // arrange by most frequent horizontals
        // we need actual heights (sum of line.height for each column
        if (mRecalculateHeightOnFinish && getLineCount() > 0) {
            int counter = 0;
            int accumulator = 0;
            mColumnsReady = 1;

            int totalHeight = getTextLayout().getHeight();
            int requiredHeight = totalHeight / mColumnsCount;
            int maxRequiredHeight = requiredHeight;

            TextLayout.LinesIterator iter = getTextLayout().getLinesIterator();
            int[] linesHeights = new int[iter.getSize()];
            int processedHeight = 0;

            for (; iter.hasNext(); iter.next()) {
                // increase height of current column until left_height / left_columns_count > require
                int height = iter.getHeight();
                linesHeights[counter] = height;
                if (accumulator+height > maxRequiredHeight) {
                    int leftColumns = mColumnsCount-mColumnsReady;
                    if (leftColumns*maxRequiredHeight<totalHeight-processedHeight) {
                        maxRequiredHeight+=height;
                    } else {
                        mLinesHeightsOnColumns[mColumnsReady-1] = accumulator;
                        mColumnsLinesStarts[mColumnsReady] = counter;
                        mColumnsReady++;
                        accumulator = 0;
                    }
                }
                processedHeight+= height;
                accumulator+=height;
                counter++;
            }
            mAutoHeightCalculated = true;
            mAutoHeight = maxRequiredHeight;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    requestLayout();
                }
            });
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
            mAutoHeightCalculated = false;
            mAutoHeight = 0;
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

    /**
     * used for display selection cursors correctly
     *
     * @param line
     * @param bounds
     * @return y-coordinate of lines (top of line rect)
     */


    @Override
    public int getLineBounds(int line, Rect bounds) {
        Log.d(TAG,"getLineBounds("+line+",bounds");
        int paddingTop = getOptions().getTextPaddings().top;
        int deltaY = 0;
        int deltaX = 0;
        int baseLine;
        if (mColumnsCount > 1) {
            int column = 0;
            // column last line calc as mColumnLineStarts[column+1]-1
            for (;column<mColumnsCount-1 && line>mColumnsLinesStarts[column+1]-1; column++)
            if (column>mColumnsCount) {
                Log.w(TAG,"error calculating column for line");
                return -1;
            }
            for (int i = 0; i < column; i++) {
                deltaY += mLinesHeightsOnColumns[i] - paddingTop;
                deltaX += mColumnWidth + mColumnSpacing;
            }
            baseLine = super.getLineBounds(line, bounds);
        } else {
            baseLine = super.getLineBounds(line, bounds);
        }
        bounds.left += deltaX;
        bounds.right += deltaX;
        bounds.top -= deltaY;
        bounds.bottom -= deltaY;
        return baseLine - deltaY;
    }
}
