package su.whs.watl.experimental;

import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

import su.whs.watl.text.ImagePlacementHandler;

/**
 * Created by igor n. boulliev on 04.09.15.
 */
public class TableDrawable extends LazyDrawable implements AutoPlacedDrawable {

    /**
     * create instance with given size
     *
     * @param srcWidth
     * @param srcHeight
     */

    private List<List<Layout>> layouts = new ArrayList<List<Layout>>();
    private TextPaint mPaint = new TextPaint();
    public TableDrawable(int srcWidth, int srcHeight) {
        super(srcWidth, srcHeight);
    }

    public TableDrawable(List<List<CharSequence>> content) {
        super(0,0);
    }

    @Override
    protected Drawable readPreviewDrawable() {
        // return placeholder drawable
        return null;
    }

    @Override
    protected Drawable readFullDrawable() {
        // return full drawable
        return null;
    }

    @Override
    public int place(int width, int height) {
        // store available width and available height

        return ImagePlacementHandler.PLACEHOLDER;
    }
}
