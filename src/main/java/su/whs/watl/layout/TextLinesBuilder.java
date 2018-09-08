package su.whs.watl.layout;

import android.text.Layout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

import su.whs.syllabification.parent.LineBreaker;
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
    private ContentView.Options mOptions;

    public TextLinesBuilder(TextLinesBuilderCallbacks callbacks, ContentView.Options options) {
        mCallbacks = callbacks;
        mOptions = options;
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
        add(text,span,lastBreak,span.mEnd,paint);
    }
    public void add(char[] text, Span span, Break lastBreak, int limit, TextPaint paint) {
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
        for(int i=span.mStart; i<span.mEnd && i<limit; i++) {
            float width = span.mMeasure.mWidths[i - span.mStart];

            if (leftWidth-width<0) {
                if (mCallbacks.isWhitespacesCompressEnabled()) {
                    // try to compress

                } else {

                }

            }
            restart:
            while(i<span.mEnd && i<limit) {
                if (text[i] < 21) {  // special character
                    if (text[i] == '\n') {
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
                    i = handleForceBreak(text,span,lastBreak,i,width,paint);
                    continue restart;
                }
            }
        }
        if (limit < span.mEnd) {
            // force break after rollback
            // do nothing, called from handleForceBreak
        } else if (limit > span.mEnd) {
            // in rebuild line process after force break

        } else {
            // normal finish of span

        }
        //
    }

    private int handleForceBreak(char[] text, Span span, Break lastBreak, int at, float width, TextPaint paint) {
        float unsufficient = -(mReflowState.leftWidth - width);
        if (mOptions.isCompressionEnabled()) {
                        /*
                        * i.e. 5 whitespaces on line with 8 width for each - 40 px of line eaten by whitespace
                        * check, if unsufficient < 40 * .8
                        * */
            if ( // TODO: implement ContentView.Options.isCompressionEnabled() before testing
                    mReflowState.whitespacesCount>0
                            && ((mReflowState.whitespacesCount*mReflowState.currentWhitespaceWidth)*.8f/mReflowState.whitespacesCount) > unsufficient) {
                // increment i and continue
                at++;
                return at;
            }
        }
        LineBreaker lb = mOptions.getLineBreaker();
        int breakVal = lb.nearestLineBreak(text,mReflowState.lastWhitespace,text.length,text.length);
        int breakPosition = LineBreaker.getPosition(breakVal);
        boolean hyphen = LineBreaker.isHyphen(breakVal);
        if (breakPosition<span.mStart) {
            List<Span> rolledOut = rollback(breakPosition);
            for(Span again : rolledOut)
                add(text,again,lastBreak,breakPosition,paint); // add rolled back spans
            return mReflowState.position;
        }
        if (hyphen) {

        }
        return mReflowState.position;
    }

    private List<Span> rollback(int toPosition) {
        List<Span> results = new ArrayList<Span>();
        Span prev = mReflowState.span;
        while (toPosition<prev.mStart) {
            mReflowState = mStatesStack.get(0);
            results.add(0,prev);
            prev = mReflowState.span;
        }
        return results;
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
