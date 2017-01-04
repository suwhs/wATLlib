package su.whs.watl.text;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.style.DynamicDrawableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
import android.util.Log;
import android.view.Gravity;

import java.util.ArrayList;
import java.util.List;

import su.whs.watl.text.style.PreformattedSpan;
import su.whs.lazydrawable.parent.LazyDrawable;

/**
 * Created by igor n. boulliev on 11.01.16.
 */
class ReflowContext {
    private static final String TAG="ReflowContext";
    private boolean debug = false;

    private List<LineSpan> deffered = new ArrayList<LineSpan>();
    private ImagePlacementHandler imagePlacementHandler;
    private int viewHeightLeft;
    private int viewHeightDec;
    private Rect textPaddings;
    private Rect drawablePaddings = new Rect();
    private LineSpan wrappedSpan = null;
    private Point scale = new Point();
    private int wrapHeight = 0;
    private int wrapMargin = 0;
    private int wrapEnd = 0;
    private int y = 0;
    private int collectedHeight = 0;
    private int lineWidthDec = 0;
    private int wrapWidth = 0;
    private List<TextLine> result = new ArrayList<TextLine>();
    private int lineStartAt = 0;
    private List<ReflowState> stack = new ArrayList<ReflowState>();
    private ReflowState state;
    private LineSpan span;
    private int lineMargin;
    private LineBreaker lineBreaker;
    private float lineSpacingMultiplier = 1f;
    private int lineSpacingAdd = 0;
    private boolean forceBreak = false;
    private int[] geometry = new int[2];
    private int leadingMargin = 0;
    private boolean carrierReturn = false;
    private boolean lineStartsWithRtl = false;
    private boolean lineContainsRtlSpans = false;
    private int currentDirection = Layout.DIR_LEFT_TO_RIGHT;
    private int forcedParagraphLeftMargin = 0;
    private boolean isPreformatted = false;
    private int forcedParagraphTopMargin = 0;
    private int linesAddedInParagraph = 0;
    private LeadingMarginSpan leadingMarginSpan = null;
    private ContentView.Options options;
    private long timeQuantStart = System.currentTimeMillis();
    private LineSpan lastParagraphStarts = null;
    private ParagraphStyle[] paragraphStyles;
    private LeadingMarginSpan actualLeadingMarginSpan = null;
    private int lineBreakVal = -1;
    private int spanHeight = 0;
    private int spanLeading = 0;
    private int spanDescent = 0;
    private long steplimit = 150;
    private int textEnd;
    private int height = -1;
    private int width;
    private TextPaint paint;
    private int viewHeight = -1;
    private boolean justification;
    private ITextLayout mCallbacks;
    private TextPaint workPaint;
    private int mMaxLines = -1;

    public ReflowContext(char[] text, int lineStartAt, int textEnd, LineSpan _startSpan, float x, int width, int height, int viewHeight, int maxLines, TextPaint paint, ITextLayout callbacks) {
        this.textEnd = textEnd;
        this.width = width;
        this.height = height;
        this.viewHeight = viewHeight;
        this.paint = paint;
        this.workPaint = new TextPaint(paint);
        this.span = _startSpan;
        if (this.span==null) {
            return;
        }
        mCallbacks = callbacks;
        mMaxLines = maxLines;
        options = mCallbacks.getOptions();
        lineBreaker = options.getLineBreaker();
        imagePlacementHandler = options.getImagePlacementHandler();
        if (imagePlacementHandler == null)
            imagePlacementHandler = new ImagePlacementHandler.DefaultImagePlacementHandler();
        textPaddings = options.getTextPaddings();
        options.getDrawablePaddings(drawablePaddings);
        lineWidthDec = textPaddings.left + textPaddings.right;
        viewHeightDec = textPaddings.top + textPaddings.bottom;
        viewHeightLeft = viewHeight - viewHeightDec;
        wrapWidth = width - lineWidthDec;
        wrapEnd = y;
        this.lineStartAt = lineStartAt;
        lineSpacingMultiplier = options.getLineSpacingMultiplier();
        lineSpacingAdd = options.getLineSpacingAdd();
        justification = options.isJustification();
        if (width < 1) {
            if (mCallbacks.updateGeometry(geometry)) {
                this.width = geometry[0];
                this.viewHeight = geometry[1];
            } else {
                throw new IllegalArgumentException("reflow called with argument 'width' < 1");
            }
        }
        state = new ReflowState(span, x);
        state.startSpan = span;
        state.carrierReturnSpan = span;
        if (span.start < lineStartAt) {
            if (debug && span.end <= lineStartAt) {
                Log.e(TAG, "incorrect start span");
                return;
            } else {
                if (span.widths == null)
                    LineSpan.measure(span, text, workPaint, true);
                for (int i = span.start; i < lineStartAt; i++) {
                    state.processedWidth += span.widths[i - span.start];
                }
            }
            state.character = lineStartAt;
            state.lastWhitespace = lineStartAt;
        }
        forcedParagraphLeftMargin = options.getNewLineLeftMargin();
        forcedParagraphTopMargin = options.getNewLineTopMargin();
        steplimit = options.getReflowQuantize();
    }

