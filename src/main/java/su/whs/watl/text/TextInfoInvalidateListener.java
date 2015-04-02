package su.whs.watl.text;

/**
 * Created by igor n. boulliev on 13.02.15.
 */

/**
 * TextInfoInvalidateListener
 * - used for notify TextLayout's holder about layout geometry changes and content updates
 */

public interface TextInfoInvalidateListener {
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
}
