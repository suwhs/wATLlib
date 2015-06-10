package su.whs.watl.text;

import android.content.Context;
import android.graphics.Color;
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

public abstract class BaseTextPagerAdapter extends PagerAdapter implements ITextView, TextLayoutEx.PagerViewBuilder, ContentView.OptionsChangeListener {
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
            return ((ProxyLayout)object).getPosition();
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
        ProxyLayout proxyLayout = getProxyLayoutForPage(position);

        View proxiedView = null;

        if (mFakePages.contains(proxyLayout)) {
            // check if we have proxyLayout attached to fake page
            View vp = mFakePages.remove(proxyLayout);
            if (vp!=null) {
                // proxiedView = vp.detachInvisiblePage(proxyLayout.getPosition());

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
        mProxyMap.remove((ProxyLayout) object);
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

    private ProxyLayout getProxyLayoutForPage(int page) {
        if (mProxies.get(page)!=null)
            return mProxies.get(page);
        int vtype = getViewTypeForPage(page);
        if (hasReplies(vtype)) {
            // we have geometry for page's view - so create proxy layout with stored replies
            ProxyLayout pl = new ProxyLayout(mTextLayout,page,mReplies.get(vtype));
            mProxies.put(page,pl);
            return pl;
        }
        ProxyLayout pl = new ProxyLayout(mTextLayout,page);
        mProxies.put(page,pl);
        return pl;
    }

    @Override
    public void pageReady(int page) {
        int vtype = getViewTypeForPage(page);
        ProxyLayout pl = mProxies.get(page);
        if (pl==null)
            throw new RuntimeException("page ready received for non-existent page");
        if (!hasReplies(vtype)) {
            mReplies.put(vtype,pl.getReplies());
        }
        if (mProxyMap.containsKey(pl)) {
            ViewProxy vp = mProxyMap.get(pl);
            vp.resetLoadingState();
        }
        mMaxPageNumber = mMaxPageNumber < page ? page : mMaxPageNumber;
        notifyDataSetChanged();
    }

    @Override
    public void invalidateMeasurement() {
        // complete re-reflow
        if (mTextLayout.isReflowBackgroundTaskRunning()) {
            mTextLayout.setReflowBackgroundTaskCancelled(true);
        }
        setText(mText);
    }

    @Override
    public void invalidateLines() {
        // recalculate lines distribution
        invalidateMeasurement(); // TODO: create new thread, that simulate reflow with new options values
    }

    @Override
    public void invalidate() {
        // refresh current view
        ProxyLayout pl = getProxyLayoutForPage(mPrimaryItem);
        if (mProxyMap.containsKey(pl)) {
            ViewProxy vp = mProxyMap.get(pl);
            vp.invalidate();
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

        public void addInvisiblePage(View page, ProxyLayout layout) {
            TextViewEx invisiblePageTV = (TextViewEx) page.findViewById(mContentResourceId);
            invisiblePageTV.setTextLayout(layout);
            addView(page, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mInvisiblePages.put(layout.getPosition(),page);
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

            int vtype = getViewTypeForPage(page);
            if (hasReplies(vtype)) {
                ProxyLayout layout = new ProxyLayout(mTextLayout,page,mReplies.get(vtype));
                mProxies.put(page,layout);
            } else {
                ProxyLayout layout = new ProxyLayout(mTextLayout,page);
                mProxies.put(page,layout);
                mFakePages.add(layout);
            }

        }
    }

    @Override
    public void layoutFinished() {
        Log.v(TAG,"Layout Finished");
    }

    // TODO: find nearest ChapterTitleSpan to provide page title
    //

    private SparseArray<ProxyLayout.Replies> mReplies = new SparseArray<ProxyLayout.Replies>();

    private boolean hasReplies(int vtype) {
        return mReplies.get(vtype) != null;
    }

    private ProxyLayout.Replies getRepliesForViewType(int vtype) {
        return mReplies.get(vtype);
    }

    private class FakePagesController {
        private Map<ProxyLayout,ViewProxy> mViewsMap = new HashMap<ProxyLayout,ViewProxy>();

        public FakePagesController() {

        }

        public void put(ViewProxy view, View page, ProxyLayout proxyLayout) {
            mViewsMap.put(proxyLayout,view);
            view.addInvisiblePage(page,proxyLayout);
        }

        public ViewProxy get(ProxyLayout proxyLayout) {
            return mViewsMap.get(proxyLayout);
        }

        public View remove(ProxyLayout proxyLayout) {
            ViewProxy vp = mViewsMap.get(proxyLayout);
            if (vp!=null) {
                return vp.detachInvisiblePage(proxyLayout.getPosition());
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
                /*
            * if (mViewsMap.size() < 1) - try to fetch views from mProxyMap
            */

                if (mViewsMap.size()<1) {
                    for (ViewProxy proxy : mProxyMap.values()) {
                        primaryView = proxy;
                        break;
                    }
                } else {
                    // take first known viewProxy
                    for (ViewProxy proxy : mViewsMap.values()) {
                        primaryView = proxy;
                        break;
                    }
                }
            }

            put(primaryView,getViewForPage(layout.getPosition()),layout);
        }

        public boolean contains(ProxyLayout proxyLayout) {
            return mViewsMap.containsKey(proxyLayout);
        }
    }

    private class OptionsWrapper extends ContentView.Options {
        public OptionsWrapper() {
            super();
            setChangeListener(BaseTextPagerAdapter.this);
        }
    }



}
