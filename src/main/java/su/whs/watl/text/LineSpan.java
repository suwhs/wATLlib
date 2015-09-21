package su.whs.watl.text;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ParagraphStyle;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Bidi;
import java.util.List;

/**
 * Created by igor n. boulliev on 07.12.14.
 * copyright (c) 2014-2015 all rights reserved
 */

/* package */
class LineSpan {
    private static final String TAG = "LineSpan"; // at least 64 bytes per lineSpan
    private static final boolean debug = false;
    private static boolean mBidiEnabled = true;
    private static boolean mBidiDebug = true;
    public static boolean isBidiEnabled() { return mBidiEnabled; } // TODO: move to Options
    public int gravity = Gravity.LEFT;
    public int direction = Layout.DIR_LEFT_TO_RIGHT; // TODO: use Bidi on prepare() ?
    public boolean strong = false;
    public boolean paragraphStart = false; // this span are first span in paragraph
    public boolean paragraphEnd = false; // this span are last span in paragraph

    public float width = -1;
    public int height = -1;
    public int start; // first character index
    public int end; // end-1 == last character index
    // TODO: lazy widths[]
    public float[] widths; // widths of chars
    public LineSpan next = null; // next LineSpan in chain
    public CharacterStyle[] spans;
    public ParagraphStyle[] paragraphStyles;
    public CharSequence reversed = null;
    // CACHED DATA
    public LineSpanBreak breakFirst = null;
    public int baselineShift = 0;
    public int margin;
    public int marginLineCount = -1;
    public int marginEnd; // used if marginLineCount >-1
    public float hyphenWidth = 0f;
    public float leading;
    public float drawableScaledWidth = 0f;
    public float drawableScaledHeight = 0;
    public float drawableScrollX = 0f;
    public float drawableScrollY = 0f;
    public float drawableClipWidth = 0f;
    public float drawableClipHeight = 0f;
    public int skip = 0;
    /* options ? */
    public int paragraphStartMargin;
    public int paragraphTopMargin;
    boolean isDrawable = false;     // is cache ?

    public int descent;
    public boolean noDraw = false;

//    /* copy constructor */
//
//    public LineSpan(LineSpan span) {
//        this.spans = span.spans;
//        this.paragraphStyles = span.paragraphStyles;
//        this.next = span.next;
//        this.start = span.start;
//        this.end = span.end;
//        this.widths = span.widths;
//        this.height = span.height;
//        this.descent = span.descent;
//        this.leading = span.leading;
//    }

    /* */

    public LineSpan() {
    }

    private static int flagsToInt(LineSpan span) {
        return (span.strong ? 0x01 : 0) | (span.paragraphStart ? 0x02 : 0) | (span.paragraphEnd ? 0x04 : 0) | (span.isDrawable ? 0x08 : 0);
    }

    private static void flagsFromInt(LineSpan span, int flags) {
        span.strong = (flags & 0x01) == 0x01;
        span.paragraphStart = (flags & 0x02) == 0x02;
        span.paragraphEnd = (flags & 0x04) == 0x04;
        span.isDrawable = (flags & 0x08) == 0x08;
    }

    public static void Serialize(DataOutputStream osw, LineSpan span) throws IOException {
        LineSpan current = span;
        int counter = span == null ? 0 : 1;
        for (current = span; current != null; current = current.next, counter++) ;
        osw.writeInt(counter);
        for (current = span; current != null; current = current.next) {
            SerializeLineSpan(osw, current);
        }
    }

    public static void Deserialize(DataInputStream isw, LineSpan span) throws IOException {
        // it's restore all info except measurement
        // so, call 'prepare', then Deserialize(stream, preparedLineSpan)
        LineSpan current = span;
        int counter = span == null ? 0 : 1;
        for (current = span; current != null; current = current.next, counter++) ;
        int storedCounter = isw.readInt();
        if (storedCounter != counter) {
            throw new IOException("stored counter and prepared span chain length does not match");
        }
        for (current = span; current != null; current = current.next) {
            DeserializeLineSpan(isw, current);
        }
    }

