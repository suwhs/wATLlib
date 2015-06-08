package su.whs.watl.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import su.whs.watl.ui.ITextView;
import su.whs.watl.ui.TextViewEx;

/**
 * Created by igor n. boulliev on 23.05.15.
 *
 */

public abstract class BaseTextPagerAdapter extends PagerAdapter implements ITextView, TextLayoutEx.PagerViewBuilder {
    private static final String TAG="BaseTextPagerAdapter";

    private TextLayoutEx mTextLayout = null;
    private Spanned mText = null;
    private SparseArray<ProxyLayout> mProxies = new SparseArray<ProxyLayout>();
    private OptionsWrapper mOptions = new OptionsWrapper();
    private int mAttachedPagesCounter = 0;
    private TextPaint mTextPaint = new TextPaint();
    private int mPrimaryItem = 0;
    private SparseArray<List<ViewProxy>> mUnusedViews = new SparseArray<List<ViewProxy>>();
    private Map<ProxyLayout,ViewProxy> mProxyMap = new HashMap<ProxyLayout,ViewProxy>();
    private int mContentResourceId = android.R.id.content;
    private int mMaxPageNumber = 0;
    private boolean mUpdating = false;
    private int mCount = 1;
    // private ViewProxy mViewProxyWithFakePage = null;
    private ViewTypeGeometryCollector mGeometryCollector = new ViewTypeGeometryCollector();
    private FakePagesController mFakePages = new FakePagesController();

    @Override
    public void setText(CharSequence text) {
        if (text instanceof Spanned) {
            mText = (Spanned)text;
        } else {
            mText = new SpannableString(text);
        }
        if (mTextLayout!=null) {
            mTextLayout.stopReflowIfNeed();
            mTextLayout = null;
        }
        mTextLayout = new TextLayoutEx(mText,mTextPaint,this);
        notifyDataSetChanged();
    }

    @Override
    public void setTextSize(float size) {
        Log.e(TAG, "not implemented yet");
    }

    @Override
    public void setTextSize(int unit, float size) {
        Log.e(TAG, "not implemented yet");
    }

    @Override
    public ContentView.Options getOptions() {
        return mOptions;
    }

    @Override
    public int getCount() {
        if (mUpdating) return mCount;
        if (mProxies.size()<1) return 1;
        if (mMaxPageNumber>mCount) {
            mCount = mMaxPageNumber;
            notifyDataSetChanged();
        }
        return mCount;
    }

    @Override
    public int getItemPosition(Object object) {
        if (object instanceof ProxyLayout) {
            return ((ProxyLayout)object).mPosition;
        }
        return 0;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) { // here object - ProxyLayout
        // check if View actual holds content from ((ProxyLayout)object)
        if (view instanceof ViewProxy) {
            return ((ViewProxy)view).isFromProxy(object);
        }
        return false;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return null;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Log.v(TAG,"instantiate item " + position);
        // determine view type for page
        int vtype = getViewTypeForPage(position);

        // check if we have ProxyLayout instance for page
        ProxyLayout proxyLayout = mProxies.get(position);
        View proxiedView = null;

        if (proxyLayout==null) { // if not - create new ProxyLayout
            proxyLayout = new ProxyLayout(position);
            mProxies.put(position, proxyLayout); // and save it to map
        } else {
            // check if we have proxyLayout attached to fake page
            ViewProxy vp = mFakePages.get(proxyLayout);
            if (vp!=null) {
                proxiedView = vp.detachInvisiblePage(proxyLayout.mPosition);
            }
        }

        // check if we have unused ViewProxy for vtype

        List<ViewProxy> unused = mUnusedViews.get(vtype);

        if (unused!=null) {
            if (unused.size()>0) {
                ViewProxy proxy = unused.remove(0);
                // replace ProxyLayout and return
                proxy.replaceProxyLayout(proxyLayout);
                mProxyMap.put(proxyLayout, proxy);
                container.addView(proxy);
                return proxyLayout;
            }
        }

        // construct new view
        if (proxiedView==null)
            proxiedView = getViewForPage(position);
        ViewProxy proxy = new ViewProxy(container.getContext(),proxyLayout);
        mProxyMap.put(proxyLayout, proxy);
        proxy.addRealPage(proxiedView);
        container.addView(proxy);
        return proxyLayout;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        //  if ViewProxy holds fakePage (determine geometry process active) - do not remove view!
        Log.v(TAG,"destroy item "+position);
        int vtype = getViewTypeForPage(position);
        List<ViewProxy> unused = mUnusedViews.get(vtype);
        if (unused==null) {
            unused = new ArrayList<ViewProxy>();
            mUnusedViews.put(vtype,unused);
        }
        // get ViewProxy, which holds this ((ProxyLayout)object)
        ViewProxy proxy = mProxyMap.get(object);
        if (proxy.hasInvisiblePage()) {
            return; // do not remove
        }
        mProxyMap.remove((ProxyLayout)object);
        unused.add(proxy);
        container.removeView(proxy);
    }


