package su.whs.watl.text;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextPaint;

/**
 * Created by igor n. boulliev on 10.02.15.
 */
public interface ContentView {

    interface OptionsChangeListener {
        void invalidateMeasurement(); // full reflow required
        void invalidateLines(); // lines distribution changed
        void invalidate(); // simple repaint
        void setTextSize(float size);
        TextPaint getTextPaint();
    }

    class Options {
        private static final String FILTER_EMPTY_LINES_ENABLED = "FELE";
        private static final String JUSTIFICATION_ENABLED = "JE";
        private static final String DEFAULT_DIRECTION = "DD";
        private static final String DRAWABLE_PADDINGS = "DP";
        private static final String IMAGE_PLACEMENT_HANDLER_CLASS = "IPHCLS";
        private static final String LINE_BREAKER_CLASS = "LBCLS";
        private static final String REFLOW_TIME_QUANT_MS = "RTQMS";
        private static final String LINESPACING_MULTIPLIER = "LSMUL";
        private static final String LINESPACING_ADD = "LSADD";
        private static final String EMPTY_LINES_HEIGHT = "ELH";
        private static final String EMPTY_LINES_TRESHOLD = "ELT";
        private static final String PARAGRAPH_MARGIN_LEFT = "PMLFT";
        private static final String PARAGRAPH_MARGIN_TOP = "PMTOP";
        private static final String TEXT_PADDINGS = "TPAD";

        private boolean mFilterEmptyLines = true;
        private boolean mJustification = true;
        private int mDefaultDirection = 0;
        private Rect mDrawablePaddings = new Rect(5, 0, 5, 0);
        private ImagePlacementHandler mImagePlacementHandler = null;
        private LineBreaker mLineBreaker = null;
        private int mReflowTimeQuant = 300;
        private float mLineSpacingMultiplier = 1f;
        private int mLineSpacingAdd = 0;
        private int mEmptyLineHeightLimit = 0;
        private int mEmptyLinesThreshold = 3;
        private int mNewLineLeftMargin = 0;
        private int mNewLineTopMargin = 0;
        private Rect mTextPaddings = new Rect(5,5,5,5);
        /* non-serializable */
        private boolean mInvalidateMeasurement = false;
        private boolean mInvalidateLines = false;
        private boolean mInvalidate = false;
        private OptionsChangeListener mListener;

        public Options() {

        }

        public Options(Options source) {
            copy(source);
        }

        public void copy(Options source) {
            mFilterEmptyLines = source.mFilterEmptyLines;
            mJustification = source.mJustification;
            mDefaultDirection = source.mDefaultDirection;
            mDrawablePaddings = source.mDrawablePaddings;
            mImagePlacementHandler = source.mImagePlacementHandler;
            mLineBreaker = source.mLineBreaker;
            mReflowTimeQuant = source.mReflowTimeQuant;
            mLineSpacingMultiplier = source.mLineSpacingMultiplier;
            mLineSpacingAdd = source.mLineSpacingAdd;
            mEmptyLineHeightLimit = source.mEmptyLineHeightLimit;
            mEmptyLinesThreshold = source.mEmptyLinesThreshold;
            mListener = source.mListener;
            mNewLineLeftMargin = source.mNewLineLeftMargin;
            mNewLineTopMargin = source.mNewLineTopMargin;
            if (mListener!=null)
                mListener.invalidateMeasurement();
        }

        public void set(Bundle in) {
            mFilterEmptyLines = in.getBoolean(FILTER_EMPTY_LINES_ENABLED,true);
            mJustification = in.getBoolean(JUSTIFICATION_ENABLED,true);
            mDefaultDirection = in.getInt(DEFAULT_DIRECTION,0);
            rectFromBundle(DRAWABLE_PADDINGS,in,mDrawablePaddings);
            mReflowTimeQuant = in.getInt(REFLOW_TIME_QUANT_MS,300);
            mLineSpacingMultiplier = in.getFloat(LINESPACING_MULTIPLIER,1f);
            mLineSpacingAdd = in.getInt(LINESPACING_ADD);
            mEmptyLineHeightLimit = in.getInt(EMPTY_LINES_HEIGHT,0);
            mEmptyLinesThreshold = in.getInt(EMPTY_LINES_TRESHOLD,3);
            mNewLineLeftMargin = in.getInt(PARAGRAPH_MARGIN_LEFT);
            mNewLineTopMargin = in.getInt(PARAGRAPH_MARGIN_TOP);
            rectFromBundle(TEXT_PADDINGS,in,mTextPaddings);
        }

        public Bundle getState() {
            Bundle state = new Bundle();
            state.putBoolean(FILTER_EMPTY_LINES_ENABLED,mFilterEmptyLines);
            state.putBoolean(JUSTIFICATION_ENABLED,mJustification);
            state.putInt(DEFAULT_DIRECTION,mDefaultDirection);
            rectToBundle(DRAWABLE_PADDINGS,mDrawablePaddings,state);
            state.putInt(REFLOW_TIME_QUANT_MS,mReflowTimeQuant);
            state.putFloat(LINESPACING_MULTIPLIER,mLineSpacingMultiplier);
            state.putInt(LINESPACING_ADD,mLineSpacingAdd);
            state.putInt(EMPTY_LINES_HEIGHT,mEmptyLineHeightLimit);
            state.putInt(EMPTY_LINES_TRESHOLD,mEmptyLinesThreshold);
            state.putInt(PARAGRAPH_MARGIN_LEFT,mNewLineLeftMargin);
            state.putInt(PARAGRAPH_MARGIN_TOP,mNewLineTopMargin);
            rectToBundle(TEXT_PADDINGS,mTextPaddings,state);
            return state;
        }