    public static void SerializeLineSpan(DataOutputStream osw, LineSpan span) {

    }

    public static void DeserializeLineSpan(DataInputStream isw, LineSpan span) {

    }


    /**
     * uses for transform given spanned text into LineSpan
     *
     * @param text  - text
     * @param start - start character
     * @param end   - last character + 1
     * @param dynamicDrawableSpanSparseArray
     * @return LineSpan linked list
     */

    /* geometry-independed calculations */
    public static LineSpan prepare(Spanned text, int start, int end, int paragraphStartMargin, int paragraphTopMargin, SparseArray<DynamicDrawableSpan> dynamicDrawableSpanSparseArray) {
        return prepare(text, start, end, paragraphStartMargin, paragraphTopMargin, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,dynamicDrawableSpanSparseArray);
    }

    public static LineSpan prepare(Spanned text, int start, int end, int paragraphStartMargin, int paragraphTopMargin, int defaultDirection, SparseArray<DynamicDrawableSpan> forwardsDrawableArray) {
        // TODO: optimize or make LAZY calculating (too long time for complex html)
        long timeStart = SystemClock.uptimeMillis();
        long bidiSpent = 0;
        LineSpan result = new LineSpan();
        LineSpan prev = null;
        LineSpan current = result;
        int nextParagraph;
        paragraphRun:
        for (int p = start; p < end; p = nextParagraph) {
            nextParagraph = text.nextSpanTransition(p, end, ParagraphStyle.class);
            current.paragraphStyles = text.getSpans(p, nextParagraph, ParagraphStyle.class);
            current.paragraphStart = true;
            current.paragraphStartMargin = paragraphStartMargin;
            current.paragraphTopMargin = paragraphTopMargin;
            current.gravity = Gravity.NO_GRAVITY;
            int margin = 0;
            int marginLineCount = 0;
            int marginEnd = 0;

            for (ParagraphStyle ps : current.paragraphStyles) {
                if (ps instanceof LeadingMarginSpan) {
                    margin = ((LeadingMarginSpan) ps).getLeadingMargin(true);
                } else if (ps instanceof LeadingMarginSpan.LeadingMarginSpan2) {
                    marginLineCount = ((LeadingMarginSpan.LeadingMarginSpan2) ps).getLeadingMarginLineCount();
                    marginEnd = text.getSpanEnd(ps);
                } else if (ps instanceof AlignmentSpan) {
                    AlignmentSpan alignment = (AlignmentSpan) ps;
                    if (alignment.getAlignment() == Layout.Alignment.ALIGN_CENTER) {
                        current.gravity = Gravity.CENTER_HORIZONTAL;
                    } else if (alignment.getAlignment() == Layout.Alignment.ALIGN_OPPOSITE) {
                        // so... to right ?
                        if (defaultDirection == Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT) {
                            current.gravity = Gravity.RIGHT;
                        } else {
                            current.gravity = Gravity.LEFT;
                        }
                    }
                }
            }

            int nextCharacterStyle;
            stylesRun:
            for (int c = p; c < nextParagraph; c = nextCharacterStyle) {
                nextCharacterStyle = text.nextSpanTransition(c, nextParagraph, CharacterStyle.class);
                if (mBidiEnabled) { // direction on lineSpan-level
                    long bidiStart = SystemClock.uptimeMillis();
                    String bidiString = new String(text.subSequence(c, nextCharacterStyle).toString());
                    Bidi bidi = new Bidi(bidiString, defaultDirection);
                    // TODO: glue RTL word splitted into different LineSpan by CharacterStyle
                    // TODO: punktuation must be attracted to LTR (optionally?)
                    // NOTE: google chrome have same behavior - 'jumping brackets'
                    // NOTE: ltr chain in rtl paragraph wrapped to new line in same order, as it stored in memory (logical)
                    if (bidi.isRightToLeft()) {
                        current.direction = Layout.DIR_RIGHT_TO_LEFT;
                        if (Build.VERSION.SDK_INT<14)
                            current.reversed = TextUtils.getReverse(text,c,nextCharacterStyle);
                        bidiSpent += (SystemClock.uptimeMillis() - bidiStart);
                        if (mBidiDebug)
                        Log.d(TAG,
                                "bidi:runDirection:["
                                        + c+"," + nextCharacterStyle
                                        + " dir: " + bidi.getBaseLevel() + " [RTL]"
                                        + " '" + text.subSequence(c,nextCharacterStyle) + "'" // TextUtils.getReverse(text,c,nextCharacterStyle) + "'"
                        );
                    } else if (bidi.isMixed()) {
                        /* int base = bidi.getBaseLevel(); */
                        int runLimit = c;
                        for(int run = 0; run < bidi.getRunCount(); run++ ) {
                            // TODO:
                            int runStart = bidi.getRunStart(run) + c;
                            runLimit = bidi.getRunLimit(run) + c;
                            int runLevel = bidi.getRunLevel(run);
                            boolean isRtl = runLevel % 2 > 0;
                            current.direction = isRtl ? Layout.DIR_RIGHT_TO_LEFT : Layout.DIR_LEFT_TO_RIGHT;
                            if (mBidiDebug)
                            Log.d(TAG,
                                    "bidi:runDirection:["
                                    + runStart+"," + runLimit
                                    + " rtl: " + isRtl + " ("+runLevel+")"
                                    + " '" +  text.subSequence(runStart,runLimit) + "'"// (runIsRtl ? TextUtils.getReverse(text,runStart,runLimit) : text.subSequence(runStart,runLimit)) + "'"
                            );
                            // inject new span with single direction
                            current.start = runStart;
                            current.end = runLimit;
                            if (runLevel == 2 && Build.VERSION.SDK_INT<14) { // old version does not render RTL correct
                                current.reversed = TextUtils.getReverse(text,runStart,runLimit);
                            }
                            current.spans = text.getSpans(current.start, current.end, CharacterStyle.class);
                            if (current.spans.length>0 && current.spans[0] instanceof DynamicDrawableSpan) {
                                forwardsDrawableArray.append(c, (DynamicDrawableSpan) current.spans[0]);
                            }

                            // each linespan within paragraph inherits paragraph margins
                            current.margin = margin;
                            current.marginLineCount = marginLineCount;
                            current.marginEnd = marginEnd;

                            current.next = new LineSpan();
                            current.next.paragraphStyles = current.paragraphStyles;
                            current.next.gravity = current.gravity;
                            prev = current;
                            current = current.next;
                        }
                        bidiSpent += (SystemClock.uptimeMillis() - bidiStart);
                        continue stylesRun;
                    }

                }
                current.spans = text.getSpans(c, nextCharacterStyle, CharacterStyle.class);
                if (current.spans.length>0 && current.spans[0] instanceof DynamicDrawableSpan) {
                    forwardsDrawableArray.append(c, (DynamicDrawableSpan) current.spans[0]);
                }
                current.start = c;
                current.end = nextCharacterStyle;

                // each linespan within paragraph inherits paragraph margins
                current.margin = margin;
                current.marginLineCount = marginLineCount;
                current.marginEnd = marginEnd;

                current.next = new LineSpan();
                current.next.paragraphStyles = current.paragraphStyles;
                current.next.gravity = current.gravity;
                prev = current;
                current = current.next;
            }
            current.paragraphEnd = true;
        }
        if (prev.next != null) {
            prev.next = null;
        }

        current.next = null; // remove last lineSpan
        long timeSpent = SystemClock.uptimeMillis() - timeStart;
        if (debug)
            Log.e(TAG, "prepare time spent: " + timeSpent +", bidi spent: " + bidiSpent);
        return result;
    }