    @Override
    public Parcelable saveState() {
        /**
         * save pagination info cache
         */

        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        // super.restoreState(state,loader);
        /**
         * restore pagination info cache
         * **/
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        Log.d(TAG, "setPrimaryItem == " + position + ", " + object);
        mPrimaryItem = position;
    }

    @Override
    public void startUpdate(ViewGroup container) {
        // view pager starts populating views
        super.startUpdate(container);
        mUpdating = true;
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        super.finishUpdate(container);
        mUpdating = false;
    }

    public abstract View getViewForPage(int position);
    public int getViewTypesCount() { return 1; }
    public int getViewTypeForPage(int position) { return 0; }
    public View getViewType(int type) {
        return getViewForPage(0);
    }


    private class ProxyLayout extends TextLayout implements TextLayoutEx.TextLayoutListenerAdv {
        private static final String TAG="ProxyLayout";
        protected int mPosition = 0;
        private boolean mTextReady = false;
        private boolean mTextInvalidated = false;
        private TextLayoutListener wrappedListener = null;
        private List<Point> mGeometry = null;
        private List<Integer> mViewHeights = new ArrayList<Integer>();
        private List<Integer> mLinesForView = new ArrayList<Integer>();
        private int mFakeLinesCounter = 0;

        private int mCurrentVew = 0;
        private int[] mActiveGeometry = new int[] { 0, 0 };
        private boolean mGeometryChanged = false;
        private boolean mLayoutFinished = false;
        private int mCollectedHeight = 0;

        public ProxyLayout(int position) {
            mPosition = position;
            if (position>mMaxPageNumber) mMaxPageNumber = position;
            // need check
            mProxies.put(position,this);
        }

        public ProxyLayout(int position, List<Point> geometry) {
            mPosition = position;
            if (position>mMaxPageNumber) mMaxPageNumber = position;
            mProxies.put(position,this);
            mGeometry = geometry;
            Point first = nextView();
            mTextInvalidated = true;
            mActiveGeometry[0] = first.x;
            mActiveGeometry[1] = first.y;
            mGeometryChanged = true;
            mTextLayout.pageGeometryBegins(mPosition, first.x, -1, first.y, this);
        }

        private Point nextView() {
            Point result = mGeometry.get(mCurrentVew);
            mCurrentVew++;
            return result;
        }

        @Override
        public boolean isLayouted() {
            return mTextInvalidated;
        }

        /* setSize called from TextViewEx.prepareLayout() ui-threas */
        // TODO: MultiColumnTextViewEx calls here

        @Override
        public void setSize(int width, int height, int viewHeight) {
            Log.d(TAG, "setSize");
            mTextInvalidated = true;
            int vtype = getViewTypeForPage(mPosition);
            mGeometry = null;
            mActiveGeometry[0] = width;
            mActiveGeometry[1] = viewHeight;
            mGeometryChanged = true;
            if (!mGeometryCollector.contains(vtype)) {
                mGeometryCollector.notifySetSize(vtype,mPosition,width,viewHeight);
            }

            // publishPageGeometryFirst(mPosition,width,height,viewHeight);
            mTextLayout.pageGeometryBegins(mPosition, width, height, viewHeight, this);
        }

        // TODO: base TextViewEx calls here
        @Override
        public void setSize(int width, int height) {
            setSize(width, -1, height);
        }
        @Override
        public void setInvalidateListener(TextLayoutListener listener) {
            Log.d(TAG,"setInvalidateListener: " + listener);
            attach(listener);
        }

        private void attach(TextLayoutListener listener) {
            Log.d(TAG,"attach");
            wrappedListener = listener;
            if (mTextReady) {
                // we have all lines prepared
                listener.onTextInfoInvalidated();
                if (listener instanceof TextLayoutListener) {
                    // restore geometry for listener by call correct listener.onViewHeightExceed
                    int collectedHeight = mCollectedHeight;
                    mCollectedHeight = 0;
                    mFakeLinesCounter = 0;
                    for (int i=0; i<mViewHeights.size();i++) {
                        int h = mViewHeights.get(i);
                        Point p = mGeometry.get(i);
                        mFakeLinesCounter += mLinesForView.get(i);
                        mCollectedHeight+=h;
                        listener.onHeightExceed(mCollectedHeight);
                    }
                }
                listener.onTextReady();
                mFakeLinesCounter = -1;
            } else if (mTextInvalidated) {
                // we in reflow process for this page
                listener.onTextInfoInvalidated();
            }
        }

