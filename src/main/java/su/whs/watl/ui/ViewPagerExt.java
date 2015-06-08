package su.whs.watl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Created by igor n. boulliev on 29.05.15.
 */
public class ViewPagerExt extends ViewPager {

    /**
     * setWillNotDraw(false) called by ViewPager
     */

    public ViewPagerExt(Context context) {
        super(context);
    }

    public ViewPagerExt(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTransformer!=null && mTransformer.onTouchEvent(event)) return true;
        return super.onTouchEvent(event);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mTransformer!=null && mTransformer.dispatchDraw(canvas)) return;
        super.onDraw(canvas);
    }

    private IPageTransformerExt mTransformer = new CurlEdgePageTransformer();

    {
        mTransformer.attach(ViewPagerExt.this);
    }


}
