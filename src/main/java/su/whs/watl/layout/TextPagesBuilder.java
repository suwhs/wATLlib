package su.whs.watl.layout;

import android.text.TextPaint;

import java.util.List;

import su.whs.watl.text.TextLine;

/**
 * Created by igor n. boulliev on 15.01.17.
 */

public class TextPagesBuilder implements TextLinesBuilderCallbacks {
    private TextPagesBuilderCallbacks mCallbacks;
    private TextLinesBuilder mTextLinesBuilder;
    private int mCollectedHeight = 0;
    private int mCurrentLineHeight = 0;
    private List<TextLine> mLines;
    private int mFirstPageLine = 0;
    private Break mLastBreak;
    private TextPaint mMeasurePaint;

    public TextPagesBuilder(TextPagesBuilderCallbacks callbacks, TextPaint measurePaint) {
        mCallbacks = callbacks;
        mMeasurePaint = measurePaint;
    }

    void add(char[] text, Span lineSpan) {
        mTextLinesBuilder.add(text, lineSpan, mLastBreak, mMeasurePaint);
    }

    void add(Line textLine, Break lastBreak) {
        if (textLine.height + mCollectedHeight > mCallbacks.getAvailableHeight()) {
            if (mCallbacks.getAvailableHeight()>0) {
                finishPage();
                add(textLine, lastBreak);
            } else {
                mCollectedHeight += textLine.height;
                mCallbacks.onHeightChanged(mCollectedHeight);
            }
        }
        mLastBreak = lastBreak;
    }

    @Override
    public int getAvailableWidth() {
        return mCallbacks.getAvailableWidth();
    }

    @Override
    public int getAvailableHeight() {
        return Math.max(mCallbacks.getAvailableHeight()-mCollectedHeight,-1);
    }

    @Override
    public void onLineHeightChanged(int height) {
        if (mCollectedHeight+height>getAvailableHeight()) {
            finishPage();
        }
        mCurrentLineHeight = height;
    }

    @Override
    public boolean onLineReady(Line textLine) {
        return false;
    }

    @Override
    public boolean isWhitespacesCompressEnabled() {
        return false;
    }

//    @Override
//    public void onSpanProcessingFinished(Span span) {
//
//    }


    private void finishPage() {
        if (!mCallbacks.onPageReady(mLines,mFirstPageLine,mLines.size())) {
            mCallbacks.onLayoutFinished();
        }
        mFirstPageLine = mLines.size();
    }
}