        public void setChangeListener(OptionsChangeListener listener) {
            mListener = listener;
        }

        private void _il() { mInvalidateLines = true; }
        private void _im() { mInvalidateMeasurement = true; }
        private void _ii() { mInvalidate = true; }
        private void _clearInvalidation() {
            mInvalidate = false;
            mInvalidateLines = false;
            mInvalidateMeasurement = false;
        }
        public Options setFilterEmptyLines(boolean filter) {
            if (mFilterEmptyLines!=filter) _il();
            mFilterEmptyLines = filter;
            return this;
        }

        public boolean isFilterEmptyLines() {
            return mFilterEmptyLines;
        }

        public Options enableJustification(boolean justification) {
            if (mJustification!=justification) _ii();
            mJustification = justification;
            return this;
        }

        public Options setDefaultDirection(int direction) {
            if (mDefaultDirection!=direction) _im();
            mDefaultDirection = direction;
            return this;
        }

        public int getDefaultDirection() {
            return mDefaultDirection;
        }

        public Options setDrawablePaddings(int left, int top, int right, int bottom) {
            if (mDrawablePaddings.left!=left||mDrawablePaddings.top!=top||mDrawablePaddings.right!=right||mDrawablePaddings.bottom!=bottom) _im();
            mDrawablePaddings.set(left,top,right,bottom);
            return this;
        }

        public void getDrawablePaddings(Rect rect) {
            rect.set(mDrawablePaddings);
        }

        public Options setImagePlacementHandler(ImagePlacementHandler handler) {
            if (isSameClass(mImagePlacementHandler,handler)) {
                return this;
            } else {
                mImagePlacementHandler = handler;
                _im();
                return this;
            }
        }

        public ImagePlacementHandler getImagePlacementHandler() {
            return mImagePlacementHandler;
        }

        public Options setLineBreaker(LineBreaker lineBreaker) {
            if (isSameClass(mLineBreaker,lineBreaker)) {
                return this;
            }
            _im();
            mLineBreaker = lineBreaker;
            return this;
        }

        public LineBreaker getLineBreaker() {
            return mLineBreaker;
        }

        public Options setLineSpacingMultiplier(float mult) {
            if (mLineSpacingMultiplier!=mult) _il();
            mLineSpacingMultiplier = mult;
            return this;
        }

        public float getLineSpacingMultiplier() {
            return mLineSpacingMultiplier;
        }

        public Options setLineSpacingAdd(int add) {
            if (mLineSpacingAdd!=add) _il();
            mLineSpacingAdd = add;
            return this;
        }

        public int getLineSpacingAdd() {
            return mLineSpacingAdd;
        }

        public Options setReflowQuantize(int milliseconds) {
            mReflowTimeQuant = milliseconds;
            return this;
        }

        public int getReflowQuantize() {
            return mReflowTimeQuant;
        }

        public boolean isJustification() {
            return mJustification;
        }

        public int getEmptyLineHeightLimit() {
            return mEmptyLineHeightLimit;
        }

        public int getEmptyLinesThreshold() {
            return mEmptyLinesThreshold;
        }

        public Options setNewLineLeftMargin(int margin) {
            if (mNewLineLeftMargin!=margin) _im();
            mNewLineLeftMargin = margin;
            return this;
        }

        public int getNewLineLeftMargin() { return mNewLineLeftMargin; }

        public Options setNewLineTopMargin(int margin) {
            if (mNewLineTopMargin!=margin) _im();
            mNewLineTopMargin = margin;
            return this;
        }

        public int getNewLineTopMargin() { return mNewLineTopMargin; }

        public void apply() {
            if (mListener != null) {
                if (mInvalidateMeasurement) {
                    _clearInvalidation();
                    mListener.invalidateMeasurement();
                } else if (mInvalidateLines) {
                    _clearInvalidation();
                    mListener.invalidateLines();
                } else if (mInvalidate) {
                    _clearInvalidation();
                    mListener.invalidate();
                }
            }
        }

        private boolean isSameClass(Object a, Object b) {
            if (a!=null) {
                if (b!=null) {
                    if (a.getClass().getName().equals(b.getClass().getName())) {
                        return true;
                    }
                } else {
                    return false;
                }
            } else if (b==null) {
                return true;
            }
            return false;
        }

        public void setTextPaddings(int left, int top, int right, int bottom) {
            if (diff(mTextPaddings,new Rect(left,top,right,bottom))) _im();
            mTextPaddings.set(left,top,right,bottom);
        }

        public Rect getTextPaddings() { return mTextPaddings; }

        public Options setTextSize(float size) {
            if (mListener!=null) {
                if (mListener.getTextPaint().getTextSize()==size) return this;
                _im();
                mListener.getTextPaint().setTextSize(size);
            }
            return this;
        }

        /* utils */
        private boolean diff(Rect a, Rect b) {
            return (a.left==b.left && a.top==b.top && a.right==b.right && a.bottom==b.bottom);
        }

        private void rectToBundle(String name, Rect r, Bundle out) {
            out.putIntArray(name,new int[]{r.left, r.top, r.right, r.bottom});
        }

        private void rectFromBundle(String name, Bundle in, Rect out) {
            int[] r = in.getIntArray(name);
            out.set(r[0],r[1],r[2],r[3]);
        }

    }



    void setLoadingState(boolean loading);

    void contentReady(String uuid, CharSequence content, Options options);
}
