package su.whs.watl.text;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;

/**
 * Created by igor n. boulliev on 03.02.15.
 */
public abstract class ImagePlacementHandler {
    private static final int THUMBNAIL = 0x01;
    private static final int EXCLUSIVE = 0x02;
    private static final int SCROLLABLE = 0x04;
    private static final int WRAP_TEXT = 0x08;
    private static final int ALIGN_CENTER = 0x10;
    private static final int ALIGN_START = 0x20;
    private static final int ALIGN_END = 0x40;
    public static final int DEFER = 0x00;
    public static final int PLACEHOLDER = 0x80;
    public static final int INLINE = 0xf000;
    /**
     * FIXME:
     * available height not used
     */

    public static class DefaultImagePlacementHandler extends ImagePlacementHandler {
        private static final String TAG = "ImagePlacementHandler";

        /**
         * default image placement handler
         * <p/>
         * behavior -
         *
         * @param drawableSpan
         * @param height       - available height (or -1 if no limit)
         * @param width        - available width (to end of line)
         * @param viewWidth    - layout width (total)
         * @param offset       - character position in text, given to TextLayout
         * @param scale        - accept result 'size' of drawable
         * @param paddings     - accept result 'paddings' for drawable
         * @return
         */

        @Override
        public int place(DynamicDrawableSpan drawableSpan, int /* unused - but may be -1 */ height, int width, int viewWidth, int offset, Point scale, Rect paddings, boolean allowDefer) {
            Drawable drawable = drawableSpan.getDrawable();
            if (drawable == null) {
                return 0;
            }
            Log.v(TAG, "place(" + drawableSpan + ", height=" + height + ", width=" + width + ", viewWidth=" + viewWidth + ", offset=" + offset);
            float dW = drawable.getIntrinsicWidth();
            float dH = drawable.getIntrinsicHeight();

            if (dW == 0 || dH == 0) {
                return PLACEHOLDER;
            }

            int paddingLeft = 5;
            int paddingRight = 5;
            int paddingTop = 5;
            int paddingBottom = 5;

            if (height < 0) { // height undefined
                // if image required < 1/2 viewWidth - than wrap, else - exclusive
                if (dW < viewWidth / 2) {
                    // wrap
                    scale.x = (int) dW;
                    scale.y = (int) dH;
                    paddings.set(paddingLeft, paddingTop, 0, paddingBottom);
                    Log.v(TAG, "(0) WRAP_TEXT | ALIGN_END " + scale.x + "," + scale.y);
                    return WRAP_TEXT | ALIGN_END;
                } else {
                    if ((dW + paddingLeft + paddingRight) > viewWidth) { // need scale to fit width
                        float drawableRatio = dH / dW;
                        scale.x = viewWidth;
                        scale.y = (int) (scale.x * drawableRatio);
                        paddings.set(0, 0, 0, 0);
                    } else {
                        scale.x = (int) dW;
                        scale.y = (int) dH;
                        paddings.set(0, 0, 0, 0);
                    }
                    Log.v(TAG, "(1) EXCLUSIVE | ALIGN_CENTER " + scale.x + "," + scale.y);
                    return EXCLUSIVE | ALIGN_CENTER;
                }
            } else { // height defined
                float viewRatio = height / viewWidth;
                float drawableRatio = dH / dW;
                if (viewRatio > drawableRatio) {
                    float ratio = viewWidth / dW;
                    scale.x = (int) (dW * (viewWidth / dW));
                    scale.y = (int) (dH * ratio);
                    return EXCLUSIVE | ALIGN_CENTER;
                } else {
                    float ratio = height / dH;
                    if (ratio<1) {
                        scale.x = (int) (dW * ratio);
                        scale.y = (int) (dH * ratio);
                        return EXCLUSIVE | ALIGN_CENTER;
                    }
                    scale.x = (int) dW;
                    scale.y = (int) dH;
                    return WRAP_TEXT | ALIGN_START;
                }
                /*
                if (dH > height * 5) {
                    if (scale.y * 5 < dH) {
                        Log.v(TAG, "(6) DEFER");
                        return DEFER;
                    }
                    scale.y = height;
                    scale.x = (int) (viewWidth / drawableRatio);
                }

                if (scale.x*1.5 > viewWidth) {
                    if (scale.x > viewWidth) {
                        scale.x = viewWidth;
                        scale.y = (int) (scale.x * drawableRatio);
                    }
                    if (scale.y > height) {
                        scale.y = height;
                        scale.x = (int) (viewWidth / drawableRatio);
                    }
                    Log.v(TAG, "(2) EXCLUSIVE | ALIGN_CENTER" + scale.x + "," + scale.y);
                    return EXCLUSIVE | ALIGN_CENTER;
                }
                Log.v(TAG, "(3) WRAP_TEXT | ALIGN_START " + scale.x + "," + scale.y);
                return WRAP_TEXT | ALIGN_START; */
            }
//
//            if (dW == 0)
//                return 0;
//            float ratio = dH / dW;
//
//            if (dW > width * .6) {
//                Log.v(TAG,"most width occupied");
//                if (dW > viewWidth) {
//                    scale.y = (int) ((viewWidth) * ratio);
//                    scale.x = viewWidth;
//                    paddings.set(0, 0, 0, 0);
//                    Log.v(TAG,"make drawable exclusive");
//                    return EXCLUSIVE;
//                } else if (width < dW * 0.3) {
//                    if (width > dW) {
//                        scale.x = (int) dW;
//                        scale.y = (int) dH;
//                    } else {
//                        scale.y = height; // (int) ((viewWidth) * ratio);
//                        scale.x = (int) (viewWidth / ratio);
//                    }
//                    return EXCLUSIVE;
//                }
//                scale.y = (int) (width / 2 * ratio);
//                scale.x = width / 2;
//            } else {
//                scale.x = (int) dW;
//                scale.y = (int) dH;
//            }
//
//            // if scale.x < width/3 - align right, else - aligh left
//            if (scale.x < viewWidth / 3) {
//                Log.v(TAG,"drawable width less than 1/3 of view width");
//                paddings.set(10, 5, 0, 0);
//                return WRAP_TEXT | ALIGN_END;
//            }
//
//            Log.v(TAG, "align start");
//            paddings.set(0, 5, 10, 0);
//            return WRAP_TEXT | ALIGN_START;
        }
    }

