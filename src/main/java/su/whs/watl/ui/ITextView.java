package su.whs.watl.ui;

import su.whs.watl.text.ContentView;

/**
 * Created by igor n. boulliev on 18.02.15.
 */

public interface ITextView {
    void setText(CharSequence text);
    void setTextSize(float size);
    void setTextSize(int unit, float size);
    ContentView.Options getOptions();
}