    public void deferDrawable(LineSpan span) {
        deffered.add(span);
    }

    public boolean handleDefferedImages() {
        if (deffered.size() > 0) {
            LineSpan defferedSpan = deffered.remove(0);
            DynamicDrawableSpan defferedDrawableSpan = defferedSpan.getDrawable();
            return handleImage(defferedSpan, defferedDrawableSpan, false);
        }
        return true;
    }

    private boolean checkViewHeightExceed() {
        if (viewHeight > -1 && !mCallbacks.onProgress(result, y + state.height, true)) {
            return false;
        } else if (viewHeight > -1 && mCallbacks.updateGeometry(geometry)) {
                                /* special case - ask geometry for next portion */
            width = geometry[0];
            viewHeight = geometry[1];
        }

        if (viewHeight > -1) { // TODO: reorganize conditions
            collectedHeight += y;
            y = 0; // textPaddings.top;  // y zeroed only if viewHeight greater than -1
            viewHeightLeft = viewHeight - viewHeightDec;
            wrapWidth = width - lineWidthDec;
            wrapHeight = 0;
            wrapMargin = 0;
        }
        return true;
    }

    // FIXME: if image does not fit viewHeight - view does not invalidated (bug)
    public boolean handleImage(LineSpan span, DynamicDrawableSpan dds, boolean allowDefer) {
        int placement = imagePlacementHandler.place(dds,
                viewHeightLeft - 1,
                viewHeight - 1,
                wrapWidth,
                width,
                state.character,
                scale,
                options,
                allowDefer);

        boolean collectHeights = false;
        if (debug)
            Log.v(TAG, "handleImage:" + span);
        if (placement == ImagePlacementHandler.DEFER) {
            deffered.add(span);
            if (state.character == lineStartAt) {
                lineStartAt++;
            } // use __finishLine and return
            state.character++;
            return true;
        } else if (placement == ImagePlacementHandler.PLACEHOLDER) {
            span.noDraw = true;
            state.character++;
            Drawable dr = dds.getDrawable();
            if (dr != null && dr instanceof LazyDrawable) {
                mCallbacks.registerDrawable(dds, placement, state.character);
                ((LazyDrawable) dr).load();
                dr.setCallback(mCallbacks.getPlaceholderCallbacks());
            }
            return true;
        }

        int gravity = LineSpan.imageAlignmentToGravity(ImagePlacementHandler.getAlignment(placement));

        boolean finishLine = false;

        if (lineStartAt < state.character &&
                (imagePlacementHandler.isNewLineBefore(placement) ||
                        (imagePlacementHandler.isWrapText(placement) && wrapHeight > 0))) {
            if (debug) Log.v(TAG, "newLineBefore()");
            finishLine = true;
        }


        if (imagePlacementHandler.isWrapText(placement) && state.gravity != Gravity.CENTER_HORIZONTAL) {
            if (debug) Log.v(TAG, "wrapText");
            if (wrapHeight > 0) {
                if (debug)
                    Log.e(TAG, "close wrap null span"); // FIXME: here we are exeed viewHeight, but not tell to listener - check

                if (viewHeightLeft > -1 && viewHeightLeft < wrapHeight) {
                    if (!checkViewHeightExceed()) return false;
                }
                wrapMargin = 0;
                result.add(new TextLine(null, 0, wrapHeight));
                y += wrapHeight;
            }

            if (finishLine && !__finishLine()) return false;


            wrappedSpan = span;
            wrapHeight = scale.y /*+ drawablePaddings.top + drawablePaddings.bottom */;
                /* need to store y+wrapHeight as minimum height for layout */
            wrapEnd = collectedHeight + y + wrapHeight;

            int paddingWidth = drawablePaddings.right + drawablePaddings.left;
            wrapWidth = width - scale.x - paddingWidth;
            // TODO: use largest margin to split drawable and text (if wrapMargin==0 text left border==textPaddings.left)
            if (gravity == Gravity.LEFT) { // TODO:  if drawablePadding.right==0 - use textPadding.left instead
                // wrapMargin = scale.x + paddingWidth - textPaddings.left; // width - wrapWidth - paddingWidth - textPaddings.left;
                wrapMargin = width - (wrapWidth + textPaddings.left);
                wrapWidth -= textPaddings.left;
            } else if (gravity == Gravity.RIGHT) {
                wrapWidth -= textPaddings.right;
                wrapMargin = 0;
            }
            collectHeights = true;
            TextLine ld = new TextLine(span, scale.y, scale.x + paddingWidth, gravity);
            state.skipWhitespaces = true;
            result.add(ld);
            /* add image break */
            LineSpanBreak br = span.newBreak();
            br.position = state.character;
            br.strong = true;
            br.carrierReturn = false;
            state.span.breakFirst = br;
            state.prevBreak = state.lastBreak;
            state.carrierReturnSpan = state.span;
            state.lastBreak = br;
            lineStartAt = state.character + 1;
        } else if (imagePlacementHandler.isNewLineAfter(placement) || state.gravity == Gravity.CENTER_HORIZONTAL) {
            if (debug) Log.v(TAG, "newLineAfter");
            if (finishLine && !__finishLine()) return false;
            TextLine ld;

            if (state.height < (scale.y/* + drawablePaddings.bottom + drawablePaddings.bottom*/)) {
                state.height = scale.y/* + drawablePaddings.top + drawablePaddings.bottom*/;
            } // scale.y already contains paddings

            if (viewHeightLeft < state.height) {
                if (!checkViewHeightExceed()) return false;
            }
            y += state.height;
            viewHeightLeft -= state.height;

            if (height > -1 && y > height - 1) {
                // Log.v(TAG, "9 break recursion height=" + height + ", y=" + y);
                return false;
            }
            if (imagePlacementHandler.isNewLineBefore(placement)) {
                span.gravity = gravity;
            }
            ld = new TextLine(span, scale.x, state.height);
            ld.margin = lineMargin;
            ld.gravity = gravity;
            result.add(ld); // first call here
            state.breakLineAfterImage();
            wrapWidth = width - lineWidthDec;
            wrapMargin = 0;
            collectHeights = false;
            lineStartAt = state.span.end;
            carrierReturn = true;
        } else {
            // inline/or placeholder
            spanHeight = scale.y /* + drawablePaddings.top + drawablePaddings.bottom */;
            Log.e(TAG, "unsupported placement for image: " + placement);
        }
        span.drawableScaledWidth = scale.x;
        span.drawableScaledHeight = scale.y;
        Drawable dr = dds.getDrawable();

        if (dr instanceof LazyDrawable ) {
            ((LazyDrawable) dr).setCallbackCompat(mCallbacks.getDrawableCallbacks());
            ((LazyDrawable) dr).load();
        }
        if (dr != null) {
            mCallbacks.setDrawableBounds(dr, 0, 0, scale.x, scale.y); // dr.setBounds(0, 0, scale.x, scale.y);
        } else {
            Log.e(TAG, "no drawable on DynamicDrawableSpan: " + span);
        }

        mCallbacks.registerDrawable(dds,placement,state.character);

        if (collectHeights) {
            if (spanHeight > state.height) state.height = spanHeight;
            if (spanLeading > state.leading) state.leading = spanLeading;
            if (spanDescent > state.descent) state.descent = spanDescent;
        }
        state.skipWhitespaces = true;
        state.character++;

        return true; // switchSpan();
    }

