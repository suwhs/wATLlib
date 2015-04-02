package su.whs.watl.text;

/**
 * Created by igor n. boulliev on 19.02.15.
 */
public interface TextInfoInvalidateListenerExt extends TextInfoInvalidateListener {
    /**
     * called when viewHeight exceed
     *
     * @return - if true, reflow() process will be continued
     */
    boolean onHeightExceed(int collectedHeight);
}
