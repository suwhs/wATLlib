package su.whs.watl.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import su.whs.watl.text.TextInfoInvalidateListenerExt;

/**
 * Created by igor n. boulliev on 16.05.15.
 *
 */
public class TextPagesView extends MultiColumnTextViewEx implements TextInfoInvalidateListenerExt {

    private float mFlipState = 1f;
    private boolean mFlipping = false;
    private boolean mDragging = false;
    private Bitmap mFadeInPage = null;
    private Bitmap mFadeOutPage = null;
    private int mPageWidth = 0;
    private int mPageHeight = 0;
    private Bitmap.Config mCacheConfig = Bitmap.Config.RGB_565;
    private int mFlipTimeLimitMillisec = 100;
    private float mFlipDistanceTreshold = 0.3f;

    public interface PageFlipPainter {
        void drawFrame(Canvas canvas, Bitmap fadeIn, Bitmap fadeOut, float position);
    }

    private class DefaultPageFlipPainter implements PageFlipPainter {
        @Override
        public void drawFrame(Canvas canvas, Bitmap fadeIn, Bitmap fadeOut, float position) {

        }
    }

    private PageFlipPainter mPageFlipPainter = new DefaultPageFlipPainter();

    public TextPagesView(Context context) {
        super(context);
    }

    public TextPagesView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextPagesView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * states - dragging
     *  - flipping (dragging finished, animate to fadeIn/fadeOut)
     *  - flip time (ms, default = 100)
     *
     */

    private void prepareBitmapCaches() {
        releaseBitmapCaches();
        if (mFadeInPage==null)
            mFadeInPage = Bitmap.createBitmap(mPageWidth,mPageHeight,mCacheConfig);
        if (mFadeOutPage==null)
            mFadeOutPage = Bitmap.createBitmap(mPageWidth,mPageHeight,mCacheConfig);
    }

    private void releaseBitmapCaches() {
        if (mFadeInPage!=null && !mFadeInPage.isRecycled())
            mFadeInPage.recycle();
        if (mFadeOutPage!=null && !mFadeOutPage.isRecycled())
            mFadeOutPage.recycle();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mFlipping) {
            if (mFlipState<1f) {

            } else {

            }
        }
    }

    private void drawPageToCanvas(Canvas canvas, int page) {

    }

    private void onStartDrag() {
        mDragging = true;
        if (!mFlipping) mFlipping = true;
    }

    private void onFinishDrag() {
        mDragging = false;
        calculateVelocity();
    }

    private void animateNext() {
        if (!mDragging) {

        }
    }

    private void calculateVelocity() {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event)  {
        /* detect drag event and turn mFlipping on/off, call invalidate() if need */
        return false;
    }

    @Override
    public boolean onHeightExceed(int collectedHeight) {
        return super.onHeightExceed(collectedHeight);
        /*
        synchronized (this) {
            if (mColumnsReady < mColumnsCount) {
                mColumnsLinesStarts[mColumnsReady] = mFirstColumnLine;
                int ln = getLineCount() - 1;
                int ls = getTextLayout().getLineStart(ln);
                int le = getTextLayout().getLineEnd(ln);
                Log.v(TAG, "last line = '" + getText().subSequence(ls, le) + "'");
                mFirstColumnLine = getLineCount();
                mLinesHeightsOnColumns[mColumnsReady] = collectedHeight;
                mColumnsReady++;
            }
        }
        return mColumnsReady < mColumnsCount; // continue process
        */
    }

}
