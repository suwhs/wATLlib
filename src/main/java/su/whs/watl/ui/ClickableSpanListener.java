package su.whs.watl.ui;

import android.graphics.Rect;
import android.text.style.CharacterStyle;
import android.view.View;

/**
 * Created by igor n. boulliev on 01.04.15.
 */
public interface ClickableSpanListener {
    void onClick(View view, CharacterStyle span, int start, int end, Rect rect);
}
