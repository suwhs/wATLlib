package su.whs.watl.ui;

import su.whs.watl.text.ContentView;

/**
 * Created by igor n. boulliev on 18.02.15.
 */

public interface ITextView {
    public void setText(CharSequence text);
    public void setTextSize(float size);
    public void setTextSize(int unit, float size);
    public ContentView.Options getOptions();
}
