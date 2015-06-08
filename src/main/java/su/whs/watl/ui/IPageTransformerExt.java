package su.whs.watl.ui;

import android.graphics.Canvas;
import android.view.MotionEvent;

/**
 * Created by igor n. boulliev on 30.05.15.
 */

public interface IPageTransformerExt {
    void attach(ViewPagerExt viewPager);
    boolean dispatchDraw(Canvas canvas);
    boolean onTouchEvent(MotionEvent event);
}
