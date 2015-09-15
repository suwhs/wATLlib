package su.whs.watl.text;

import android.util.Log;

/**
 * Created by igor n. boulliev on 13.03.15.
 */

/* @hide */
class ReflowState {
    private static final String TAG = "ReflowState";
    private boolean debug = false;
    // public LineSpanBreak afterBreak;
    LineSpan span;
    protected LineSpanBreak lastBreak = null;
    LineSpanBreak prevBreak = null;
    LineSpan startSpan = null; // ??? WHY // FIXME: remove
    int gravity = 0; // active gravity
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
        if (debug)
            Log.d(TAG,"[0] state from span:\n" + span +"\n"+this.toString());
    }

    public ReflowState(ReflowState state, boolean debug) {
        this.span = state.span;
        copy(state);
        if (debug)
            Log.d(TAG,"[1] copy state:\n"+this.toString());
    }

    public ReflowState(ReflowState state) {
        this(state,true);
    }

    public ReflowState(ReflowState state, LineSpan span) {
        this.span = span;
        copy(state);
        this.gravity = span.gravity;
        this.character = state.character;
        this.lastWhitespace = state.lastWhitespace;
        this.carrierReturnBreak = state.carrierReturnBreak;
        this.carrierReturnSpan = state.carrierReturnSpan;
        this.whitespaces = state.whitespaces;
        this.startSpan = state.startSpan;
        this.breakWidth = 0f;
        this.skipWhitespaces = state.skipWhitespaces;
        if (debug)
            Log.d(TAG,"[2] next state with span:\n" + span +"\n"+this.toString());
    }

    private void copy(ReflowState state) {
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
        if (debug)
            Log.d(TAG, "[3] carrierReturn on TextLine:>>>\n" + ld + "\n<<<\n" + this.toString());
    }

    public void breakLine(boolean whitespace, TextLayout.TextLine ld) {
        /* break line at state.character (state.character are last character on line) */
        float incrementWidth = span.widths[character - span.start];

        if (character == lastWhitespace /* lastBreak != null && lastBreak.position == character */) {
            if (lastBreak==null) {
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
            } else {
                lastBreak.carrierReturn = true;
                if (!lastBreak.strong)
                    ld.whitespaces--;
                lastBreak.strong = true;
            }
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

        if (debug && carrierReturnBreak.position < carrierReturnSpan.start) {
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
        if (debug)
            Log.d(TAG,"[4] breakLine(whitespace="+whitespace+") on TextLine:>>>\n"+ld+"\n<<<\n"+this.toString());
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
            lastBreak.next = lineBreak;
            lastBreak = lineBreak;
        }
        this.breakWidth = 0f;
        if (!strong)
            this.whitespaces++;
        character++;
        if (debug)
            Log.d(TAG,"[5] nonLetterBreak (strong="+strong+")" );
        return this;
         // on TextLine:>>>\n"+ld+"\n<<<\n"+this.toString());
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
        if (debug)
            Log.d(TAG,"[6] rollbak before:>>>\n"+toString()+"\n<<<");

        while (this.character > breakPosition) {
            float currentCharacterWidth = span.widths[this.character - span.start - 1];
            this.breakWidth -= currentCharacterWidth;
            this.lineWidth -= currentCharacterWidth;
            this.character--;
        }

        if (debug && this.lastBreak!=null && !lastBreak.strong) { // if we fail to whiespace (non-drawing break) - we must correct substraccted breakwidth
            if (this.prevBreak!=null && this.prevBreak.next == lastBreak) {
                Log.d(TAG,"should we revert lastBreak?");
            } else if (this.prevBreak!=null) {
                Log.d(TAG,"inconsistent prevBreak.next!=lastBreak");
            }
            Log.d(TAG,"lastbreak width="+lastBreak.width);
            Log.d(TAG,"lastbreak tail="+lastBreak.tail);
            // this.breakWidth += this.lastBreak.width;
        }
        if (debug)
        Log.d(TAG,"[6] rollback after:>>>\n"+toString()+"\n<<<");

    }


    public void doSkipWhitespaces(char[] text) {
        if (debug)
            Log.d(TAG,"[7] skipWhitespaces before:>>>\n"+toString()+"\n<<<");
        while (character < span.end && (text[character] == ' ')) {
            carrierReturnSpan.skip++;
            processedWidth += span.widths[character - span.start];
            character++;
        }
        if (character < span.end) {
            skipWhitespaces = false;
        }
        if (debug)
            Log.d(TAG,"[7] skipWhitespaces after:>>>\n"+toString()+"\n<<<");
    }


    public String toString() {
        return String.format("ReflowState [character=%d,height=%d,lastWhitespace=%d,lineWidth=%.2f,processedWidth=%.2f,breakWidth=%.2f"
                + "\nspan=%h\nstartSpan=%h\ncarrierReturnSpan=%h\ncarrierReturnBreak=%h\nprevBreak=%h\n",
                this.character,
                this.height,
                this.lastWhitespace,
                this.lineWidth,
                this.processedWidth,
                this.breakWidth,
                this.span,
                this.startSpan,
                this.carrierReturnSpan,
                this.carrierReturnBreak,
                this.prevBreak);
    }


    public void breakLineAfterImage() {
        float incrementWidth = span.widths[character - span.start];

        if (lastBreak==null) {
            LineSpanBreak lineBreak = span.newBreak();
            lineBreak.position = this.character;
            lineBreak.carrierReturn = true;
            lineBreak.width = breakWidth + incrementWidth;
            processedWidth += lineBreak.width;
            prevBreak = lastBreak;
            if (span.breakFirst == null) {
                span.breakFirst = lineBreak;
            } else {
                lastBreak.next = lineBreak;
            }
            lastBreak = lineBreak;
        } else {
            Log.e(TAG,"there must no break on image span");
        }

        lineWidth = 0f;

        breakWidth = 0f;
        height = 0; // span.height;
        leading = 0f; // span.leading;
        descent = 0; // span.descent;
        whitespaces = 0;
        carrierReturnBreak = lastBreak;
        carrierReturnSpan = this.span;
        this.lastWhitespace = this.character;
    }
}
