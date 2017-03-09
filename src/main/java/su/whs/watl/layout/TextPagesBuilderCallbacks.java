package su.whs.watl.layout;

import java.util.List;

import su.whs.watl.text.TextLine;

/**
 * Created by igor n. boulliev on 15.01.17.
 */

public interface TextPagesBuilderCallbacks {
    int getAvailableWidth();
    int getAvailableHeight();
    boolean onPageReady(List<TextLine> lines, int start, int end);
    void onHeightChanged(int height);
    void onLayoutFinished();
}