        @Override
        public int getHeight() {
            return mCollectedHeight;
        }

        @Override
        public int getLinesCount() {
            if (mFakeLinesCounter>-1) {
                return mFakeLinesCounter;
            }
            return super.getLinesCount();
        }

        private void detach() {
            Log.d(TAG,"detach");
            wrappedListener = null;
        }

        @Override
        public void draw(Canvas canvas, int left, int top, int right, int bottom) {
            super.draw(canvas,left,top,right,bottom);
        }

        @Override
        public boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed) {
            Log.v(TAG, "onProgress()");
            // replace lines
            this.lines = lines;
            if (wrappedListener!=null) {
                // transfer events to wrappedListener
                mCollectedHeight = collectedHeight;
                return true;
            } else {
                // use geometry to handle events manually
                if (viewHeightExceed) {
                    return mGeometryCollector.viewHeightExceed(mPosition);
                }
                return true;
            }
        }

        @Override
        public boolean updateGeometry(int[] geometry) {
            if (mGeometryChanged) {
                Log.v(TAG,"change geometry");
                geometry[0] = mActiveGeometry[0];
                geometry[1] = mActiveGeometry[1];
                mGeometryChanged = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean onHeightExceed(int collectedHeight) {
            Log.v(TAG,"onHeightExceed " + collectedHeight);
            if (wrappedListener!=null) {
                boolean result = wrappedListener.onHeightExceed(collectedHeight);
                mGeometryCollector.onHeightExceed(mPosition,collectedHeight,result);
            } else {
                return mGeometryCollector.hasNextView();
            }
            return false;
        }

        @Override
        public void onTextInfoInvalidated() {
            mTextReady = false;
            if (wrappedListener!=null)
                wrappedListener.onTextInfoInvalidated();
        }

        @Override
        public void onTextHeightChanged() {
            if (wrappedListener!=null)
                wrappedListener.onTextHeightChanged();
        }

        @Override
        public void onTextReady() {
            mTextReady = true;
            if (wrappedListener!=null)
                wrappedListener.onTextReady();
        }

        @Override
        public CharSequence getText() {
            return mTextLayout.getText();
        }
    }


    private class OptionsWrapper extends ContentView.Options {
        private boolean mIsAttached = false;
        private OptionsWrapper() {

        }

        private void attach(ContentView.Options options) {
            copy(options);
        }

    }

    /* used for substitute invisible views to determine invisible pages geometry
    * and show 'loading' stub
    * */
    private class ViewProxy extends FrameLayout {

        private View mRealPage = null;
        private ProgressBar mProgress;
        private boolean mLoading = false;
        private ProxyLayout mProxy;
        private TextViewEx mContent;
        private SparseArray<View> mInvisiblePages = new SparseArray<View>();

        public ViewProxy(Context context, ProxyLayout proxy) {
            super(context);
            mProxy = proxy;
            setBackgroundColor(Color.RED);
            mProgress = new ProgressBar(context);
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setIndeterminate(true);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            addView(mProgress, params);
        }

        public boolean isFromProxy(Object object) {
            return object == mProxy;
        }

        public void replaceProxyLayout(ProxyLayout proxy) {
            if (mProxy!=null) {
                if (mProxy==proxy) return;
                mProxy.detach();
            }
            mProxy = proxy;
            if (mContent!=null)
                mContent.setTextLayout(proxy);
            else {
                throw new RuntimeException("mContent are null");
            }
        }

        public void addInvisiblePage(View page, int position) {
            TextViewEx invisiblePageTV = (TextViewEx) page.findViewById(mContentResourceId);
            invisiblePageTV.setTextLayout(new ProxyLayout(position));
            addView(page, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mInvisiblePages.put(position,page);
        }

        public boolean hasInvisiblePage() {
            return mInvisiblePages.size() > 0;
        }

        public void addRealPage(View page) {
            Log.v(TAG, "addRealPage");
            if (mRealPage!=null) {
                removeView(mRealPage);
                mRealPage = null;
            }
            mRealPage = page;
            mContent = (TextViewEx) page.findViewById(mContentResourceId);
            if (mContent==null) {
                Log.e(TAG,"could not find textviewEx on real page!");
            }
            if (mTextPaint==null) mTextPaint = mContent.getPaint();
            mContent.setTextLayout(mProxy);
            if (mLoading) {
             //   mRealPage.setVisibility(View.INVISIBLE);
               // mContent.onBeforeDraw();
            }
            addView(mRealPage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        }

        public void setLoadingState() {
            mProgress.bringToFront();
            mProgress.setVisibility(View.VISIBLE);
           // if (mRealPage!=null)
           //     mRealPage.setVisibility(View.INVISIBLE);
            mLoading = true;
        }

        public void resetLoadingState() {
            mProgress.setVisibility(View.GONE);
            if (mRealPage!=null)
                mRealPage.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAttachedToWindow() {
            /** ui-thread **/
     //       if (mRealPage!=null && mContent == null) -- this never happens
     //           mContent = (TextViewEx) mRealPage.findViewById(mContentResourceId);
            super.onAttachedToWindow();
            mAttachedPagesCounter++;
            if (mAttachedPagesCounter<2) {
                onAttachedFirst(getRootView().getContext());
            }
            setLoadingState();

        }

        @Override
        public void onDetachedFromWindow() {
            /** ui-thread **/
            // mContent = null;
            super.onDetachedFromWindow();
            mAttachedPagesCounter--;
            if (mAttachedPagesCounter<1)
                onDetachedLast();
        }

        public View detachInvisiblePage(int page) {
            View detached = mInvisiblePages.get(page);
            removeView(detached);
            return detached;
        }
    }



    private void onAttachedFirst(Context context) {
        /** ui-thread **/
    }

    private void onDetachedLast() {
        /** ui-thread **/
        /** detached all views from mContext **/
    }

    @Override
    public void createViewForPage(int page) {
        // if we already have view for page - create proxyLayout on geometry, else - we need to allocate fake page and force content layout on it
        // note - createViewForPage called, when TextLayoutEx are in waiting for pageGeometryBegins call with first parameter == page
        // so, check if view created in 'hidden zone', if so - we need to throw error, if this view has been layouted already ?
        if (mProxies.get(page)!=null) {
            // we already have proxy for page
            ProxyLayout layout = mProxies.get(page);
            if (mProxyMap.containsKey(layout)) {
                // and we already have ViewProxy for this page
                return;
            }
            // we have layout, but not have page
            mFakePages.add(layout);
        } else {
            ProxyLayout layout = new ProxyLayout(page);
            mFakePages.add(layout);
        }
    }

    // TODO: find nearest ChapterTitleSpan to provide page title
    //

    private class ViewTypeGeometryCollector {

        public ViewTypeGeometryCollector() {

        }

        public void notifySetSize(int vtype, int page, int width, int viewHeight) {

        }

        public boolean contains(int page) {
            return false;
        }

        public List<Point> get(int page) {
            return null;
        }

        public boolean viewHeightExceed(int page) {
            return false;
        }

        public void onHeightExceed(int page, int collectedHeight, boolean notLast) {

        }

        public boolean hasNextView() {
            return false;
        }
    }

    private class FakePagesController {
        private Map<ProxyLayout,ViewProxy> mViewsMap = new HashMap<ProxyLayout,ViewProxy>();

        public FakePagesController() {

        }

        public void put(ViewProxy view, View page, ProxyLayout proxyLayout) {
            mViewsMap.put(proxyLayout,view);
            view.addInvisiblePage(page,proxyLayout.mPosition);
        }

        public ViewProxy get(ProxyLayout proxyLayout) {
            return mViewsMap.get(proxyLayout);
        }

        public View remove(ProxyLayout proxyLayout) {
            ViewProxy vp = mViewsMap.get(proxyLayout);
            if (vp!=null) {
                return vp.detachInvisiblePage(proxyLayout.mPosition);
            }
            return null;
        }

        public void add(ProxyLayout layout) {
            // lookup primary item proxyview
            ProxyLayout pl = mProxies.get(mPrimaryItem);
            if (pl==null) {
                // we have no proxy for primary item?
                // pl = new ProxyLayout(mPrimaryItem);
                Log.e(TAG,"no proxy for primary item");
                return;
            }
            ViewProxy primaryView = mViewsMap.get(pl);
            if (primaryView==null) {
                for (ViewProxy proxy : mViewsMap.values()) {
                    primaryView = proxy;
                    break;
                }
            }
            put(primaryView,getViewForPage(layout.mPosition),layout);
        }
    }
}
