package su.whs.watl.experimental;

import android.graphics.Point;

import su.whs.watl.text.ContentView;

/**
 * Created by igor n. boulliev on 04.09.15.
 */
public interface AutoPlacedDrawable {
    int place(int width, int height, int leftWidth, int leftHeight, Point scale, ContentView.Options options);
}
