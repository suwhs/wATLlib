package su.whs.watl.layout;

/**
 * Created by igor n. boulliev on 15.01.17.
 */

public interface TextLinesBuilderCallbacks {
    int getAvailableWidth();
    int getAvailableHeight();
    void onLineHeightChanged(int height);
    boolean onLineReady(Line textLine);
    boolean isWhitespacesCompressEnabled();
}
