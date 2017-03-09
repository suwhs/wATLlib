package su.whs.watl.layout;

import android.text.Layout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

import su.whs.watl.text.ContentView;


/**
 * Created by igor n. boulliev on 15.01.17.
 */

public class TextLinesBuilder {
    private TextLinesBuilderCallbacks mCallbacks;
    private float mCollectedWidth = 0f;
    private State mReflowState;
    private List<State> mStatesStack = new ArrayList<State>();
    private List<Break> mBreaks = new ArrayList<Break>();
    private Line mCurrentLine;

    public TextLinesBuilder(TextLinesBuilderCallbacks callbacks, ContentView.Options options) {
        mCallbacks = callbacks;
    }

    public void reset() {
        mCollectedWidth = 0f;
        mBreaks = new ArrayList<Break>();
    }

    /**
     * push 'span' to line
     * if lastBreak == null then lastBreak = (span,0)
     * if span.height > state.height:
     *    state marked as 'invalidates line height'
     *    // case: second char of span exceed line width limit, but linebreaker reports line ends on lastBreak
     *    // - we does not change current line height, just reports onLineFinished(), clear states stack
     *    // and starts new line with span height
     * **/

    public void add(char[] text, Span span, Break lastBreak, TextPaint paint) {
        if (span.mMeasure == null)
            span.measure(text, paint);
        if (mReflowState!=null) {
            mStatesStack.add(0, mReflowState);
        }
        mReflowState = new State(span,mCallbacks.getAvailableWidth());
        if (lastBreak!=null) {
            mReflowState.beginsFrom(lastBreak.mStart+lastBreak.mLength);
        }
        if (span.mDirection == Layout.DIR_RIGHT_TO_LEFT) {
            // addRTL(text,span,lastBreak,paint);
            return;
        }
        /**
         * classify characters
         *
         *
         * character classes:
         *      letter
         *      digit
         *      dynamic drawable (next:drawable)
         *      whitespace
         *      punctuation
         *      character are carrier return (paragraph's end)
         * character groups:
         *      printable
         *      drawable
         *      whitespace
         *
         * character group transitions
         *      printable->drawable
         *      printable->whitespace
         *      printable->printable
         *
         *      drawable->printable
         *      drawable->drawable
         *      drawable->whitespace
         *
         *      whitespace->printable
         *      whitespace->drawable
         *      whitespace->whitespace
         */

        float leftWidth = mCallbacks.getAvailableWidth() - mCollectedWidth;
        loop:
        for(int i=span.mStart; i<span.mEnd; i++) {
            float width = span.mMeasure.mWidths[i - span.mStart];

            if (leftWidth-width<0) {
                if (mCallbacks.isWhitespacesCompressEnabled()) {
                    // try to compress

                } else {

                }

            }
            restart:
            while(i<span.mEnd) {
                if (text[i] < 21) {
                    if (text[i]=='\n') {
                        if (!mCallbacks.onLineReady(mCurrentLine)) {
                            finishLine();
                            finishLayout();
                            return;
                        }
                        mStatesStack.clear();
                        i++;
                        continue loop;
                    } else {
                        // non-printable character
                        i++;
                    }
                } else if (text[i] == 65532) {
                    // inline placement only here, span must contains placement width, and correct height
                    addInlineImage(span);
                    i++;
                    continue loop;
                } else if ((mReflowState.leftWidth - width) > 0) {
                    mReflowState.leftWidth -= width;
                    i++;
                    continue loop;
                } else {
                    // force break (lb)
                    i++;
                    continue restart;
                }
            }
        }
        //
    }

    private void addRTL(char[] text, Span span, Break lastBreak, TextPaint paint) {

    }

    private void addInlineImage(Span span) {

    }

    public void finish() {

    }

    private static boolean isAllowCarrierReturn(char ch) {
        return true;
    }

    private static boolean isAllowHanging(char ch) {
        return false;
    }

    private void finishLine() {

    }

    private void finishLayout() {

    }
}
