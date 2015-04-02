package su.whs.watl.text;

import android.graphics.Rect;

/**
 * Created by igor n. boulliev on 10.02.15.
 */
public interface ContentView {

    interface OptionsChangeListener {
        void invalidateMeasurement();
        void invalidateLines();
        void invalidate();
    }

    class Options {
        boolean mFilterEmptyLines = true;
        boolean mJustification = true;
        int mDefaultDirection = 0;
        int[] mDrawablePaddings = new int[]{0, 0, 0, 0};
        ImagePlacementHandler mImagePlacementHandler = null;
        LineBreaker mLineBreaker = null;
        int mReflowTimeQuant = 150;
        float mLineSpacingMultiplier = 1f;
        int mLineSpacingAdd = 0;
        private int mEmptyLineHeightLimit;
        private int mEmptyLinesThreshold;
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
        }

        public int invalidateLevel(Options source) {
            int result = 0;
            if (mFilterEmptyLines != source.mFilterEmptyLines || mJustification != source.mJustification || mLineSpacingMultiplier != source.mLineSpacingMultiplier || mLineSpacingAdd != source.mLineSpacingAdd ||
                    mEmptyLineHeightLimit != source.mEmptyLineHeightLimit || mEmptyLinesThreshold != source.mEmptyLinesThreshold)
                result = 1;
            return result;
        }

        public void setChangeListener(OptionsChangeListener listener) {
            mListener = listener;
        }

        public Options filterEmptyLines(boolean filter) {
            mFilterEmptyLines = filter;
            return this;
        }

        public boolean isFilterEmptyLines() {
            return mFilterEmptyLines;
        }

        public Options enableJustification(boolean justification) {
            mJustification = justification;
            return this;
        }

        public Options setDefaultDirection(int direction) {
            mDefaultDirection = direction;
            return this;
        }

        public int getDefaultDirection() {
            return mDefaultDirection;
        }

        public Options setDrawablePaddings(int left, int top, int right, int bottom) {
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
            mImagePlacementHandler = handler;
            return this;
        }

        public ImagePlacementHandler getImagePlacementHandler() {
            return mImagePlacementHandler;
        }

        public Options setLineBreaker(LineBreaker lineBreaker) {
            mLineBreaker = lineBreaker;
            return this;
        }

        public LineBreaker getLineBreaker() {
            return mLineBreaker;
        }

        public Options setLineSpacingMultiplier(float mult) {
            mLineSpacingMultiplier = mult;
            return this;
        }

        public float getLineSpacingMultiplier() {
            return mLineSpacingMultiplier;
        }

        public Options setLineSpacingAdd(int add) {
            mLineSpacingAdd = add;
            return this;
        }

        public int getmLineSpacingAdd() {
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

        public void apply() {
            if (mListener != null) {
                mListener.invalidate();
            }
        }
    }

    void setLoadingState(boolean loading);

    void contentReady(String uuid, CharSequence content, Options options);
}
