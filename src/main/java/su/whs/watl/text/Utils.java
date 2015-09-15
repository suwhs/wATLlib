package su.whs.watl.text;

import android.graphics.Rect;
import android.text.Layout;
import android.text.TextPaint;

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

        if (direction != Layout.DIR_LEFT_TO_RIGHT)
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

            if (span.direction != Layout.DIR_RIGHT_TO_LEFT) {
                if (ltrRun < 1f) {
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
                while (lineSpanBreak != null) {
                    if (x - ltrRun + (lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument)) < atX) { // x point to character in lineSpanBreak (LTR)
                        int i;
                        if (span.widths == null) {
                            LineSpan.measure(span, text, paint, true);
                            needCleanUp = true;
                        }
                        for (i = run; i <= lineSpanBreak.position && (x - ltrRun + span.widths[i - span.start]) < atX; i++) {
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
                if (span != null) {
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

    public static float runLineSpanToIndex(char[] text, TextPaint paint, LineSpan span, LineSpanBreak spanBreak, int lineStart, int index, int direction, float justifyArgument, int width, Rect textPaddings, Rect drawablePaddings) {
        if (direction == Layout.DIR_RIGHT_TO_LEFT)
            return runLineSpanToIndexRtl(text, paint, span, spanBreak, lineStart, index, justifyArgument, width, textPaddings, drawablePaddings);

        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;

        float x = 0;
        boolean needCleanUp = false;
        int run = lineStart; // decrement x until run < index
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
                x += drawableWidth;
            }
            float tail = span.width;


            runBreaks:
            while (lineSpanBreak != null) {
                if (lineSpanBreak.position >= index) {
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < lineSpanBreak.position && i < index; i++) {
                        x += span.widths[i - span.start];
                    }
                    if (needCleanUp) span.widths = null;
                    return x;
                }
                if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                    return x;
                }
                x += lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                tail = lineSpanBreak.tail;
                run = lineSpanBreak.position + 1 + lineSpanBreak.skip;
                lineSpanBreak = lineSpanBreak.next;
            }
            if (span.end > run && span.end > index) {
                int i;
                if (span.widths == null) {
                    LineSpan.measure(span, text, paint, true);
                    needCleanUp = true;
                }
                for (i = run; i < span.end && i < index; i++)
                    x += span.widths[i - span.start];
                if (needCleanUp) span.widths = null;
                return x;
            }
            x += tail > 0f ? tail : 0f;
            span = span.next;
            if (span != null) {
                lineSpanBreak = span.breakFirst;
                run = span.start + span.skip;
            }
        }

        return x;

    }

    private static float runLineSpanToIndexRtl(
            char[] text,
            TextPaint paint,
            LineSpan span,
            LineSpanBreak spanBreak,
            int lineStart,
            int index,
            float justifyArgument,
            int width,
            Rect textPaddings,
            Rect drawablePaddings) {
        int drawablePaddingsWidth = drawablePaddings.left + drawablePaddings.right;
        float ltrRun = 0f;
        float x = width;
        boolean needCleanUp = false;
        int run = lineStart; // decrement x until run < index
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
                x -= drawableWidth;
            }
            float tail = span.width;

            if (span.direction == Layout.DIR_LEFT_TO_RIGHT) {
                if (ltrRun < 1f) {
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
                while (lineSpanBreak != null) {
                    if (lineSpanBreak.position >= index) { // index point to character in lineSpanBreak (LTR)
                        int i;
                        if (span.widths == null) {
                            LineSpan.measure(span, text, paint, true);
                            needCleanUp = true;
                        }
                        for (i = run; i <= lineSpanBreak.position && i < index; i++) {
                            ltrRun -= span.widths[i - span.start];
                        }
                        if (needCleanUp) span.widths = null;
                        return x - ltrRun;
                    }
                    if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                        return x;
                    }
                    ltrRun -= lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                    lineSpanBreak = lineSpanBreak.next;
                }
                if (span.end > run && span.end > index) { // x point to character in ltr tail
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < span.end && i < index; i++)
                        x -= span.widths[i - span.start];
                    if (needCleanUp) span.widths = null;
                    return x;
                }
                x -= tail > 0f ? tail : 0;
                span = span.next;
                if (span != null) {
                    lineSpanBreak = span.breakFirst;
                    run = span.start + span.skip;
                }
            } else {
                runBreaks:
                while (lineSpanBreak != null) {
                    if (lineSpanBreak.position >= index) {
                        int i;
                        if (span.widths == null) {
                            LineSpan.measure(span, text, paint, true);
                            needCleanUp = true;
                        }
                        for (i = run; i < lineSpanBreak.position && i < index; i++) {
                            x -= span.widths[i - span.start];
                        }
                        if (needCleanUp) span.widths = null;
                        return x;
                    }
                    if (lineSpanBreak.carrierReturn) { // reached end of line, so returns last character on line
                        return x;
                    }
                    x -= lineSpanBreak.width + (lineSpanBreak.strong ? 0f : justifyArgument);
                    tail = lineSpanBreak.tail;
                    run = lineSpanBreak.position + 1 + lineSpanBreak.skip;
                    lineSpanBreak = lineSpanBreak.next;
                }
                if (span.end > run && span.end > index) {
                    int i;
                    if (span.widths == null) {
                        LineSpan.measure(span, text, paint, true);
                        needCleanUp = true;
                    }
                    for (i = run; i < span.end && i < index; i++)
                        x -= span.widths[i - span.start];
                    if (needCleanUp) span.widths = null;
                    return x;
                }
                x += tail > 0f ? tail : 0f;
                span = span.next;
                if (span != null) {
                    lineSpanBreak = span.breakFirst;
                    run = span.start + span.skip;
                }
            }
        }

        return x;
    }
}