    private static void test_breaks_loops(List<LineSpanBreak> breaks, LineSpanBreak lineBreak) throws Exception {
        while (lineBreak != null) {
            if (breaks.contains(lineBreak))
                throw new Exception("break already met" + lineBreak);
            breaks.add(lineBreak);
            lineBreak = lineBreak.next;
        }
    }

    // FIXME: need measure state, and lazy measuring for spans on demand

    /**
     * @param lineSpan - start span for measure
     * @param text     - text from spanned
     * @param paint    - text paint
     * @param lasy     - if true, measurement breaks after span are measured
     */

    protected static void measure(LineSpan lineSpan, char[] text, TextPaint paint, boolean lasy) {
        // Log.v(TAG, "measure with font size:" + paint.getTextSize());
        TextPaint workPaint = new TextPaint();
        Paint.FontMetricsInt fmi = new Paint.FontMetricsInt();
        recursive:
        for (LineSpan span = lineSpan; span != null; span = span.next) {
            span.clearCache(lasy);
            workPaint.set(paint);

            ReplacementSpan replacement = null;

            if (span.spans != null)
                for (CharacterStyle style : span.spans) {
                    if (style instanceof MetricAffectingSpan) {
                        if (style instanceof ReplacementSpan) {
                            replacement = (ReplacementSpan) style;
                            break;
                        }
                        ((MetricAffectingSpan) style).updateMeasureState(workPaint);
                    } else if (style instanceof ClickableSpan) {
                        span.strong = true;
                    }
                }

            /* */
            if (replacement == null) {
                span.widths = new float[span.end - span.start];
                workPaint.getTextWidths(text, span.start, span.end - span.start, span.widths);
                workPaint.getFontMetricsInt(fmi);
                span.width = 0f;

                for (int i = 0; i < span.widths.length; i++)
                    span.width += span.widths[i];

                span.baselineShift = -fmi.ascent;
                span.height = -fmi.ascent + fmi.descent;
                span.leading = -fmi.leading;
                span.descent = fmi.descent;
                span.hyphenWidth = paint.measureText("-");
            } else {
                if (replacement instanceof DynamicDrawableSpan) {
                    span.isDrawable = true;
                    DynamicDrawableSpan image = (DynamicDrawableSpan) replacement;
                    Drawable drawable = image.getDrawable();
                    if (drawable == null) {
                        span.width = replacement.getSize(paint, new String(text), span.start, span.end, fmi);
                        span.height = -fmi.ascent + fmi.descent;
                    } else {
                        // stub !
                        span.width = drawable.getIntrinsicWidth();
                        span.height = drawable.getIntrinsicHeight();
                    }
                    span.widths = new float[]{span.width};
                } else {
                    span.width = replacement.getSize(paint, new String(text), span.start, span.end, fmi);
                }
            }
            if (lasy) break;
        }
        // test_span_loops(lineSpan);
    }

