package su.whs.watl.experimental;

import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

import su.whs.wlazydrawable.PreviewDrawable;
import su.whs.watl.text.ContentView;
import su.whs.watl.text.ImagePlacementHandler;

/**
 * Created by igor n. boulliev on 04.09.15.
 */
public class TableDrawable extends PreviewDrawable implements AutoPlacedDrawable {

    /**
     * create instance with given size
     *
     * @param srcWidth
     * @param srcHeight
     */

    private List<List<Layout>> layouts = new ArrayList<List<Layout>>();
    private TextPaint mPaint = new TextPaint();
    public TableDrawable(Context context, int srcWidth, int srcHeight) {
        super(context, srcWidth, srcHeight);
    }

    public TableDrawable(Context context, List<List<CharSequence>> content) {
        super(context,0,0);
    }

    @Override
    public int place(int width, int height, int leftWidth, int leftHeight, Point scale, ContentView.Options options) {
        // store available width and available height

        return ImagePlacementHandler.PLACEHOLDER;
    }

    @Override
    protected Drawable getPreviewDrawable() {
        return null;
    }

    @Override
    protected Drawable getFullDrawable() {
        return null;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {

    }

    @Override
    protected void onLoadingError() {

    }

    @Override
    protected int getSampling() {
        return 1;
    }
}
