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
import android.view.ViewParent;
import android.widget.Button;
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
    private int mContentResourceId = -1;
    private int mMaxPageNumber = 0;
    private boolean mUpdating = false;
    private int mCount = 1;
    private ITextPagesNumber mPagesNumberListener = null;
    private boolean mNeedFontSize = true;
    private FakePagesController mFakePages = new FakePagesController();

    // static initialization
    {
        mTextPaint.setAntiAlias(true);
        mTextPaint.linkColor = Color.BLUE; // required to correctly draw colors
        mOptions.setTextPaddings(15,15,15,15);
    }

    public BaseTextPagerAdapter(int resourceId) {
        super();
        mContentResourceId = resourceId;
    }

    public BaseTextPagerAdapter(int resourceId, ITextPagesNumber pagesNumberListener) {
        this(resourceId);
        mPagesNumberListener = pagesNumberListener;
    }

    @Override
    public void setText(CharSequence text) {
        mCount = 1;
        mMaxPageNumber = 0;
        if (text instanceof Spanned) {
            mText = (Spanned)text;
        } else {
            mText = new SpannableString(text);
        }
        if (mTextLayout!=null) {
            mTextLayout.stopReflowIfNeed();
            mTextLayout = null;
        }
        mTextLayout = new TextLayoutEx(mText,mTextPaint,mOptions,this);
        notifyDataSetChanged();
    }

    @Override
    public void setTextSize(float size) {
        mNeedFontSize = false;
        mTextPaint.setTextSize(size);
    }

    @Override
    public void setTextSize(int unit, float size) {
        throw new RuntimeException("setTextSize(unit,size) not supported");
    }

    @Override
    public ContentView.Options getOptions() {
        return mOptions;
    }

    @Override
    public int getCount() {
        if (mUpdating) return mCount;
        if (mProxies.size()<1) return 1;
        if (mMaxPageNumber+1>mCount) {
            mCount = mMaxPageNumber+1;
            notifyDataSetChanged();
        }
        return mCount;
    }

    @Override
    public int getItemPosition(Object object) {
        if (object instanceof ProxyLayout) {
            int position = ((ProxyLayout)object).getPosition();
            if (mProxies.get(position)==object)
                return position;
        }
        return POSITION_NONE;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) { // here object - ProxyLayout
        // check if View actual holds content from ((ProxyLayout)object)
        if (view instanceof ViewProxy) {
            if (mProxies.get(mPrimaryItem)==object) {
                ViewProxy proxy = mProxyMap.get(object);
                proxy.updateIndicators();
            }
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
        // Log.v(TAG,"destroy item "+position);
        int vtype = getViewTypeForPage(position);
        List<ViewProxy> unused = mUnusedViews.get(vtype);
        if (unused==null) {
            unused = new ArrayList<ViewProxy>();
            mUnusedViews.put(vtype,unused);
        }
        // get ViewProxy, which holds this ((ProxyLayout)object)
        ViewProxy proxy = mProxyMap.get(object);
        if (proxy!=null && proxy.hasInvisiblePage()) {
            return; // do not remove
        }
        if (proxy!=null) {
            mProxyMap.remove((ProxyLayout) object);
            unused.add(proxy);
            container.removeView(proxy);
        }
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
        if (mPrimaryItem==position) return;
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
        if (mCount<mMaxPageNumber+1) {
            mCount=mMaxPageNumber+1;
            notifyDataSetChanged();
        }
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
        mCount = 1;
        mMaxPageNumber = 0;
        clearViewProxies();
        clearProxyLayouts();
        notifyDataSetChanged();
    }

    private void clearViewProxies() {

        for (ViewProxy vp : mProxyMap.values()) {
            vp.clear();
            ViewParent p = vp.getParent();
            if (p!=null) {
                if (p instanceof ViewGroup) {
                    ((ViewGroup)p).removeView(vp);
                }
            }
        }
        mProxyMap.clear();
    }

    private void clearProxyLayouts() {
        for (int i=0; i<mUnusedViews.size(); i++) {
            int k = mUnusedViews.keyAt(i);
            List<ViewProxy> unused = mUnusedViews.get(k);
            unused.clear();
        }
        mUnusedViews.clear();
        mProxies.clear();
        mReplies.clear();
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

    @Override
    public void finalize() throws Throwable {
        mText = null;
        mProxies.clear();
        mProxyMap.clear();
        mUnusedViews.clear();
        mTextLayout = null;
        super.finalize();
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
            mProgress = new ProgressBar(context);
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setIndeterminate(true);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            addView(mProgress, params);
        }

        public boolean isFromProxy(Object object) {
            return object == mProxy && object!=null;
        }

        public void replaceProxyLayout(ProxyLayout proxy) {
            if (mProxy!=null) {
                if (mProxy==proxy) return;
                mProxy.detach();
            }
            mProxy = proxy;
            if (mContent!=null) {
                mContent.setTextLayout(proxy);
                updateIndicators();
                if (proxy.isLayouted())
                    resetLoadingState();
            } else {
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
            // Log.v(TAG, "addRealPage");
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
            updateIndicators();
            if (mProxy.isLayouted()) {
                resetLoadingState();
            }
            addView(mRealPage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        public void updateIndicators() {
            if (mPagesNumberListener!=null && mRealPage!=null) {
                mPagesNumberListener.updateInfo(mRealPage,mProxy.getPosition(),getCount());
            }
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
            super.onAttachedToWindow();
            mAttachedPagesCounter++;
            if (mAttachedPagesCounter<2) { //
                onAttachedFirst(getRootView().getContext());
            }
            if (!mProxy.isLayouted())
                setLoadingState();
            else
                resetLoadingState();
            updateIndicators();
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

        @Override
        public void invalidate() {
            // Log.v(TAG,"called invalidate from child?");
            super.invalidate();
        }

        public void clear() {
            if (mRealPage!=null) {
                removeView(mRealPage);
                mRealPage = null;
            }
            mContent = null;
            if (mProxy!=null) mProxy.setInvalidateListener(null);
            mProxy = null;
            for (int i=0; i<mInvisiblePages.size(); i++) {
                int k = mInvisiblePages.keyAt(i);
                View v = mInvisiblePages.get(k);
                if (v!=null) {
                    removeView(v);
                }
            }
            mInvisiblePages.clear();
            setLoadingState();
        }
    }

    private void onAttachedFirst(Context context) {
        /** ui-thread **/
        if (!mNeedFontSize)
            return;
        float size = new Button(context).getTextSize();
        mNeedFontSize = false;
        mTextPaint.setTextSize(size);
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
            if (!layout.isLayouted()) {
                Log.v(TAG,"no layout calculated for " + layout);
            }
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
                mProxies.put(page, layout);
            } else {
                ProxyLayout layout = new ProxyLayout(mTextLayout,page);
                mProxies.put(page,layout);
                mFakePages.add(layout);
                if (page>mMaxPageNumber)
                    mMaxPageNumber = page;
            }

        }
    }

    @Override
    public void layoutFinished() {
        // Log.v(TAG,"Layout Finished");
    }

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
