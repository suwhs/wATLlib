package su.whs.watl.text;

import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;

import java.util.List;

/**
 * Created by igor n. boulliev on 11.01.16.
 */
public interface ITextLayout {
    ContentView.Options getOptions();
    boolean updateGeometry(int[] geometry);
    boolean onProgress(List<TextLine> lines, int height, boolean viewHeightExeed);
    Drawable.Callback getPlaceholderCallbacks();
    Drawable.Callback getDrawableCallbacks();
    void onFinish(List<TextLine> lines, int height);
    void registerDrawable(DynamicDrawableSpan dds, int placement, int position);
    void setDrawableBounds(Drawable dr, int left, int top, int right, int bottom);
    void setMaxLines(int maxLines);
}
