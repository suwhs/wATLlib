package su.whs.watl.text;

import android.graphics.RectF;
import android.text.style.DynamicDrawableSpan;
import android.view.View;

/**
 * Created by igor n. boulliev on 05.02.15.
 */
public interface DynamicDrawableInteractionListener {
    void onClicked(DynamicDrawableSpan span, RectF bounds, View view);
}
