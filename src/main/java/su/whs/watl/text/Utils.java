package su.whs.watl.text;

import android.graphics.Rect;
import android.text.Layout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 09.09.15.
 */
public class Utils {
    // reference function
    public static int runLineSpanToX(
            char[] text,
            TextPaint paint,
            LineSpan span,
            LineSpanBreak spanBreak,
            int lineStart,
            float atX,
            int direction,
            float justifyArgument,
            int width,
            Rect textPaddings,
            Rect drawablePaddings) {

        if (direction == Layout.DIR_RIGHT_TO_LEFT)
            return runLineSpanToXRtl(text, paint, span, spanBreak, lineStart, atX, Layout.DIR_RIGHT_TO_LEFT, justifyArgument, width, textPaddings, drawablePaddings);

        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;

        float x = 0f;
        boolean needCleanUp = false;
        int run = lineStart;
        LineSpanBreak lineSpanBreak = spanBreak;
        while (span.end <= run && span.next != null) span = span.next;
        while (lineSpanBreak != null && lineSpanBreak.position < run)
            lineSpanBreak = lineSpanBreak.next;
        if (lineSpanBreak != null && lineSpanBreak.position == span.end) {
            span = span.next;
        }
        runSpans:
        while (span != null) {
            if (span.isDrawable) {
                float drawableWidth = drawablePaddingsWidth + (span.drawableScaledWidth > 0f ? span.drawableScaledWidth : span.width);
                if (x + drawableWidth > atX) {
                    return span.start;
                }
            }
            float tail = span.width;
            runBreaks:
            while (lineSpanBreak != null) {
                if (x + lineSpanBreak.width > atX) {
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < lineSpanBreak.position && (x + span.widths[i - span.start] < atX); i++) {
                        x += span.widths[i - span.start];
                    }
                    if (needCleanUp) span.widths = null;
                    return i;
                }
                if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                    return lineSpanBreak.position;
                }
                x += lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                tail = lineSpanBreak.tail;
                run = lineSpanBreak.position + 1 + lineSpanBreak.skip;
                lineSpanBreak = lineSpanBreak.next;
            }
            if (span.end > run && x + tail > atX) {
                int i;
                if (span.widths == null) {
                    LineSpan.measure(span, text, paint, true);
                    needCleanUp = true;
                }
                for (i = run; i < span.end && (x + span.widths[i - span.start]) < atX; i++)
                    x += span.widths[i - span.start];
                if (needCleanUp) span.widths = null;
                return i;
            }
            x += tail > 0f ? tail : 0f;
            span = span.next;
            if (span != null) {
                lineSpanBreak = span.breakFirst;
                run = span.start + span.skip;
            }
        }

        return run;
    }

    private static int runLineSpanToXRtl(
            char[] text,
            TextPaint paint,
            LineSpan span,
            LineSpanBreak spanBreak,
            int lineStart,
            float atX,
            int direction,
            float justifyArgument,
            int width,
            Rect textPaddings,
            Rect drawablePaddings) {
        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;
        float ltrRun = 0f;
        float x = width;
        boolean needCleanUp = false;
        int run = lineStart;
        LineSpanBreak lineSpanBreak = spanBreak;
        while (span.end <= run && span.next != null) span = span.next;
        while (lineSpanBreak != null && lineSpanBreak.position < run)
            lineSpanBreak = lineSpanBreak.next;
        if (lineSpanBreak != null && lineSpanBreak.position == span.end) {
            span = span.next;
        }
        runSpans:
        while (span != null) {
            if (span.isDrawable) {
                float drawableWidth = drawablePaddingsWidth + (span.drawableScaledWidth > 0f ? span.drawableScaledWidth : span.width);
                if (x - drawableWidth < atX) {
                    return span.start;
                }
            }
            float tail = span.width;

            if (span.direction == Layout.DIR_LEFT_TO_RIGHT) {
                if (ltrRun<1f) {
                    LineSpan ltrSpan = span;
                    for (; ltrSpan != null && ltrSpan.direction == Layout.DIR_LEFT_TO_RIGHT; ltrSpan = ltrSpan.next) {
                        LineSpanBreak ltrBreak = ltrSpan.breakFirst;
                        for (; ltrBreak != null; ltrBreak = ltrBreak.next) {
                            tail = ltrBreak.tail;
                            ltrRun += ltrBreak.width + (ltrBreak.strong ? 0f : justifyArgument);
                        }
                    }
                    if (tail > 0f)
                        ltrRun += tail;
                }
                runBreaksLtr:
                while(lineSpanBreak !=null) {
                    if (x - ltrRun + (lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument)) < atX) { // x point to character in lineSpanBreak (LTR)
                        int i;
                        if (span.widths == null) {
                            LineSpan.measure(span, text, paint, true);
                            needCleanUp = true;
                        }
                        for (i=run; i<=lineSpanBreak.position && (x - ltrRun + span.widths[i - span.start]) < atX; i++) {
                            ltrRun -= span.widths[i - span.start];
                        }
                        if (needCleanUp) span.widths = null;
                        return i;
                    }
                    if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                        return lineSpanBreak.position;
                    }
                    ltrRun -= lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                    lineSpanBreak = lineSpanBreak.next;
                }
                if (span.end > run && x - ltrRun - tail < atX) { // x point to character in ltr tail
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < span.end && (x - ltrRun + span.widths[i - span.start]) > atX; i++)
                        x -= span.widths[i - span.start];
                    if (needCleanUp) span.widths = null;
                    return i;
                }
                x -= tail > 0f ? tail : 0;
                span = span.next;
                if (span!=null) {
                    lineSpanBreak = span.breakFirst;
                    run = span.start + span.skip;
                }
            } else {
                runBreaks:
                while (lineSpanBreak != null) {
                    if (x - lineSpanBreak.width < atX) {
                        int i;
                        if (span.widths == null) {
                            LineSpan.measure(span, text, paint, true);
                            needCleanUp = true;
                        }
                        for (i = run; i < lineSpanBreak.position && (x - span.widths[i - span.start] > atX); i++) {
                            x -= span.widths[i - span.start];
                        }
                        if (needCleanUp) span.widths = null;
                        return i;
                    }
                    if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                        return lineSpanBreak.position;
                    }
                    x -= lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                    tail = lineSpanBreak.tail;
                    run = lineSpanBreak.position + 1 + lineSpanBreak.skip;
                    lineSpanBreak = lineSpanBreak.next;
                }
                if (span.end > run && x - tail < atX) {
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < span.end && (x - span.widths[i - span.start]) > atX; i++)
                        x -= span.widths[i - span.start];
                    if (needCleanUp) span.widths = null;
                    return i;
                }
                x += tail > 0f ? tail : 0f;
                span = span.next;
                if (span != null) {
                    lineSpanBreak = span.breakFirst;
                    run = span.start + span.skip;
                }
            }
        }

        return run;

    }

    /**
     * returns drawing start coordinate for character at position 'index'
     * note, if span are LTR - returns left edge of character,
     * else (for RTL) - returns right bound
     */

    public static float runLineSpanToIndex(char[] text, TextPaint paint, LineSpan span, LineSpanBreak spanBreak, int index, int direction, float justifyArgument, int width, Rect textPaddings, Rect drawablePaddings) {
        if (direction == Layout.DIR_RIGHT_TO_LEFT)
            return runLineSpanToIndexRtl(text, paint, span, spanBreak, index, justifyArgument, width, textPaddings, drawablePaddings);
        float x = 0f;
        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;
        int runStart;
        boolean needCleanUp = false;
        runSpans:
        while (span != null) {
            // here we handle ONLY inline drawables.
            if (span.isDrawable) {
                float drawableWidth = span.width;
                if (span.drawableScaledWidth > 0)
                    drawableWidth = span.drawableScaledWidth + drawablePaddingsWidth;
                x += drawableWidth;
                spanBreak = spanBreak == null ? span.breakFirst : spanBreak.next;
                span = span.next;
                continue runSpans;
            }
            if (spanBreak == null) spanBreak = span.breakFirst;
            runStart = spanBreak == null ? span.start : spanBreak.position + spanBreak.skip;
            if (index <= runStart) return x;
            float tail = 0f;
            if (spanBreak == null) {
                if (span.end > index) {
                    if (span.widths == null) {
                        needCleanUp = true;
                        LineSpan.measure(span, text, paint, true);
                    }
                    for (int i = runStart; i < span.end; i++) {
                        x += span.widths[i = span.start];
                    }
                    if (needCleanUp)
                        span.widths = null;
                    break runSpans;
                }
                x += span.width;
            } else {
                runBreaks:
                while (spanBreak != null) {
                    if (spanBreak.position < index) {
                        x += spanBreak.width + (spanBreak.strong ? 0f : justifyArgument);
                        tail = spanBreak.tail;
                        runStart = spanBreak.position + 1 + spanBreak.skip;
                    } else { // index<=spanBreak.position
                        if (span.widths == null) {
                            needCleanUp = true;
                            LineSpan.measure(span, text, paint, true);
                        }
                        for (int i = runStart; i < index; i++) {
                            x += span.widths[i - span.start];
                        }
                        if (needCleanUp)
                            span.widths = null;
                        break runSpans;
                    }
                    if (spanBreak.carrierReturn)
                        break runSpans;
                    spanBreak = spanBreak.next;
                }
            }
            span = span.next;
            x += tail;
        }

        return x;
    }

    private static float runLineSpanToIndexRtl(char[] text, TextPaint paint, LineSpan span, LineSpanBreak spanBreak, int index, float justifyArgument, int width, Rect textPaddings, Rect drawablePaddings) {
        float x = width;
        int runStart;
        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;
        boolean needCleanUp = false;
        List<Float> innerLtrStack = new ArrayList<Float>();
        runSpans:
        while (span != null) {
            // here we handle ONLY inline drawables.
            if (span.isDrawable) {
                float drawableWidth = span.width;
                if (span.drawableScaledWidth > 0)
                    drawableWidth = span.drawableScaledWidth + drawablePaddingsWidth;
                x += drawableWidth;
                spanBreak = spanBreak == null ? span.breakFirst : spanBreak.next;
                span = span.next;
                continue runSpans;
            }
            boolean isLtr = span.direction == Layout.DIR_LEFT_TO_RIGHT;
            if (spanBreak == null) spanBreak = span.breakFirst;
            runStart = spanBreak == null ? span.start : spanBreak.position + spanBreak.skip;
            if (isLtr && innerLtrStack.size() < 1) {
                LineSpan ltrSpan = span;
                LineSpanBreak ltrSpanBreak = spanBreak;
                if (ltrSpanBreak == null) ltrSpanBreak = ltrSpan.breakFirst;
                innerLtrScanLoop:
                while (ltrSpan != null && ltrSpan.direction == Layout.DIR_LEFT_TO_RIGHT) {
                    if (ltrSpanBreak == null) {
                        for (int i = 0; i < innerLtrStack.size(); i++)
                            innerLtrStack.set(i, innerLtrStack.get(i) + ltrSpan.width);
                        innerLtrStack.add(ltrSpan.width);
                    } else {
                        float tail = 0f;
                        while (ltrSpanBreak != null) {
                            for (int i = 0; i < innerLtrStack.size(); i++)
                                innerLtrStack.set(i, innerLtrStack.get(i) + ltrSpanBreak.width + (ltrSpanBreak.strong ? 0f : justifyArgument));
                            tail = ltrSpanBreak.tail;
                            if (ltrSpanBreak.carrierReturn)
                                break innerLtrScanLoop;
                            ltrSpanBreak = ltrSpanBreak.next;
                        }
                        innerLtrStack.add(tail);
                    }
                    ltrSpan = ltrSpan.next;
                }
            }
            /* stack created, next loop until 'index' reached */
            if (index <= runStart) return width - x;
            float tail = 0f;
            if (spanBreak == null) {
                if (span.end > index) {
                    if (span.widths == null) {
                        needCleanUp = true;
                        LineSpan.measure(span, text, paint, true);
                    }
                    if (isLtr) {
                        float runWidth = innerLtrStack.remove(0);
                        x += runWidth;
                        for (int i = runStart; i < span.end && i < index; i++) {
                            x -= span.widths[i - span.start];
                        }
                    } else {
                        for (int i = runStart; i < span.end && i < index; i++) {
                            x += span.widths[i = span.start];
                        }
                    }
                    if (needCleanUp)
                        span.widths = null;
                    return width - x;
                }
                x += span.width;
            } else if (isLtr) {
                runBreaksLtr:
                while (spanBreak != null) {
                    x += innerLtrStack.remove(0);
                    if (spanBreak.position >= index) { // position >= index, so it is result lookup
                        if (span.widths == null) {
                            needCleanUp = true;
                            LineSpan.measure(span, text, paint, true);
                        }
                        for (int i = runStart; i < spanBreak.position; i++) {
                            x -= span.widths[i - span.start];
                        }
                        if (needCleanUp)
                            span.widths = null;
                        return width - x;
                    }
                    spanBreak = spanBreak.next;
                }
            } else {
                runBreaks:
                while (spanBreak != null) {
                    if (spanBreak.position < index) {
                        x += spanBreak.width + (spanBreak.strong ? 0f : justifyArgument);
                        tail = spanBreak.tail;
                        runStart = spanBreak.position + 1 + spanBreak.skip;
                    } else { // index<=spanBreak.position
                        if (span.widths == null) {
                            needCleanUp = true;
                            LineSpan.measure(span, text, paint, true);
                        }
                        for (int i = runStart; i < index; i++) {
                            x += span.widths[i - span.start];
                        }
                        if (needCleanUp)
                            span.widths = null;
                        break runSpans;
                    }
                    if (spanBreak.carrierReturn)
                        break runSpans;
                    spanBreak = spanBreak.next;
                }
            }
            span = span.next;
            x += tail;
        }
        return width - x;
    }
}
