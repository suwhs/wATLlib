package su.whs.watl.text;

/**
 * interface for interaction between TextLayout and View
 * Created by igor n. boulliev on 13.02.15.
 */

/**
 * TextInfoInvalidateListener
 * - used for notify TextLayout's holder about layout geometry changes and content updates
 */

public interface TextLayoutListener {
    /**
     * called, when layout's content changed
     */
    void onTextInfoInvalidated();

    /**
     * called, when layout's height changed
     * (this is not called, if layout created with height > -1)
     */
    void onTextHeightChanged();

    /**
     * called when reflow finished
     */
    void onTextReady();

    /**
     * called when viewHeight exceed
     *
     * @return - if true, reflow() process will be continued
     */

    boolean onHeightExceed(int collectedHeight);

    /**
     * allow TextLayout to call repaint on anitmation events
     */
    void invalidate(int left, int top, int right, int bottom);
}