    private boolean __finishLine() {
        if (debug) Log.v(TAG, "finishLine()");
        if (state.character < 1) return true;
        if (lineStartAt < state.character) {
            TextLine ld;
            y += state.height;
            viewHeightLeft -= state.height;

            if (height > -1 && y > height - 1) {
                if (debug) Log.v(TAG, "4 break recursion while finish line");
                return false;
            }

            ld = new TextLine(state, lineStartAt, leadingMarginSpan);
            ld.margin = lineMargin + wrapMargin;
            ld.wrapMargin = wrapMargin;
            ld.end--;

            state.breakLine(false, ld); // carrierReturn(ld);
            result.add(ld);
            // state.character--; // correct shifted by carrierReturn() position;
            // WARN: do not execute justification on line before images
            lineStartAt = state.character;
                                /* handle wrap ends */
            wrapHeight -= ld.height;
            state.skipWhitespaces = true;
        } else if (lineStartAt == state.character) {
            if (wrapHeight > 0) {
                result.add(new TextLine(null, 0, wrapHeight));   // TODO: need correctly handle in draw()
                wrapHeight = 0;
                wrapMargin = 0;
            }
            state.carrierReturnSpan = state.span; //
        }
        return true;
    }

    public int handleCarrierReturn(int direction) {
        try {
            state.processedWidth += span.widths[state.character - span.start];
        } catch (IndexOutOfBoundsException e) {
            // FIXME: dirty hack with try/catch
            // TODO: fix \n immediately after drawable span
            // Log.v(TAG,"text length = "+text.length+" span.widths.length="+span.widths.length);
        }
        if (lineStartAt == span.end) {
            state.carrierReturnSpan = state.span.next;
        }

                    /* eliminate empty line only if non-empty-lines-count > threshold */
        if (!isPreformatted && options.isFilterEmptyLines() && state.character == lineStartAt && linesAddedInParagraph < options.getEmptyLinesThreshold()) {
            TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
            ld.direction = direction;
            state.carrierReturn(ld);
            linesAddedInParagraph = 0;
        } else { // filterEmptyLines disabled, or state.character > lineStartAt, or linesAddedInParagraph >= emptyLinesTreshold
            TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
            ld.direction = direction;
            if (!isPreformatted && options.isFilterEmptyLines() && state.character == lineStartAt) {
                linesAddedInParagraph = 0;
                ld.height = options.getEmptyLineHeightLimit();
            } else
                linesAddedInParagraph++;
            y += ld.height;
                /* handle exceed view height */
            viewHeightLeft -= ld.height;
            result.add(ld);

            ld.margin = lineMargin + wrapMargin;
            ld.wrapMargin = wrapMargin;

                /* handle wrap ends */
            wrapHeight -= ld.height; // maybe after carrier return wrap margin must be resets?
            // state.carrierReturns ZEROES state.height
            state.carrierReturn(ld);

            if (wrapWidth < (width - lineWidthDec) && wrapHeight < 1) {
                if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                    int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                    wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight - textPaddings.top);
                    wrappedSpan = null;
                }

                wrapWidth = width - lineWidthDec;
                wrapMargin = 0;
                wrapHeight = height > viewHeight ? height : viewHeight;
                            /* FIXME: adjust vertical wrapped image position here ? */
            }
        }

        if (lineMargin > 0 || span.paragraphStart) {
            state.lineWidth = lineMargin;
        }

        carrierReturn = true;
        state.lineWidth += forcedParagraphLeftMargin;
        lineStartAt = state.character;
        state.skipWhitespaces = true;

        return lineStartAt >= span.end ? 0 : span.direction;
    }

    private int handleBreakLine(char[] text, int breakPosition, boolean hyphen, int direction) {
        boolean isWhitespace = text[breakPosition] == ' ';
        TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);
        ld.margin = lineMargin + wrapMargin;
        ld.wrapMargin = wrapMargin;
        ld.direction = direction;
        if (direction != Layout.DIR_LEFT_TO_RIGHT) {
            // Log.w(TAG, "break line == RTL");
        }
        result.add(ld); //secondCallHere
        ld.hyphen = hyphen;