    private void fitViewPort(int dW, int dH, int vW, int wH, Point scale) {

    }

    public ImagePlacementHandler() {

    }

    /**
     * @param drawable  - DynamicDrawableSpan to place in layout
     * @param height    - available height (or -1 if no limit)
     * @param width     - available width (to end of line)
     * @param viewWidth - layout width
     * @param offset    - character position in text, given to TextLayout
     * @param scale     - accept result 'size' of drawable
     * @param paddings  - accept result 'paddings' for drawable
     * @return encoded rule
     */

    public abstract int place(DynamicDrawableSpan drawable, int height, int width, int viewWidth, int offset, Point scale, Rect paddings, boolean allowDefer);

    public static boolean isNewLineBefore(int value) {
        return (value & EXCLUSIVE) == EXCLUSIVE;
    }

    public static boolean isNewLineAfter(int value) {
        return (value & EXCLUSIVE) == EXCLUSIVE;
    }

    public static boolean isWrapText(int value) {
        return (value & EXCLUSIVE) != EXCLUSIVE && (value & WRAP_TEXT) == WRAP_TEXT;
    }

    public static Layout.Alignment getAlignment(int value) {
        if ((value & ALIGN_START) == ALIGN_START)
            return Layout.Alignment.ALIGN_NORMAL;
        else if ((value & ALIGN_END) == ALIGN_END)
            return Layout.Alignment.ALIGN_OPPOSITE;
        else if ((value & ALIGN_CENTER) == ALIGN_CENTER)
            return Layout.Alignment.ALIGN_CENTER;
        return Layout.Alignment.ALIGN_NORMAL;
    }

}
