package su.whs.watl.text.style;

import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.style.DynamicDrawableSpan;

import su.whs.watl.text.HtmlTagHandler;

/**
 * Created by igor n. boulliev on 13.11.15.
 */
public class VideoThumbnailSpan extends DynamicDrawableSpan {
    private Drawable mDrawable;
    private String mSource;

    public VideoThumbnailSpan(final String poster, final String source, int width, int height, final Html.ImageGetter imageGetter) {
        super();
        if (imageGetter instanceof HtmlTagHandler.ImageGetter) {
            mDrawable = ((HtmlTagHandler.ImageGetter)imageGetter).getDrawable(poster,source,width,height);
        } else {
            throw new IllegalStateException("requred HtmlTagHandler.ImageGetter instance as ImageGetter");
        }
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }

    public String getVideoSource() {
        return mSource;
    }
}