    /**
     * measure all spans chain
     *
     * @param lineSpan - start span
     * @param text     - text
     * @param paint    - paint
     */

    protected static void measure(LineSpan lineSpan, char[] text, TextPaint paint) {
        measure(lineSpan, text, paint, false);
    }

    /**
     * convert debug representaion of subsequence of chars
     *
     * @param text
     * @param start
     * @param end
     * @return
     */

    private static String dbgString(char[] text, int start, int end) {
        return " string='" + new String(text, start, end - start) + "'";
    }

    static int imageAlignmentToGravity(Layout.Alignment alignment) {
        if (alignment == Layout.Alignment.ALIGN_NORMAL)
            return Gravity.LEFT;
        else if (alignment == Layout.Alignment.ALIGN_OPPOSITE)
            return Gravity.RIGHT;
        else
            return Gravity.CENTER_HORIZONTAL;
    }


    protected DynamicDrawableSpan getDrawable() {
        if (spans != null && spans.length > 0) {
            for (CharacterStyle style : spans)
                if (style instanceof DynamicDrawableSpan)
                    return (DynamicDrawableSpan) style;
        }
        return null;
    }

    public synchronized void clearCache(boolean lasy) {
        for (LineSpan span = this; span != null; span = span.next) {
            span.breakFirst = null;
            if (span.isDrawable) {
                span.width = 0;
                span.drawableScaledHeight = 0f;
                span.drawableClipHeight = 0f;
                span.drawableClipWidth = 0f;
                span.drawableScaledWidth = 0f;
                span.drawableScrollX = 0f;
                span.drawableScrollY = 0f;
            }
            if (lasy) break;
        }
    }

