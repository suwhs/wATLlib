package su.whs.watl.text;

import android.util.Log;

/**
 * Created by igor n. boulliev on 13.03.15.
 */


class ReflowState {
    private static final String TAG = "ReflowState";
    // public LineSpanBreak afterBreak;
    LineSpan span;
    LineSpanBreak lastBreak = null;
    LineSpanBreak prevBreak = null;
    LineSpan startSpan = null;
    int gravity = 0;
    float lineWidth = 0f;
    int shiftY;
    int lastWhitespace = 0;
    float processedWidth = 0f;
    float breakWidth = 0f;
    int character = 0;
    int whitespaces = 0;
    int height;
    float leading;
    int descent;
    LineSpanBreak carrierReturnBreak = null;
    LineSpan carrierReturnSpan = null;
    boolean skipWhitespaces = false;


    public ReflowState(LineSpan span, float lineWidth) {
        this.span = span;
        this.gravity = span.gravity;
        this.lineWidth = lineWidth;
        this.character = span.start;
    }

    public ReflowState(ReflowState state) {
        this.span = state.span;
        if (state.span!=null && state.span.breakFirst!=null) {
            this.lastBreak = state.span.breakFirst;
        }
        this.gravity = state.gravity;
        this.lineWidth = state.lineWidth;
        this.shiftY = state.shiftY;
        this.lastWhitespace = state.lastWhitespace;
        this.breakWidth = state.breakWidth;
        this.character = state.character;
        this.height = state.height;
        this.leading = state.leading;
        this.descent = state.descent;
        this.skipWhitespaces = state.skipWhitespaces;
    }

    public ReflowState(ReflowState state, LineSpan span) {
        this(state);
        this.span = span;
        this.gravity = span.gravity;
        this.character = state.character;
        this.lastWhitespace = state.lastWhitespace;
        this.carrierReturnBreak = state.carrierReturnBreak;
        this.carrierReturnSpan = state.carrierReturnSpan;
        this.whitespaces = state.whitespaces;
        this.startSpan = state.startSpan;
        this.breakWidth = 0f;
    }

    public void carrierReturn(TextLayout.TextLine ld) {
        LineSpanBreak lineBreak = span.newBreak();
        lineBreak.position = this.character - 1;
        ld.end = this.character;
        ld.gravity = this.gravity;
        lineBreak.carrierReturn = true;
        lineBreak.width = breakWidth;
        processedWidth += breakWidth;
        prevBreak = lastBreak;
        if (span.breakFirst == null) {
            span.breakFirst = lineBreak;
        } else {
            if (lastBreak==null) {
                LineSpanBreak lsb = span.breakFirst;
                while(lsb.next!=null) lsb = lsb.next;
                lastBreak = lsb;
            }
            lastBreak.next = lineBreak;
        }

        lastBreak = lineBreak;
        carrierReturnBreak = lastBreak;
        carrierReturnSpan = span;
        this.lastWhitespace = this.character;
        lineWidth = span.margin;
        breakWidth = 0f;

        height = 0; // span.height;
        leading = 0f; // span.leading;
        descent = 0; // span.descent;

        whitespaces = 0;
        character++;
    }

    public void breakLine(boolean whitespace, TextLayout.TextLine ld) {
        /* break line at state.character (state.character are last character on line) */
        float incrementWidth = span.widths[character - span.start];

        if (character == lastWhitespace /* lastBreak != null && lastBreak.position == character */) {
            lastBreak.carrierReturn = true;
            if (!lastBreak.strong)
                ld.whitespaces--;
            lastBreak.strong = true;
            carrierReturnBreak = lastBreak;
            carrierReturnSpan = span;
        } else {
            LineSpanBreak lineBreak = span.newBreak();
            lineBreak.position = this.character;
            lineBreak.carrierReturn = true;
            lineBreak.width = breakWidth + (!whitespace ? incrementWidth : 0f);
            processedWidth += lineBreak.width;
            prevBreak = lastBreak;
            if (span.breakFirst == null) {
                span.breakFirst = lineBreak;
            } else {
                lastBreak.next = lineBreak;
            }
            if (whitespace) {
                ld.whitespaces--;
            }
            lastBreak = lineBreak;
            carrierReturnBreak = lastBreak;
            carrierReturnSpan = span;
        }

        if (carrierReturnBreak.position <= carrierReturnSpan.start) {
            Log.w(TAG, "carrier return position <= span.start");
        }

        ld.width = lineWidth + (!whitespace ? incrementWidth : 0f);

        lineWidth = 0f;
        breakWidth = 0f;
        height = 0; // span.height;
        leading = 0f; // span.leading;
        descent = 0; // span.descent;
        whitespaces = 0;
        this.lastWhitespace = this.character;
    }

    public ReflowState nonLetterBreak(boolean strong) {
        float currentCharacterWidth = span.widths[this.character - span.start];
        increaseBreak(currentCharacterWidth);
        LineSpanBreak lineBreak = span.newBreak();

        this.lastWhitespace = this.character;
        if (character < span.end)
            this.processedWidth += this.breakWidth;
        lineBreak.width = breakWidth;
        lineBreak.position = character;
        lineBreak.strong = strong;
        prevBreak = this.lastBreak;
        if (span.breakFirst == null) {
            span.breakFirst = lineBreak;
            lastBreak = lineBreak;
        } else {
            if (lastBreak == null) {
                Log.e(TAG, "breakFirst!=null, but lastBreak==null!");
                LineSpanBreak lsb = span.breakFirst;
                while(lsb.next!=null) lsb = lsb.next;
                lastBreak = lsb;
            }
            lastBreak.next = lineBreak;
            lastBreak = lineBreak;
        }

        this.breakWidth = 0f;
        if (!strong)
            this.whitespaces++;
        character++;
        return this;
    }

    public void directionSwitchBreak() {

    }

    public ReflowState finish() {
        if (lastBreak != null) {
            lastBreak.tail = span.width - this.processedWidth;
        }
        this.breakWidth = 0f;
        return this;
    }

    public void increaseBreak(float width) {
        this.breakWidth += width;
        this.lineWidth += width;
    }

    public void increaseBreak(float width, float realWidth) {
        this.breakWidth += realWidth;
        this.lineWidth += realWidth;
    }

    public void rollback(int breakPosition) {
        while (this.character > breakPosition) {
            float currentCharacterWidth = span.widths[this.character - span.start - 1];
            this.breakWidth -= currentCharacterWidth;
            this.lineWidth -= currentCharacterWidth;
            this.character--;
        }
    }


    public void doSkipWhitespaces(char[] text) {
        while (character < span.end && (text[character] == ' ')) {
            carrierReturnSpan.skip++;
            processedWidth += span.widths[character - span.start];
            character++;
        }
        if (character < span.end) {
            skipWhitespaces = false;
        }
    }


}
