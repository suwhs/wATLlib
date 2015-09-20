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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
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
    private static ThreadPoolExecutor executor = new ThreadPoolExecutor(1,3,10, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());
    private static final String TAG="LazyDrawable";
    private int mSrcWidth;
    private int mSrcHeight;
    private Drawable mDrawable;
    private Rect mBounds = new Rect();
    private boolean mBoundsApplied = false;
    private boolean mLoadingInProgress = false;
    protected boolean mLoadingError = false;
    private Drawable.Callback mCallbackCompat = null;
    private Drawable mPlayButtonDrawable = null;

    /**
     * create instance with given size
     * @param srcWidth
     * @param srcHeight
     */
    public LazyDrawable(int srcWidth, int srcHeight) {
        mSrcWidth = srcWidth;
        mSrcHeight = srcHeight;
        mBounds.set(0, 0, srcWidth, srcHeight);
    }

    public void setPlayButtonDrawable(Drawable drawable) {
        mPlayButtonDrawable = drawable;
        mPlayButtonDrawable.setBounds(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());
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
        mBoundsApplied = false;
        mBounds.set(left,top,right,bottom);
        super.setBounds(left,top,right,bottom);
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
        mBoundsApplied = false;
    }

    /**
     * load preview (sync)
     */
    public void initialLoad() {
        Drawable d = readPreviewDrawable();
        synchronized (LazyDrawable.this) {
            mDrawable = d;
            mLoadingInProgress = false;
        }
    }

    /**
     * if no preview loaded - execute initialLoad() in background and draw 'loadingFrame', if defined
     * @param canvas
     */

    @Override
    public void draw(Canvas canvas) {
        if (mDrawable==null) {
            synchronized (this) {
                mLoadingInProgress = true;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        initialLoad();
                        invalidateSelfOnUiThread();
                    }
                });
            }
        }
        if (!mBoundsApplied) {
            synchronized (this) {
                mDrawable.setBounds(mBounds);
                mBoundsApplied = true;
            }
        }

        boolean loadError;
        boolean loadingInProgress;

        synchronized (this) {
            loadingInProgress = mLoadingInProgress;
            if (mDrawable!=null) {
                mDrawable.draw(canvas);
                if (mPlayButtonDrawable!=null && !isRunning() && !loadingInProgress)
                    drawPlayButton(canvas);
            }
            loadError = mLoadingError;

        }
        if (loadingInProgress) {
                /* draw loading animation */
            drawNextLoadingFrame(canvas);
                /* */
            if (getCallbackCompat()!=null) {
                scheduleSelf(new Runnable() {
                    @Override
                    public void run() {
                        invalidateSelf();
                    }
                }, SystemClock.uptimeMillis()+60);
            }
        } else if (loadError) {
                /* draw loading error sign */
        }
    }

    /**
     * by default - do nothing. Override it to draw loading animation
     * @param canvas
     */
    private int angle = 0;
    protected void drawNextLoadingFrame(Canvas canvas) {
        if (mPlayButtonDrawable!=null) {
            angle += 10;
            if (angle>360) angle = 0;
            drawPlayButton(canvas,angle);
        }
    }

    protected void drawPlayButton(Canvas canvas) {
        drawPlayButton(canvas, 0);
    }

    protected void drawPlayButton(Canvas canvas, int angle) {
        int x = mBounds.width() /2 + mBounds.left;
        int y = mBounds.height() / 2 + mBounds.top;
        int state = canvas.save();
        canvas.translate(x,y);
        if (angle>0) {
            canvas.rotate(angle);
        }
        x = -mPlayButtonDrawable.getIntrinsicWidth() / 2;
        y = -mPlayButtonDrawable.getIntrinsicHeight() / 2;

        canvas.translate(x,y);

        mPlayButtonDrawable.setAlpha(100);
        mPlayButtonDrawable.draw(canvas);
        canvas.restoreToCount(state);
    }

    /**
     * initiate load full image (sync)
     */
    public void loadFullImage() {
        synchronized (this) {
            mLoadingError = false;
            mLoadingInProgress = true;
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        Drawable drawable = readFullDrawable();
                        synchronized (LazyDrawable.this) {
                            if (drawable != null)
                                setDrawable(drawable);
                            mLoadingInProgress = false;
                        }
                        invalidateSelfOnUiThread();
                    }
                }
        );
    }

    @Override
    public void setAlpha(int alpha) {
        synchronized (this) {
            mDrawable.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        synchronized (this) {
            mDrawable.setColorFilter(cf);
        }
    }

    @Override
    public int getOpacity() {
        synchronized (this) {
            return mDrawable.getOpacity();
        }
    }

    @Override
    public void start() {
        synchronized (this) {
            if (mDrawable instanceof Animatable) {
                ((Animatable)mDrawable).start();
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
        if (mDrawable!=null && isRunning()) stop();
        mDrawable = drawable;
        mDrawable.setCallback(this);
        mBoundsApplied = false;
    }

    /**
     * set unrecoverable load error flag
     */
    protected void onFailure() {
        mLoadingError = true;
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
    protected abstract Drawable readPreviewDrawable();

    /**
     * abstract method. Implementation may load real image from network (not fast),
     * store to cache (and use it when this drawable required again)
     * @return
     */
    protected abstract Drawable readFullDrawable();


    /**
     * sample implementation - only 'slow method'
     * generally - for development and debug purposes
     */

    public static class FromURL extends LazyDrawable {
        private String mSrcUrl;

        public FromURL(String srcUrl) throws IOException {
            super(0, 0);
            mSrcUrl = srcUrl;
            Drawable drawable = readFullDrawable();
            setDrawable(drawable);
        }

        protected FromURL(int srcWidth, int srcHeight) {
            super(srcWidth, srcHeight);
        }

        protected FromURL(int srcWidth, int srcHeight, String srcUrl) {
            super(srcWidth,srcHeight);
            mSrcUrl = srcUrl;
        }

        public String getURL() { return mSrcUrl; }

        @Override
        protected Drawable readPreviewDrawable() {
            return readFullDrawable();
        }

        @Override
        protected Drawable readFullDrawable() {
            URL url;
            try {
                url = new URL(mSrcUrl);
            } catch (MalformedURLException e) {
                onFailure();
                return null;
            }
            Bitmap image = null;
            try {
                image = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (IOException e) {
                onFailure();
                return null;
            }
            setSize(image.getWidth(),image.getHeight());
            return new BitmapDrawable(Resources.getSystem(),image);
        }
    }
}