    public LineSpanBreak newBreak() {
        return new LineSpanBreak();
    }

    public String toString(boolean dumpBreaks) {
        try {
            return String.format("LineSpan: start = %d, end = %d, width = %.2f, widths.length = %d, height = %d, hyphenWidth = %.2f, breakFirst = %s, direction = %d, baseLineShift = %d",
                    this.start,
                    this.end,
                    this.width,
                    this.widths == null ? -1 : this.widths.length,
                    this.height,
                    this.hyphenWidth,
                    dumpBreaks ? breaksToString(this.breakFirst) : this.breakFirst !=null,
                    this.direction,
                    this.baselineShift
            );
        } catch (NullPointerException e) {
            return "(NullPointerException)";
        }
    }

    public String toString() {
        return toString(false);
    }

    public String toString(char[] text, boolean dumpBreaks) {
        try {
            return String.format("LineSpan: start = %d, end = %d, width = %.2f, widths.length = %d, height = %d, hyphenWidth = %.2f, breakFirst = %s, direction = %d, baseLineShift = %d, %s",
                    this.start,
                    this.end,
                    this.width,
                    this.widths == null ? -1 : this.widths.length,
                    this.height,
                    this.hyphenWidth,
                    dumpBreaks ? breaksToString(this.breakFirst) : this.breakFirst,
                    this.direction,
                    this.baselineShift,
                    dbgString(text, this.start, this.end).replace('\n', '^')
            );
        } catch (NullPointerException e) {
            return "(NullPointerException)";
        }
    }

    public String breaksToString(LineSpanBreak spanBreak) {
        StringBuilder sb = new StringBuilder();
        int counter = 0;
        while (spanBreak != null) {
            sb.append(String.format("[p=%d,c=%s,s=%s,w=%.2f,skip=%d,tail=%.2f],", spanBreak.position, spanBreak.carrierReturn, spanBreak.strong, spanBreak.width, spanBreak.skip, spanBreak.tail));
            spanBreak = spanBreak.next;
            if (counter > 2) {
                sb.append("\n\t");
                counter = 0;
            }
            counter++;
        }
        if (sb.length() < 1)
            return "";
        return sb.subSequence(0, sb.length() - 1).toString();
    }

    public String dump() {
        String nextStr = next == null ? "" : "\n" + next.dump();
        return toString() + nextStr;
    }

    public String dump(char[] text, int limit, boolean dumpBreaks) {
        if (limit < -1)
            return dump(text, dumpBreaks);
        if (limit == 0)
            return "";
        String nextStr = next == null ? "" : "\n" + next.dump(text, limit - 1, dumpBreaks);
        return toString(text, dumpBreaks) + nextStr;
    }

    public String dump(char[] text, boolean dumpBreaks) {
        String nextStr = next == null ? "" : "\n" + next.dump(text, dumpBreaks);
        return toString(text, dumpBreaks) + nextStr;
    }



    public static void clearMeasurementData(LineSpan span) {
        for (; span != null; span = span.next) {
            span.widths = null;
            span.height = 0;
            span.leading = 0;
            span.descent = 0;
            span.baselineShift = 0;
            span.width = 0;
            span.breakFirst = null;
        }
    }
}