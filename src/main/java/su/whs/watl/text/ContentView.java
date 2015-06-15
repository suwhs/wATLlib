package su.whs.watl.text;

import android.graphics.Rect;

/**
 * Created by igor n. boulliev on 10.02.15.
 */
public interface ContentView {

    interface OptionsChangeListener {
        void invalidateMeasurement(); // full reflow required
        void invalidateLines(); // lines distribution changed
        void invalidate(); // simple repaint
    }

    class Options {
        private boolean mFilterEmptyLines = true;
        private boolean mJustification = true;
        private int mDefaultDirection = 0;
        private int[] mDrawablePaddings = new int[]{0, 0, 0, 0};
        private ImagePlacementHandler mImagePlacementHandler = null;
        private LineBreaker mLineBreaker = null;
        private int mReflowTimeQuant = 150;
        private float mLineSpacingMultiplier = 1f;
        private int mLineSpacingAdd = 0;
        private int mEmptyLineHeightLimit;
        private int mEmptyLinesThreshold;
        private OptionsChangeListener mListener;
        private int mNewLineLeftMargin = 0;
        private int mNewLineTopMargin = 0;
        private boolean mInvalidateMeasurement = false;
        private boolean mInvalidateLines = false;
        private boolean mInvalidate = false;
        private Rect mTextPaddings = new Rect(10,10,10,10);

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
        public Options filterEmptyLines(boolean filter) {
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
            if (mDrawablePaddings[0]!=left||mDrawablePaddings[1]!=top||mDrawablePaddings[2]!=right||mDrawablePaddings[3]!=bottom) _im();
            mDrawablePaddings[0] = left;
            mDrawablePaddings[1] = top;
            mDrawablePaddings[2] = right;
            mDrawablePaddings[3] = bottom;
            return this;
        }

        public void getDrawablePaddings(Rect rect) {
            rect.set(mDrawablePaddings[0], mDrawablePaddings[1], mDrawablePaddings[2], mDrawablePaddings[3]);
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

        private boolean diff(Rect a, Rect b) {
            return (a.left==b.left && a.top==b.top && a.right==b.right && a.bottom==b.bottom);
        }
    }



    void setLoadingState(boolean loading);

    void contentReady(String uuid, CharSequence content, Options options);
}
