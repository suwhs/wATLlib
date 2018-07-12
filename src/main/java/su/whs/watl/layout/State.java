package su.whs.watl.layout;

import android.text.Layout;

/**
 * Created by igor n. boulliev <igor@whs.su> on 18.02.17.
 */

public class State {

    Span span;
    int position;
    float leftWidth;
    int direction = Layout.DIR_LEFT_TO_RIGHT;
    public int lastWhitespace;
    public int whitespacesCount;
    public float currentWhitespaceWidth = 8f;

    public State(Span span, float leftWidth) {
        this.span = span;
        this.leftWidth = leftWidth;
    }

    public void beginsFrom(int position) {
        if (position>this.span.mStart) {
            throw new IllegalStateException("beginsFrom already called");
        }
        this.position = position;
    }

}
