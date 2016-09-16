package su.whs.watl.text;

import android.text.Layout;
import android.text.style.LeadingMarginSpan;
import android.view.Gravity;

import java.lang.ref.WeakReference;

/**
 * Created by igor n. boulliev on 11.01.16.
 */
public class TextLine {
    int whitespaces = 0;
    int descent;
    float width; // real line width (with margin, but without justification)
    float justifyArgument = 0f;
    LeadingMarginSpan leadingMargin;
    boolean hyphen = false;
    WeakReference<LineSpan> span;
    int start;
    int end;
    int height;
    WeakReference<LineSpanBreak> afterBreak = null;
    int margin = 0;
    int direction = Layout.DIR_LEFT_TO_RIGHT;
    int gravity = Gravity.NO_GRAVITY;
    int wrapHeight = 0;
    int wrapWidth = 0;
    int wrapMargin = 0;
    boolean highlighted = false;
    boolean selected = false;

    public TextLine(ReflowState state, int lineStartAt, LeadingMarginSpan leadingMarginSpan) {
        this.whitespaces = state.whitespaces;
        this.width = state.lineWidth;
        LineSpan span = state.carrierReturnSpan; // FIXME: after carrier return on span.ends state must eliminate carrierReturnSpan ?
        this.span = new WeakReference<LineSpan>(span);
        this.afterBreak = state.carrierReturnBreak == null ? null : new WeakReference<LineSpanBreak>(state.carrierReturnBreak);
        this.start = lineStartAt;
        this.height = state.height; // (int) (state.height + state.descent + state.leading * lineSpacingMultiplier + lineSpacingAdd);
        this.descent = state.descent;
        this.end = state.character + 1;
        if (state.character > lineStartAt)
            this.leadingMargin = leadingMarginSpan;

    }

    public TextLine(LineSpan span, int wrapHeight, int wrapWidth, int gravity) {
        assert (!span.isDrawable);
        this.wrapWidth = wrapWidth;
        this.wrapHeight = wrapHeight;
        this.span = new WeakReference<LineSpan>(span);
        this.width = 0;
        this.height = 0;

        if (span != null) {
            this.start = span.start;
            this.end = span.end;
            this.span.get().gravity = gravity;
        }
    }

    public TextLine(LineSpan span, int width, int height) {
        if (span != null)
            this.span = new WeakReference<LineSpan>(span);
        this.width = width;
        this.height = height;
        if (span != null) {
            this.start = span.start;
            this.end = span.end;
        }
    }

//    /**
//     * for debug - dumps span info
//     *
//     * @param str - reference to text
//     * @return
//     */
//    public String dump(CharSequence str, boolean dumpBreaks) {
//        // String str = new String(chars);
//        if (span.get() == null) return "NULL";
//        return "ss=" + span.get().start + ",se=" + span.get().end + " [w/h=" + width + "/" + height + ",w=" + whitespaces + ",(s=" + start + ",e=" + end + "j=" + justifyArgument + ")]: '" + str.subSequence(start, end) + "'\n" + dumpSpans(str, dumpBreaks);
//    }
//
//    public String toString() {
//        return this.toString(false);
//    }
//
//    public String toString(CharSequence chars, boolean dumpBreaks) {
//        return dump(chars, dumpBreaks);
//    }

//    /**
//     * @param chars
//     * @return
//     */
//
//    private String dumpSpans(char[] chars, boolean dumpBreaks) {
//        SpannableStringBuilder ssb = new SpannableStringBuilder();
//        LineSpan current = this.span.get();
//        while (current != null && current.start < end) {
//            ssb.append(current.toString(chars, dumpBreaks) + "\n");
//            current = current.next;
//        }
//        return ssb.toString();
//    }

    /**
     * @param width
     */

    public void justify(int width) {
        if (whitespaces > 0) {
            justifyArgument = (width - this.width - 0.5f) / whitespaces;
        }
    }

    void setDirection(int direction) {
        this.direction = direction;
    }
}
