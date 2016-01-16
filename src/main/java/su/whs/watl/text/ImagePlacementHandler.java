package su.whs.watl.text;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.style.DynamicDrawableSpan;

import su.whs.watl.experimental.AutoPlacedDrawable;
import su.whs.wlazydrawable.LazyDrawable;

/**
 * Created by igor n. boulliev on 03.02.15.
 */
public abstract class ImagePlacementHandler {
    public static final int THUMBNAIL = 0x01;
    public static final int EXCLUSIVE = 0x02;
    public static final int SCROLLABLE = 0x04;
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

        public enum WRAP {
            AUTO,
            LEFT,
            RIGHT
        }

        // private float mWrapRatioTreshold = 1.0f;
        private WRAP mForceWrap = WRAP.AUTO;
        // private float mWrapIfWidthLeftLessThan = 0.6f;
        // private float mMinimumScaleFactor = 0.5f;

        /**
         * default image placement handler
         * <p/>
         * behavior -
         *
         * @param drawableSpan
         * @param height       - available height (or -1 if no limit)
         * @param viewHeight   - layout height (total, or -1 if not defined)
         * @param width        - available width (to end of line)
         * @param viewWidth    - layout width (total)
         * @param offset       - character position in text, given to TextLayout
         * @param scale        - accept result 'size' of drawable
         * @param options      - TextLayout's ContentView.Options
         * @return
         */

        @Override
        public int place(DynamicDrawableSpan drawableSpan, int height, int viewHeight, int width, int viewWidth, int offset, Point scale, ContentView.Options options, boolean allowDefer) {
            /* default behavior */
            // if instrictWidth < width && instrictHeight < height - leave as inline
            Drawable dr = drawableSpan.getDrawable();
            if (dr instanceof AutoPlacedDrawable) {
                int r = ((AutoPlacedDrawable)dr).place(viewWidth,viewHeight,width,height,scale,options);
                if (r>-1) return r;
                // else - calculate itself
            }

            Rect paddings = new Rect();
            options.getDrawablePaddings(paddings);
            if (dr == null)
                return PLACEHOLDER;
            int iW = dr.getIntrinsicWidth();
            int iH = dr.getIntrinsicHeight();
            int sW = paddings.left + paddings.top;
            int sH = paddings.top + paddings.bottom;

            if ((iW < 0 || iH < 0) && dr instanceof LazyDrawable) {
                return PLACEHOLDER;
            }

            float ratio = iH / (float) iW;

            scale.x = iW;
            scale.y = iH;

            width -= sW;
            height -= sH;
            viewWidth -= sW;
            viewHeight -= sH;

            int targetWidth = viewWidth;
            int targetHeight = (viewHeight < 0 ? (height < 0 ? scale.y : height) : viewHeight);

            boolean fitWidth = false;
            boolean fitHeight = false;

            float sWm = scale.x * options.getDrawableMinimumScaleFactor();
            float sHm = scale.y * options.getDrawableMinimumScaleFactor();

            if (options.getDrawableMinimumScaleFactor() < 1.0f) { // if scaling down allowed
                if (sWm <= width) {
                    targetWidth = width < scale.x ? width : scale.x;
                    fitWidth = true;
                } else if (sWm <= viewWidth) {
                    targetWidth = viewWidth < scale.x ? viewWidth : scale.x;
                }
                if (sHm <= height || (height < 0 && viewHeight < 0)) {
                    targetHeight = height < scale.y && height > -1 ? height : scale.y;
                    fitHeight = true;
                } else if (sHm <= viewHeight) {
                    targetHeight = viewHeight < scale.y && viewHeight > 0 ? viewHeight : scale.y;
                }

                if (!fitWidth && !fitHeight) {
                    boolean scaledFitWidth = false;
                    boolean scaledFitHeight = false;
                    int scaledTargetWidth = viewWidth;
                    int scaledTargetHeight = viewHeight;

                }
            } else {
                if (sWm <= width) {
                    targetWidth = width < scale.x ? width : scale.x;
                    fitWidth = true;
                } else if (sWm <= viewWidth) {
                    targetWidth = viewWidth < scale.x ? viewWidth : scale.x;
                }
                if (sHm <= height || (height < 0 && viewHeight < 0)) {
                    targetHeight = height < scale.y && height > -1 ? height : scale.y;
                    fitHeight = true;
                } else if (sHm <= viewHeight) {
                    targetHeight = viewHeight < scale.y && viewHeight > 0 ? viewHeight : scale.y;
                }
            }

            if (viewHeight<1)
                fitHeight = true;

            float rH = targetHeight / (float) scale.y;
            float rW = targetWidth / (float) scale.x;
            float scaleFactor = rW > rH ? rH : rW;

            /*
            if (rW>rH) {
                // scale to fit targetHeight
               scaleFactor = rH;
            } else {
                // scale to fit targetWidth
            } */
            scale.x *= scaleFactor;
            scale.y *= scaleFactor;
            if (fitWidth) {
                if (fitHeight) {
                    float widthRatio = scale.x / (float)viewWidth;
                    float wrapWidthTreshold = options.getDrawableWrapWidthTreshold();
                    if (ratio >= options.getDrawableWrapRatioTreshold() && widthRatio < wrapWidthTreshold) {
                        return WRAP_TEXT;
                    }
                    return EXCLUSIVE;
                }
                return EXCLUSIVE;
            } else {
                if (fitHeight) {
                    float widthLeftRatio = viewWidth / width;
                    if (widthLeftRatio < options.getDrawableWrapWidthTreshold()) {

                    }
                }
            }
            return EXCLUSIVE;
        }
    }

    public ImagePlacementHandler() {

    }

    /**
     * @param drawable  - DynamicDrawableSpan to place in layout
     * @param height    - available height (or -1 if no limit)
     * @param viewHeight - view height (if > -1 )
     * @param width     - available width (to end of line)
     * @param viewWidth - view width
     * @param offset    - character position in text, given to TextLayout
     * @param scale     - accept result 'size' of drawable
     * @param options   - ContentView.Options
     * @return encoded rule
     */

    public abstract int place(DynamicDrawableSpan drawable, int height, int viewHeight, int width, int viewWidth, int offset, Point scale, ContentView.Options options, boolean allowDefer);

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