//                    ld.width += hyphen ? span.hyphenWidth : 0;
        state.breakLine(isWhitespace, ld); // nb: breakLine does not increment state.character

        // if this line starts after '\n' - threat it as new paragraph starts
        if (carrierReturn) {
            ld.height += forcedParagraphTopMargin;
            // y += forcedParagraphTopMargin; (forcedParagraphTopMargin added twice!)

            if (leadingMarginSpan == null) { // apply paragraph margins only to lines without leading margin
                ld.margin += forcedParagraphLeftMargin;
                //ld.width += forcedParagraphLeftMargin;
            }
            leadingMarginSpan = null;
            carrierReturn = false;
        }

        if (justification && (ld.whitespaces > options.getJustificationThreshold() || (wrapWidth-ld.width) / wrapWidth < options.getJustificationFraction()))
            ld.justify(wrapWidth);

        /* handle exceed view height */
        y += ld.height; // was state.height at 1368 -- DEBUG
        viewHeightLeft -= ld.height;
                    /* handle wrap ends */
        wrapHeight -= ld.height;
                    /* check if wrap around drawable finished */
        if (wrapWidth < (width - lineWidthDec) && wrapHeight < 1) {
                        /* adjust vertical wrapped image position here */
            if (wrappedSpan != null) {
                            /* calculate actual heights, occupied by lines */
                int actualHeight = (int) (wrappedSpan.drawableScaledHeight + (wrapHeight * -1));
                wrappedSpan.baselineShift = (int) (actualHeight - wrappedSpan.drawableScaledHeight);
                wrappedSpan = null;
            }

            wrapWidth = width - lineWidthDec;
            wrapMargin = 0;
            wrapHeight = (height > viewHeight ? height : viewHeight);
        }

        if (span.paragraphStart && lastParagraphStarts == span && lineMargin > span.margin) {
            lineMargin = span.margin;
        }

        if (lineMargin > 0) {
            state.lineWidth = lineMargin;
        }

        state.character++; // nb: breakline does not increment state.character
        state.skipWhitespaces = true;
        lineStartAt = state.character;
        linesAddedInParagraph++;
        return direction; // direction;
    }

    public boolean nextSpan() {
        if (span.next == null) return false;
        span = span.next;
            /* clear span-depended fields */
        return true;
    }

    public boolean nextLine() {
            /* clear line-depended fields */
        return true;
    }

    private final int DIR_LTR = 1;
    private final int DIR_RTL = -1;
    private final int DIR_DEFAULT_LTR = 2;
    private final int DIR_DEFAULT_RTL = -2;


    public void process(char[] text) {
            /* recursion function port */
        int direction = 0;
        recursion:
        while (span != null) {
            span.clearCache(true);
            long currentTime = System.currentTimeMillis();
            long timeSpent = currentTime - timeQuantStart;
            if (span.isBidiEnabled() && span.direction != direction) {
                // if direction changes from 0 to 1 or -1 - force line break on other
                if (direction == 0)
                    direction = span.direction;
                else
                    switch (span.direction) {
                        case DIR_LTR:
                            switch (direction) {
                                case DIR_RTL: // mixed, started from RTL
                                    direction = DIR_DEFAULT_RTL;
                                    break;
                            }
                            break;
                        case DIR_RTL:
                            switch (direction) {
                                case DIR_LTR: // mixed, started from LTR
                                    direction = DIR_DEFAULT_LTR;
                                    break;
                            }
                            break;
                    }
            } else if (!span.isBidiEnabled()) {
                direction = Layout.DIR_LEFT_TO_RIGHT;
            }
            /* if we spent more times, than steplimit - execute callback */
            if (timeSpent > steplimit) {
                /* note - if viewHeightExceed == false - break loop if onProgress() returns false */
                if (!mCallbacks.onProgress(result, y + collectedHeight, false)) {
                    return;
                }
                timeQuantStart = currentTime;
            }

            leadingMargin = span.margin;

            workPaint.set(paint);

            if (span.isDrawable) {
                if (span.width == 0)
                    LineSpan.measure(span, text, workPaint);
            } else if (span.widths == null) {
                LineSpan.measure(span, text, workPaint);
            }

            // FIXME: move handling of LeadingMarginSpan2 from draw here

            if (span.paragraphStyles != paragraphStyles) {
                leadingMarginSpan = null;
                isPreformatted = false;
                if (span.paragraphStyles != null)
                    for (ParagraphStyle paragraphStyle : span.paragraphStyles) {
                        if (paragraphStyle instanceof LeadingMarginSpan) {
                            leadingMarginSpan = (LeadingMarginSpan) paragraphStyle;
                            if (leadingMarginSpan != actualLeadingMarginSpan) {
                                actualLeadingMarginSpan = leadingMarginSpan;
                            }
                        } else if (paragraphStyle instanceof PreformattedSpan) {
                            isPreformatted = true;
                        }
                    }
                paragraphStyles = span.paragraphStyles;
            }

            if (!span.isDrawable) {
                spanLeading = (int) span.leading;
                spanHeight = (int) (span.height + span.descent + (spanLeading * lineSpacingMultiplier) + lineSpacingAdd);
                spanDescent = span.descent;
            }

            if (spanHeight > state.height) {
                state.height = spanHeight; // maximum state changed, check if line too much height
                if ((viewHeight > -1) && viewHeightLeft < state.height) { // TODO: check span.isDrawable
                    if (!mCallbacks.onProgress(result, y + collectedHeight, true))
                        break recursion;
                    if (mCallbacks.updateGeometry(geometry)) {
                        width = geometry[0];
                        viewHeight = geometry[1];
                    }
                    y = 0;  // y zeroed only if viewHeight greater than -1
                    viewHeightLeft = viewHeight - viewHeightDec;
                    wrapWidth = width - lineWidthDec;
                    wrapHeight = 0;
                    wrapMargin = 0;
                }
            }
            if (spanLeading > state.leading) state.leading = spanLeading;
            if (spanDescent > state.descent) state.descent = spanDescent;

            if (!span.isDrawable && height > -1 && (y + state.height > height)) {
                break recursion;
            }

            lineMargin = leadingMargin;
            state.lineWidth += leadingMargin;
            if (state.lineWidth == 0f && (leadingMargin == 0 || span.paragraphStart)) {
                // lineMargin = leadingMargin;
                if (span.paragraphStart && span != lastParagraphStarts) {
                    lastParagraphStarts = span;
                    lineMargin += span.paragraphStartMargin;
                    state.height += span.paragraphTopMargin;
                }

            }


            /* flag */
            boolean drawableScaleBreak = false;

            // begin handle span
            /* forceBreak required if we revert char sequence due to hyphenation/linebreak algorygthm */
            processing:
            while (state.character < span.end || forceBreak) {
                if (state.skipWhitespaces) { // handle skip whitespaces
                    if (mMaxLines>-1 && result.size()>=mMaxLines) break recursion;
                    if (carrierReturn && state.lineWidth < 0.5f)
                        state.lineWidth = lineMargin;
                    state.doSkipWhitespaces(text);
                    if (lineStartAt < state.character) // skipWhitespaces must works only at lineStart, so this code execute after line ends
                        lineStartAt = state.character;
                    forceBreak = false; // cancel forcebreak
                    // restore heights and checking if new line exeed view Height
                    if (state.character < span.end) { // guard
                        // recover spanHeight
                        if (!span.isDrawable) {
                            spanLeading = (int) span.leading;
                            spanHeight = (int) (span.height + span.descent + (spanLeading * lineSpacingMultiplier) + lineSpacingAdd);
                            spanDescent = span.descent;
                        }

                        if (spanHeight > state.height) state.height = spanHeight;
                        if (spanLeading > state.leading) state.leading = spanLeading;
                        if (spanDescent > state.descent) state.descent = spanDescent;
                        // check if new line exceed view height (height defined and collected height exceed)
                        if (height > -1 && collectedHeight + y + state.height + (carrierReturn ? forcedParagraphTopMargin : 0) > height) {
                            break recursion;
                        }
                        if (viewHeightLeft - state.height < 0 && !span.isDrawable) {
                            if (viewHeight > -1 && !mCallbacks.onProgress(result, y, true)) {
                                break recursion;
                            } else if (viewHeight > -1 && mCallbacks.updateGeometry(geometry)) {
                                /* special case - ask geometry for next portion */
                                width = geometry[0];
                                viewHeight = geometry[1];
                            }

                            if (viewHeight > -1) { // TODO: reorganize conditions
                                collectedHeight += y;
                                y = 0; // textPaddings.top;  // y zeroed only if viewHeight greater than -1
                                viewHeightLeft = viewHeight - viewHeightDec;
                                wrapWidth = width - lineWidthDec;
                                wrapHeight = 0;
                                wrapMargin = 0;
                            }

                            /* handle deffered images */
                            if (deffered.size() > 0) {
                                if (!handleDefferedImages())
                                    break recursion;
                            } // END HANDLING DEFFERED IMAGES
                        } else if (viewHeightLeft - state.height < 0) {
                            // span are drawable, but no sufficient height here
                            if (debug) Log.e(TAG, "no sufficient height for drawable");
                        }
                    }
                    continue processing;
                } // end handle whitespaces block


                // view line width exceed or force break
                if ((forceBreak || state.lineWidth + span.widths[state.character - span.start] > wrapWidth) && !drawableScaleBreak) {
                    // TODO: while wrap image - we need to check - if too less chars fit to image side
                    // (wrong image placement handler may cause wrap too big image
                    if (span.isDrawable) {
                        drawableScaleBreak = true; // drawable does not fit left width at all
                        continue processing;
                    }
                    if (state.character <= lineStartAt + 1) {
                        if (debug) Log.w(TAG, "line exceed at first character");
                        if (wrapHeight > 0) {
                            // Log.e(TAG,"we must cancel wrapping!");
                            if (debug) Log.e(TAG, "close wrap null span");
                            result.add(new TextLine(null, 0, wrapHeight));
                            wrapMargin = 0;
                            viewHeightLeft -= wrapHeight;
                            if (viewHeight > -1 && viewHeightLeft - state.height < 0) {
                                if (!mCallbacks.onProgress(result, y + collectedHeight, true))
                                    break recursion;
                                if (mCallbacks.updateGeometry(geometry)) {
                                    width = geometry[0];
                                    viewHeight = geometry[1];
                                }
                                y = 0;
                                viewHeightLeft = viewHeight - viewHeightDec;
                            }
                            wrapMargin = 0;
                            wrapHeight = 0;
                            wrapWidth = width - lineWidthDec;
                            continue;
                        }
                        state.character++;
                    }

                    if (!forceBreak) // if forceBreak - lineBreakVal ALREADY contains position
                        lineBreakVal = lineBreaker.nearestLineBreak(text, state.lastWhitespace, state.character - 1, span.end);

                    // int lineBreakVal = forceBreak ? breakPosition : lineBreaker.nearestLineBreak(text, state.lastWhitespace, state.character - 1, span.end);
                    int breakPosition = LineBreaker.getPosition(lineBreakVal);
                    if (state.character == state.lastWhitespace) {
                        // Log.w(TAG, "first character on line does not fit to given width");
                        breakPosition = state.character;

                    }
                    boolean hyphen = LineBreaker.isHyphen(lineBreakVal);
//                        if (breakPosition < state.lastWhitespace) {
//                            // Log.e(TAG, "possible crash in future: error in lineBreaker:(" + lineBreaker + ") - nearestLineBreak(text,start,end,limit) must returns value, that greater or equal start");
//                        }
                    if (breakPosition <= lineStartAt) { // in some cases breakPosition<lineStartAt
                        breakPosition = state.character - 1;
                        // Log.e(TAG, "lineBreaker (" + lineBreaker + ") returns breakPosition before lineStarts");
                    }
                    forceBreak = false; // cancel force break
                    /* check if breakPosition < span.start (rollback to previous state) */
                    if (breakPosition < span.start) {
                        span.breakFirst = null;
                        // crash here if width too small
                        state = stack.remove(0);
                        span = state.span;
                        spanHeight = state.height;
                        forceBreak = true;
                        continue processing;
                    }

                    /* force close line code - test, if we can move it to procedure */
                    // local variables used :(

                    state.rollback(breakPosition);

                    if (hyphen) {
                        // check if text[breakPosition] + hyphen_width fit line
                        if (state.lineWidth + span.hyphenWidth > wrapWidth) {
                            if (debug) Log.v(TAG, "hyphen character exceed line width");
                            lineBreakVal = lineBreaker.nearestLineBreak(text, state.lastWhitespace, breakPosition - 1, span.end);
                            forceBreak = true;
                            continue processing;
                        }
                        state.lineWidth += span.hyphenWidth;
                    }

                    direction = handleBreakLine(text, breakPosition, hyphen, direction);

                } else if (text[state.character] == '\n') {
                    direction = handleCarrierReturn(direction);
                } else if (text[state.character] == 65532) { // 65532 - OBJECT REPLACEMENT CHAR
                    if (!handleImage(span, span.getDrawable(), true)) break recursion;

                } else if (!lineBreaker.isLetter(text[state.character])) {
                    // handle non-letter characters (m.b. only spaces?)
                    state.nonLetterBreak(text[state.character] != ' ' || span.strong);
                } else {
                    // handle usual character
                    if (span.widths == null) {
                        if (debug) Log.v(TAG, "span widths are null: " + span);
                    }
                    state.increaseBreak(span.widths[state.character - span.start]);
                    state.character++;
                }

            }
            // end handle span (
            if (!switchSpan())
                break recursion;
        }
        // handle tail of line
        state.character--; // FIXME: null while
        TextLine ld = new TextLine(state, lineStartAt, leadingMarginSpan);

        if (ld.end <= textEnd && (height < 1 || y + ld.height < height + 1) && (mMaxLines < 0 || result.size() < mMaxLines)) {
            result.add(ld);
        } else
            state.height = 0;
        mCallbacks.onFinish(result, (y + collectedHeight + state.height) > wrapEnd ? (y + collectedHeight + state.height) : wrapEnd);
    }

    private boolean switchSpan() {
        if (state.lineWidth == lineMargin) // cancel margin if only margin at new line
            state.lineWidth = 0f;
        // handle span switch
        if (state.skipWhitespaces) {
            state.processedWidth = state.span.width;
        } else {

        }
        stack.add(0, state.finish());
        if (span.next == null) {
            // Log.v(TAG, "break recursion at the end of chain");
            return false;
        }

        /**
         * TODO: release span.widths (if memory free required)
         *
         */
        if (span.next.breakFirst != null) {
            // Log.v(TAG, "clean underflow breakFirst");
            span.next.breakFirst = null;
        }

        state = new ReflowState(state, span.next);
        span = span.next;

        return true;
    }

}
