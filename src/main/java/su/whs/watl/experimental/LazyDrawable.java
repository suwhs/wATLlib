/*
 * Copyright 2015 whs.su
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package su.whs.watl.experimental;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by igor n. boulliev on 29.08.15.
 */

/**
 * abstract class for support two display mode - 'preview' and 'full'
 * for example, it may display local cached jpeg as preview, and load high quality png on
 * request
 *
 */

public abstract class LazyDrawable extends Drawable implements Animatable, Drawable.Callback {
    private static ThreadPoolExecutor executor = new ThreadPoolExecutor(1,1,1000L, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(500));
    // TODO: renderState - focused/unfocused + temporaryCache for 'previews' - for instances, that in 'focused' state
    public synchronized Bitmap getBitmap() {
        if (mDrawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)mDrawable).getBitmap();
        }
        return null;
    }

    public synchronized void Unload() {
        mState = State.NONE;
        setDrawable(null);
    }

    protected enum State {
        NONE,
        QUEUED,
        LOADING,
        PREVIEW,
        FULL,
        ANIMATION,
        ERROR,
        PARAM_ERROR,
    }

    public enum ScaleType {
        FILL,
        SCALE_FIT,
        CENTER_CROP
    }

    private static synchronized ThreadPoolExecutor getExecutor() {
        if (executor.isShutdown()) {
            executor = new ThreadPoolExecutor(1,1,10L, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(500));
        }
        return executor;
    }

    private static final String TAG="LazyDrawable";
    private int mSrcWidth;
    private int mSrcHeight;
    private Drawable mDrawable;
    private Rect mBounds = new Rect();
    private Drawable.Callback mCallbackCompat = null;
    private Drawable mPlayButtonDrawable = null;
    private Drawable mIndeterminateProgressDrawable = null;
    private Drawable mLoadingErrorDrawable = null;
    private Drawable mQueuedDrawable = null;
    private State mState = State.NONE;
    private ScaleType mScaleType = ScaleType.SCALE_FIT;
    private boolean mAutoStart = false;
    private int mEdgeColor = Color.WHITE;

    public interface Callback extends Drawable.Callback {
        void onLayoutRequest();
    }

    /**
     * create instance with given size
     * @param srcWidth
     * @param srcHeight
     */

    public LazyDrawable(int srcWidth, int srcHeight) {
        this(srcWidth,srcHeight,ScaleType.FILL);
    }

    public LazyDrawable(int srcWidth, int srcHeight, ScaleType scaleType) {
        mSrcWidth = srcWidth;
        mSrcHeight = srcHeight;
        mScaleType = scaleType;
        setBounds(0, 0, srcWidth, srcHeight);
    }

    /**
     * set 'play button' drawable
     * @param drawable - will draw over wrapped drawable, if animation not running
     */

    public void setPlayButtonDrawable(Drawable drawable) {
        mPlayButtonDrawable = drawable;
        mPlayButtonDrawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    public void setIndeterminateProgressDrawable(Drawable drawable) {
        mIndeterminateProgressDrawable = drawable;
    }

    public void setLoadingErrorDrawable(Drawable drawable) {
        mLoadingErrorDrawable = drawable;
    }

    public void setQueuedDrawable(Drawable drawable) {
        mQueuedDrawable = drawable;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSrcWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSrcHeight;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        mBounds.set(left,top,right,bottom);
        if (mDrawable!=null) {
            applyBounds(mDrawable);
        }
        super.setBounds(left,top,right,bottom);
    }

    private void applyBounds(Drawable drawable) {
        if (mBounds.width()==0 || mBounds.height()==0) {
            Log.e(TAG, "bounds not set:"+this);
            mState = State.PARAM_ERROR;
            return;
        }
        switch (mScaleType) {
            case FILL:
                drawable.setBounds(mBounds);
                break;
            case CENTER_CROP:
                int w = drawable.getIntrinsicWidth();
                int h = drawable.getIntrinsicHeight();
                int dW = mBounds.width() - w;
                int dH = mBounds.height() - h;
                int sX = dW / 2;
                int sY = dH / 2;
                drawable.setBounds(mBounds.left+sX,mBounds.top+sY,mBounds.right-sX, mBounds.bottom-sY);
                break;
            case SCALE_FIT:
                w = drawable.getIntrinsicWidth();
                h = drawable.getIntrinsicHeight();
                if (w<1||h<1) {
                    Log.e(TAG,"drawable with invalid intrinsic size");
                    mState = State.PARAM_ERROR;
                    return;
                }
                float sH = (float)mBounds.height() / h;
                float sW = (float)mBounds.width() / w;
                float ratio = sW > sH ? sH : sW;
                dW = (int) (w * ratio);
                dH = (int) (h * ratio);
                sX = (mBounds.width()-dW) / 2;
                sY = (mBounds.height()-dH) / 2;
                drawable.setBounds(mBounds.left+sX,mBounds.top+sY,mBounds.right-sX, mBounds.bottom-sY);
                break;
        }
    }
    /**
     * set size and adjust bounds
     * @param srcWidth
     * @param srcHeight
     */

    protected void setSize(int srcWidth, int srcHeight) {
        mSrcWidth = srcWidth;
        mSrcHeight = srcHeight;
        mBounds.right = mBounds.left + srcWidth;
        mBounds.bottom = mBounds.top + srcHeight;
        if (mDrawable!=null) {
            applyBounds(mDrawable);
        }
    }

    /**
     * load preview (sync)
     */
    public void initialLoad() {
        Drawable d = null;
        synchronized (this) {
            mState = State.LOADING;
        }
        try {
            d = readPreviewDrawable();
        } catch (OutOfMemoryError e) {
            Log.e(TAG,"out of memory while readPreviewDrawable()");
        } catch (InterruptedException e) {

        }
        if (d==null) {
            mState = State.ERROR;
            return;
        }
        synchronized (LazyDrawable.this) {
            setDrawable(d);
            mState = State.PREVIEW;
        }
        invalidateSelfOnUiThread();
    }

    private Runnable mInitialLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            initialLoad();
        }
    };

    private Runnable mFullImageLoadRunnable = new Runnable() {
        @Override
        public void run() {
            Drawable drawable = null;
            try {
                drawable = readFullDrawable();
            } catch (InterruptedException e) {

            }
            mFuture = null;
            synchronized (LazyDrawable.this) {
                if (drawable == null) {
                    if (mDrawable!=null) // but here are preview drawable
                        return;
                    mState = State.ERROR;
                    return;
                }
                setDrawable(drawable);
                mState = State.FULL;
                if (drawable instanceof Animatable && mAutoStart && !isRunning()) {
                    if (drawable instanceof Animatable && !((Animatable)drawable).isRunning()) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                start();
                            }
                        });
                    }
                }
            }
            invalidateSelfOnUiThread();
        }
    };

    private Rect mBoundsRect = new Rect();
    private Paint mBoundsPaint = new Paint();
    private Future mFuture = null;
    /**
     * if no preview loaded - execute initialLoad() in background and draw 'loadingFrame', if defined
     * @param canvas
     */

    @Override
    public void draw(Canvas canvas) {
        State state = State.LOADING;
        Drawable current = mDrawable;
        synchronized (this) {
            state = mState;
        }
        switch (state) {
            case NONE:
                synchronized (this) { mState=State.LOADING; }
                // drawNextLoadingFrame(canvas);
                try {
                    mState = State.QUEUED;
                    mFuture = getExecutor().submit(mInitialLoadingRunnable);

                } catch (RejectedExecutionException e) {
                    mState = State.ERROR;
                    invalidateSelfOnUiThread();
                }
            case QUEUED:
                if (mQueuedDrawable!=null)
                    drawProgress(canvas,mQueuedDrawable,0);
                break;
            case LOADING:
                if (getCallbackCompat()!=null) {
                    scheduleSelf(new Runnable() {
                        @Override
                        public void run() {
                            invalidateSelf();
                        }
                    }, SystemClock.uptimeMillis()+60);
                }
                if (current!=null)
                    drawDrawable(canvas,current);
                drawNextLoadingFrame(canvas);
                break;
            case ERROR:
                drawLoadError(canvas);
            case PARAM_ERROR:
                break;
            case PREVIEW:
            case FULL:
            case ANIMATION:
                drawDrawable(canvas, current);
                break;
        }

    }

    private void drawDrawable(Canvas canvas, Drawable drawable) {
        int state = canvas.save();
        canvas.clipRect(mBounds);
        if (drawable!=null) {
            drawable.draw(canvas);
            if (mScaleType == ScaleType.CENTER_CROP) {
                if (drawable.getBounds().height() > mBounds.height()) {
                    drawVerticalEdges(canvas);
                } else if (drawable.getBounds().width() > mBounds.width()) {
                    drawHorizontalEdges(canvas);
                }
            }
        }
        canvas.restoreToCount(state);
        /*
        mBoundsRect.set(mBounds.left + 5, mBounds.top + 5, mBounds.right - 5, mBounds.bottom - 5);
        mBoundsPaint.setStyle(Paint.Style.STROKE);
        mBoundsPaint.setColor(Color.RED);
        canvas.drawRect(mBoundsRect, mBoundsPaint);
        if (drawable!=null) {
            mBoundsRect.set(drawable.getBounds());
            mBoundsPaint.setColor(Color.GREEN);
            canvas.drawRect(mBoundsRect, mBoundsPaint);
            canvas.drawLine(0f, 0f, mBoundsRect.left, mBoundsRect.top, mBoundsPaint);
            canvas.drawLine(mBounds.right, mBounds.bottom, mBoundsRect.left, mBoundsRect.top, mBoundsPaint);
        } */
    }

    private int mVerticalEdgeSize = 24;
    private int mHorizontalEdgeSize = 24;
    private Paint mEdgePaint = new Paint();
    {
        mEdgePaint.setStyle(Paint.Style.FILL);
    }
    private void drawVerticalEdges(Canvas canvas) {
        Shader shader;
        shader = new LinearGradient(0, 0, 0, mVerticalEdgeSize, mEdgeColor, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        mEdgePaint.setShader(shader);
        canvas.drawRect(0, 0, mBounds.right, 0 + mVerticalEdgeSize, mEdgePaint);

        shader = new LinearGradient(0, mBounds.bottom-mVerticalEdgeSize, 0, mBounds.bottom, Color.TRANSPARENT, mEdgeColor, Shader.TileMode.CLAMP);
        mEdgePaint.setShader(shader);
        canvas.drawRect(0,mBounds.bottom-mVerticalEdgeSize,mBounds.right,mBounds.bottom,mEdgePaint);
    }

    private void drawHorizontalEdges(Canvas canvas) {
        Shader shader;
        shader = new LinearGradient(0, 0, mHorizontalEdgeSize, 0, mEdgeColor, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        mEdgePaint.setShader(shader);
        canvas.drawRect(0, 0, mHorizontalEdgeSize, mBounds.bottom, mEdgePaint);

        shader = new LinearGradient(mBounds.right-mHorizontalEdgeSize, 0, mBounds.right, 0, Color.TRANSPARENT, mEdgeColor, Shader.TileMode.CLAMP);
        mEdgePaint.setShader(shader);
        canvas.drawRect(mBounds.right - mHorizontalEdgeSize, 0, mBounds.right, mBounds.bottom, mEdgePaint);
    }

    protected void drawLoadError(Canvas canvas) {
        drawProgress(canvas, mLoadingErrorDrawable, 0);
    }

    /**
     * by default - do nothing. Override it to draw loading animation
     * @param canvas
     */
    private int angle = 0;

    protected void drawNextLoadingFrame(Canvas canvas) {
        Drawable progress = mIndeterminateProgressDrawable;
        if (progress==null)
            progress = mPlayButtonDrawable;
        if (progress!=null) {
            angle += 10;
            if (angle>360) angle = 0;
            drawProgress(canvas,progress,angle);
        }
    }

    protected void drawPlayButton(Canvas canvas) {
        drawProgress(canvas, mPlayButtonDrawable, 0, 100);
    }

    private void drawProgress(Canvas canvas, Drawable progress, int angle) {
        drawProgress(canvas, progress, angle, 255);
    }

    protected void drawProgress(Canvas canvas, Drawable progress, int angle, int alpha) {
        if (progress==null) return;
        int x = mBounds.width() /2 + mBounds.left;
        int y = mBounds.height() / 2 + mBounds.top;
        int state = canvas.save();
        canvas.translate(x,y);
        if (angle>0) {
            canvas.rotate(angle);
        }
        x = -progress.getIntrinsicWidth() / 2;
        y = -progress.getIntrinsicHeight() / 2;

        canvas.translate(x,y);

        progress.setAlpha(alpha);
        progress.draw(canvas);
        canvas.restoreToCount(state);

        // mBoundsRect.set(progress.getBounds() /* mBounds.left + 5, mBounds.top + 5, mBounds.right - 5, mBounds.bottom - 5 */);
        /* mBoundsPaint.setStyle(Paint.Style.STROKE);
        mBoundsPaint.setColor(Color.RED);
        canvas.drawRect(mBoundsRect, mBoundsPaint);
        if (progress!=null) {
            mBoundsRect.set(mBounds);
            mBoundsPaint.setColor(Color.GREEN);
            canvas.drawRect(mBoundsRect, mBoundsPaint);
            canvas.drawLine(0f, 0f, mBoundsRect.left, mBoundsRect.top, mBoundsPaint);
            canvas.drawLine(mBounds.right, mBounds.bottom, mBoundsRect.left, mBoundsRect.top, mBoundsPaint);
        } */
    }

    /**
     * initiate load full image (sync)
     */

    public void loadFullImage() {
        if (mFuture!=null)
            mFuture.cancel(true);
        mFuture = getExecutor().submit(mFullImageLoadRunnable);
    }

    private int mAlpha = 255;
    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
    }

    private ColorFilter mColorFilter;
    @Override
    public void setColorFilter(ColorFilter cf) {
        mColorFilter = cf;
    }

    private int mOpacity = 0;

    @Override
    public int getOpacity() {
        synchronized (this) {
            if (mDrawable!=null)
                return mDrawable.getOpacity();
            else
                return mOpacity;
        }
    }

    @Override
    public void start() {
        synchronized (this) {
            if (mDrawable instanceof Animatable) {
                ((Animatable)mDrawable).start();
                mState = State.ANIMATION;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (this) {
            if (mDrawable instanceof Animatable) {
                ((Animatable)mDrawable).stop();
            }
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (this) {
            if (mDrawable instanceof Animatable) {
                return ((Animatable)mDrawable).isRunning();
            }
        }
        return false;
    }

    /**
     * replace wrapped drawable (and reset internal flags)
     * @param drawable
     */
    protected synchronized void setDrawable(Drawable drawable) {
        if (mDrawable!=null && isRunning()) {
            mDrawable.setCallback(null); // remove callbacks from drawable
            stop();
        }
        mDrawable = drawable;
        if (mDrawable!=null) {
            applyBounds(drawable);
            mDrawable.setCallback(this);
        }
    }

    /**
     * set unrecoverable load error flag
     */
    protected void onFailure() {
        mState = State.ERROR;
        invalidateSelfOnUiThread();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (getCallbackCompat()!=null)
            getCallbackCompat().invalidateDrawable(this);
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (getCallbackCompat()!=null)
            getCallbackCompat().scheduleDrawable(this, what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (getCallbackCompat()!=null)
            getCallbackCompat().unscheduleDrawable(this, what);
    }

    private Drawable.Callback getCallbackCompat() {
        if (Build.VERSION.SDK_INT>10) return getCallback();
        return mCallbackCompat;
    }

    public void setCallbackCompat(Drawable.Callback cb) {
        if (Build.VERSION.SDK_INT>10) {
            setCallback(cb);
            return;
        }
        mCallbackCompat = cb;
    }

    /**
     * run invalidateSelf() on MainLooper
     */
    protected void invalidateSelfOnUiThread() {
        if (Looper.getMainLooper().getThread().equals(Thread.currentThread())) {
            invalidateSelf();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    LazyDrawable.this.invalidateSelf();
                }
            });
        }
    }

    /**
     * abstract method. Implementation must return Drawable fast as possible
     * @return
     */
    protected abstract Drawable readPreviewDrawable() throws InterruptedException;

    /**
     * abstract method. Implementation may load real image from network (not fast),
     * store to cache (and use it when this drawable required again)
     * @return
     */
    protected abstract Drawable readFullDrawable() throws InterruptedException;


    /**
     * sample implementation - only 'slow method'
     * generally - for development and debug purposes
     */
}
