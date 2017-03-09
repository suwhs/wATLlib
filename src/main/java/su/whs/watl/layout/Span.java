package su.whs.watl.layout;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;

import java.io.DataInputStream;

/**
 * Created by igor n. boulliev <igor@whs.su> on 18.02.17.
 */

public class Span {
    Style mStyle;
    int mStart;
    int mEnd;
    int mDirection = Layout.DIR_LEFT_TO_RIGHT;
    Measure mMeasure;

    public Span(Style style, int start, int end, char[] text, TextPaint paint) {
        this.mStyle = style;
        this.mStart = start;
        this.mEnd = end;
        if (paint!=null && text!=null) {
            this.mMeasure = new Measure(text,start,end,paint);
        }
    }

    public Span(Style style, int start, int end) {
        this(style,start,end, null, null);
    }

    public Span(DataInputStream inputStream, StyleDictionary dictionary) {

    }

    public void measure(char[] text, TextPaint paint) {
        mMeasure = new Measure(text,mStart,mEnd,paint);
    }

    void measure() {
        mMeasure = new Measure();
    }

    public float draw(Canvas canvas, char[] text, float x, float y, Break[] breaks, int justifyAjustPoints, float justifyArgument, TextPaint paint) {
        int breakIndex = 0;
        if (breaks!=null) {
            // draw span [break by break]
            for(;breakIndex<breaks.length;breakIndex++) {
                canvas.drawText(
                        text,
                        breaks[breakIndex].mStart,
                        breaks[breakIndex].mLength,
                        x,
                        y,
                        paint
                );
                x += breaks[breakIndex].mWidth;
                if (justifyAjustPoints>0 && breaks[breakIndex].mJustification) {
                    x += justifyArgument;
                }
            }
            return x;
        } else {
            // draw whole span 'as is' (justifyAjustPoints are ignored, justifyArgument also ignored)
            canvas.drawText(
                    text,
                    mStart,
                    mEnd,
                    x,
                    y,
                    paint
            );
            return mMeasure.mWidth;
        }
    }

    public void draw(Canvas canvas, char[] text, float x, float y, float sparseArgument, TextPaint paint) {
        for (int i=0; i<(mStart-mEnd); i++) {
            canvas.drawText(text,mStart+i,1,x,y,paint);
            x += mMeasure.mWidths[i];
        }
    }

    public class Measure {
        float[] mWidths;
        float mHeight;
        float mWidth;

        public Measure(char[] text, int start, int end, TextPaint paint) {
            mWidths = new float[end-start];
            if (mStyle!=null)
                mStyle.apply(paint);
            paint.getTextWidths(text,start,end,mWidths);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            mHeight = metrics.bottom-metrics.top;
            mWidth = 0;
            for(float width : mWidths) mWidth+=width;
        }

        public Measure() {}
    }
}
