package su.whs.watl.text;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.Spanned;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * supports for pagination and complex geometry layouts per page
 * supports different geometry for pages
 * supports serialization of 'page split info' for fast layout at restore

 * Created by igor n. boulliev on 30.05.15.
 */
public class TextLayoutEx extends TextLayout {
    private static final String TAG="TextLayoutEx";

    private int pageInProgress = 0;
    private int firstLineForPage = 0;
    private int[] geometry = new int[] { 0, 0 };
    private boolean updateGeometryFlag = false;
    private int collectedHeightTotal = 0;

    private boolean mInvalidating = false;
    private boolean mWaitForInvalidationFinished = false;

    public interface TextLayoutListenerAdv extends TextLayoutListener {
        boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed);
        boolean updateGeometry(int[] geometry);
    }

    public interface PagerViewBuilder {
        void createViewForPage(int page);
        void pageReady(int page);
        void layoutFinished();
        void invalidateMeasurement();
    }

    private class BeginsGeometry {
        int width;
        int height;
        int viewHeight;
        public BeginsGeometry(int w, int h, int v) {
            width = w;
            height = h;
            viewHeight = v;
        }
    }

    private PagerViewBuilder mBuilder;
    private boolean mLaunched = false;
    private SparseArray<TextLayoutListenerAdv> mPages = new SparseArray<TextLayoutListenerAdv>();
    // private SparseArray<BeginsGeometry> mBegins = new SparseArray<BeginsGeometry>();
    private boolean waitForNext = false;

    public TextLayoutEx(Spanned text, TextPaint textPaint, ContentView.Options options, PagerViewBuilder builder) {
        super(text, 0, text.length(), textPaint, options, new TextLayoutListener() {
            @Override
            public void onTextInfoInvalidated() {
                // STUB
            }

            @Override
            public void onTextHeightChanged() {
                // STUB
            }

            @Override
            public void onTextReady() {
                // STUB
            }

            @Override
            public boolean onHeightExceed(int collectedHeight) {
                return true;
            }
        });
        mBuilder = builder;
    }

    public void reset() {
        pageInProgress = 0;
        firstLineForPage = 0;
        updateGeometryFlag = false;
        mLaunched = false;
        synchronized (mPages) {
            mPages.clear();
        }
    }

    /**
     * called by TextLayout.reflow() immediately after viewHeightExceed == true,
     * @param geometry
     * @return
     */

    @Override
    public boolean updateGeometry(int[] geometry) {
        if (updateGeometryFlag) {
            updateGeometryFlag = false;
            geometry[0] = this.geometry[0];
            geometry[1] = this.geometry[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed) {
        Log.v(TAG,"onProgress() + collectedHeight = " + collectedHeight);
        this.lines = lines;
        if (Looper.getMainLooper().getThread().equals(Thread.currentThread())) {
            throw new RuntimeException("here is wait on main thread possible");
        }
            /* list lines always contains ALL lines for TextLayoutEx, and height - contains current viewHeight collected */
        final TextLayoutListenerAdv pageListener = mPages.get(pageInProgress);
        if (pageListener==null) {
            // no listener for page?
            Log.w("REFLOW LISTENER", "no listener for page " + pageInProgress);
            return false;
        }
        if (collectedHeight<1) {
            // Log.v(TAG,"empty height?");
            return true;
        } // FIXME: dirty hack to prevent line exceed bottom of view
        boolean stopForPage = !pageListener.onProgress(new Slice<TextLine>(lines,firstLineForPage,lines.size()), collectedHeight,viewHeightExceed);
        /*
        if (viewHeightExceed) {
            Log.v(TAG, "PL:" + pageInProgress+ " onProgress(...," + firstLineForPage + "," + lines.size() + ") == " + !stopForPage);
            Log.d(TAG, "PL:" + pageInProgress+ " HELL: " + lines.size() + " '" + log_single_line(lines.size() - 1) + "'");
        } */
        if (stopForPage && viewHeightExceed) {
            firstLineForPage = lines.size();
            // post to UI-thread
            pageListener.onTextReady();
            /*
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {

                }
            }); */
            collectedHeightTotal = collectedHeight;
            pageInProgress++;
            if (mPages.get(pageInProgress)==null) {
                synchronized (mPages) {
                    // Log.v(TAG,"request for geometry page "+pageInProgress);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mBuilder.pageReady(pageInProgress-1);
                            mBuilder.createViewForPage(pageInProgress);
                        }
                    });

                    try {
                        waitForNext = true;
                        // Log.v(TAG,"waitForNext");
                        mPages.wait();
                        waitForNext = false;
                        if (isReflowBackgroundTaskCancelled())
                            return false;
                        // Log.v(TAG, "wait done");

                    } catch (InterruptedException e) {
                        // wait interrupted
                        return false;
                    }
                }
            }
        } else if (viewHeightExceed) {
            if (pageListener.updateGeometry(geometry)) {
                // Log.v(TAG,"update geometry == true");
                updateGeometryFlag = true; // next call will be this.updateGeometry
            }
        }
        return true;
    }

    @Override
    public void setSize(int width, int height, int viewHeight) {
        throw new RuntimeException("disabled method - use pageGeometryBegins() instead");
    }


    @Override
    public void onFinish(List<TextLine> lines, int height) {
        // stub
        // Log.v("REFLOW LISTENER", "reflow finished");
        if (firstLineForPage<lines.size()-1) {
            final TextLayoutListenerAdv pageListener = mPages.get(pageInProgress);
            if (pageListener==null) {
                // no listener for page?
                Log.e("REFLOW LISTENER", "no listener for last page " + pageInProgress);
                return;
            }

            pageListener.onProgress(new Slice<TextLine>(lines,firstLineForPage,lines.size()), height,true);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mBuilder.pageReady(pageInProgress);
                    mBuilder.layoutFinished();
                }
            });
        }
        setReflowBackgroundTaskCancelled(false);
        setReflowBackgroundTaskRunning(false);
        setReflowFinished(true);
        listener.onTextReady();
    }

    public void pageGeometryBegins(int pageNo, int width, int height, int viewHeight, TextLayoutListenerAdv listener) {
        Log.v(TAG,"pageGeometryBegins "+pageNo+","+width+","+viewHeight);
        if (!waitForInvalidation()) {
            Log.e(TAG,"error while wait for invalidation finished");
        }
        synchronized (mPages) {
            if (mPages.get(pageNo)!=null) {
                throw new RuntimeException("pageGeometryBegins called twice for pageNo="+pageNo);
            }
            mPages.put(pageNo,listener);
            // mBegins.put(pageNo, new BeginsGeometry(width,height,viewHeight));
            updateGeometryFlag = true;
            geometry[0] = width;
            geometry[1] = viewHeight;
            if (waitForNext)
                mPages.notify();
        }
        if (!mLaunched) {
            if (pageNo==0) {
                mLaunched = true;
                super.setSize(width,height,viewHeight);
            } else {
                mBuilder.createViewForPage(0); // if first requested page > 0 - ask builder to create view for page 0
            }
        }
    }

    @Override
    public void invalidateMeasurement() {
        lockInvalidate();
        mBuilder.invalidateMeasurement();
        super.invalidateMeasurementInternal();
        synchronized (mPages) {
            mPages.clear();
            if (this.lines!=null)
                this.lines.clear();
        }
        reset();
        unlockInvalidate();
    }

    private synchronized void lockInvalidate() {
        mInvalidating = true;
    }

    private synchronized void unlockInvalidate() {
        if (mInvalidating)
            mInvalidating = false;
        if (mWaitForInvalidationFinished) {
            mWaitForInvalidationFinished = false;
            this.notify();
        }
    }

    private synchronized boolean waitForInvalidation() {
        if (mInvalidating)
            try {
                mWaitForInvalidationFinished = true;
                this.wait();
            } catch (InterruptedException e) {
                return false;
            }
        return true;
    }

    @Override
    public void invalidateLines() {
        super.invalidateLines();
    }

    @Override
    protected void stopReflowIfNeed() {
        setReflowBackgroundTaskCancelled(true);
        synchronized (mPages) {
            if (waitForNext)
                mPages.notify();
        }
        super.stopReflowIfNeed();
    }

    /* Slice */
    /*
    @hide
     */
    static class Slice<T> implements List<T> {
        private List<T> mHOLDER;
        private WeakReference<List<T>> mBaseList;
        private int mStart;
        private int mEnd;
        private boolean dbg_cloned = false;

        public Slice(List<T> list, int start, int end) {
            mBaseList = new WeakReference<List<T>>(list);
            mHOLDER = list;
            mStart = start;
            mEnd = end;
        }

        public Slice(Slice<T> slice) {
            mBaseList = slice.mBaseList;
            mHOLDER = slice.mHOLDER;
            mStart = slice.mStart;
            mEnd = slice.mEnd;
            dbg_cloned = true;
        }

        /* remporary shift size methods */

        // public void setSize(int size) {
        //    mEnd = mStart+size;
        // }

        @Override
        public void add(int location, T object) { /* stub */ }

        @Override
        public boolean add(T object) { return false; }

        @Override
        public boolean addAll(int location, Collection<? extends T> collection) { return false; }

        @Override
        public boolean addAll(Collection<? extends T> collection) {
            return false;
        }

        @Override
        public void clear() {

        }

        @Override
        public boolean contains(Object object) {
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            return false;
        }

        @Override
        public T get(int location) {
            if (mBaseList.get()==null) {
                // Log.e(TAG,"error: ");
                return null;
            }
            return mBaseList.get().get(location+mStart);
        }

        @Override
        public int indexOf(Object object) {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @NonNull
        @Override
        public Iterator<T> iterator() {
            // Log.v(TAG,"create Slice.iterator()");
            return new Iterator<T>() {
                private int mCurrent = mStart;
                @Override
                public boolean hasNext() {
                    return mCurrent<mEnd;
                }

                @Override
                public T next() {
                    T result = mBaseList.get().get(mCurrent);
                    mCurrent++;
                    return result;
                }

                @Override
                public void remove() {

                }
            };
        }

        @Override
        public int lastIndexOf(Object object) {
            return 0;
        }

        @NonNull
        @Override
        public ListIterator<T> listIterator() {
            return null;
        }

        @NonNull
        @Override
        public ListIterator<T> listIterator(int location) {
            return null;
        }

        @Override
        public T remove(int location) {
            return null;
        }

        @Override
        public boolean remove(Object object) {
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            return false;
        }

        @Override
        public T set(int location, T object) {
            return null;
        }

        @Override
        public int size() {
            return mEnd-mStart;
        }

        @NonNull
        @Override
        public List<T> subList(int start, int end) {
            return new Slice<T>(this,start,end);
        }

        @NonNull
        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @NonNull
        @Override
        public <T1> T1[] toArray(T1[] array) {
            return null;
        }

        @Override
        public void finalize() throws Throwable {
            mHOLDER = null;
            mBaseList.clear();
            super.finalize();
        }
    }

}
