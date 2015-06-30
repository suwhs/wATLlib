package su.whs.watl.text;

import android.text.TextPaint;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 09.06.15.
 *
 * @hide
 */
class ProxyLayout extends TextLayout implements TextLayoutEx.TextLayoutListenerAdv {
    private static final String TAG="ProxyLayout";
    private boolean debug = false;
    private int mPosition = 0;
    private Replies mEvents = null;
    private boolean mFinished = false;
    private boolean mAttached = false;
    private TextLayoutEx mLayout;
    private TextLayoutListener mPendingListener = null;
    private boolean mGeometryChanged = false;
    private int mCGWidth = 0;
    private int mCGHeight = 0;
    private int mLastCollectedHeight = 0;
    private boolean mTextInvalidated = false;

    public ProxyLayout(TextLayoutEx textLayoutEx, int pageNo) {
        if (debug)
            Log.v(TAG,"create ProxyLayout pageNo="+pageNo);
        mPosition = pageNo;
        mLayout = textLayoutEx;
        setPaint(textLayoutEx.getPaint());
    }

    /**
     * create ProxyLayout over 'events' sequence
     * @param textLayoutEx
     * @param pageNo
     * @param replies - 'events' sequence
     */

    public ProxyLayout(TextLayoutEx textLayoutEx,int pageNo, Replies replies) {
        if (debug)
            Log.v(TAG,"create ProxyLayout pageNo="+pageNo+" with replies");
        // replay replies when invalidate,
        mViewsCount = 0;
        setPaint(textLayoutEx.getPaint());
        mPosition = pageNo;
        mEvents = new Replies(replies); // clone replies, so a_current == zero
            for (ViewHeightExceedEvent event : replies.a_list) {
                if (event.equals(Event.CLONE) || event.equals(Event.CHANGE) || event.equals(Event.FINISH))
                    mViewsCount++;
            }

        if (debug)
            Log.v(TAG,"PL:"+mPosition+" restored viewsCount="+mViewsCount);
        mLayout = textLayoutEx;

        ViewHeightExceedEvent first = mEvents.first();
        this.width = first.width;
        mLayout.pageGeometryBegins(mPosition,first.width,-1,first.height,this);

    }


    /**
     *
     * @param width      - width of view
     * @param height     - height (-1 if unlimited height)
     * @param viewHeight - 'visible height'
     */

    @Override
    public void setSize(int width, int height, int viewHeight) {
        this.width = width;
        mAttached = true;
        mEvents = new Replies();
        mEvents.add(new ViewHeightExceedEvent(Event.INIT, width, viewHeight, 0, 0));
        reflowedHeight = viewHeight;
        reflowedWidth = width;
        mLayout.pageGeometryBegins(mPosition, width, -1, viewHeight, this);
    }

    @Override
    public void setSize(int width, int height) {
        setSize(width, -1, height);
    }

    @Override
    public boolean isLayouted() {
        return mTextInvalidated || mAttached;
    }

    @Override
    public void onTextInfoInvalidated() {
        this.lines = null;
        if (!mAttached) {
            if (mEvents==null) {
                throw new RuntimeException("ProxyLayout create with no Replies");
            }
            mEvents.rewind();
            // skip first INIT
            mEvents.first();
        } else { // attached
            notifyTextInfoInvalidated();
        }
    }

    @Override
    public void onTextHeightChanged() {
        // stub
        notifyTextHeightChanged();
    }

    @Override
    public void onTextReady() {
        // called when TextViewEx finish reflow process
        if (mPendingListener!=null) {
            attach(mPendingListener);
            mPendingListener = null;
            replayEvents(this.listener);
            notifyTextReady();
        }
    }

    @Override
    public boolean onHeightExceed(int collectedHeight) {
        mLastCollectedHeight = collectedHeight;
        if (!mAttached) {
            if (mEvents==null) {
                throw new RuntimeException("ProxyLayout created without Replies");
            }
            ViewHeightExceedEvent event = mEvents.next();
            if (event.equals(Event.FINISH)) {
                // firstLine();
                // lastLine();
                return false;
            } else if (event.equals(Event.CHANGE)) {
                mCGWidth = event.width;
                mCGHeight = event.height;
                mGeometryChanged = true;
                // we need correct event.lines and event collectedHeight
                if (debug)
                    Log.v(TAG,"PL:"+mPosition+" correct lines="+getLinesCount()+", height="+collectedHeight);
                // lastLine();
                event.lines = getLinesCount();
                event.collectedHeight = collectedHeight;
                return true;
            } else if (event.equals(Event.CLONE)) {
                // we need correct event.lines and event collectedHeight
                event.lines = getLinesCount();
                event.collectedHeight = collectedHeight;
                if (debug)
                    Log.v(TAG,"PL:"+mPosition+" correct lines="+getLinesCount()+", height="+collectedHeight);
                // firstLine();
                // lastLine();
                return true;
            }
            return true;
        } else { // attached
            if (debug)
                Log.v(TAG, "PL:" + mPosition + " views count increment: " + mViewsCount);
            mViewsCount++;
            // lastLine();
            boolean result = this.listener.onHeightExceed(collectedHeight);
            if (!result) { // this page does not need more views
                mEvents.add(new ViewHeightExceedEvent(Event.FINISH,0,0,collectedHeight,this.lines.size()));
                return false;
            } else {
                mEvents.add(new ViewHeightExceedEvent(Event.CLONE, 0, 0, collectedHeight, this.lines.size()));
            }
            return result;
        }
    }

    @Override
    public boolean updateGeometry(int[] geometry) {
        if (!mAttached) {
            if (mGeometryChanged) {
                geometry[0] = mCGWidth;
                geometry[1] = mCGHeight;
                mGeometryChanged = false;
                return true;
            }
            return false;
        } else { // attached and learning;
            boolean result = listener instanceof TextLayoutEx.TextLayoutListenerAdv ? ((TextLayoutEx.TextLayoutListenerAdv)listener).updateGeometry(geometry) : false;
            if (result) {
                mEvents.add(new ViewHeightExceedEvent(Event.CHANGE,geometry[0],geometry[1],mLastCollectedHeight,this.lines.size()));
                return true;
            }
        }
        return false; // geometry does not changed
    }

    public Replies getReplies() {
        if (mEvents.a_complete)
            return new Replies(mEvents);
        throw new RuntimeException("replies not ready yet");
    }

    @Override
    public boolean onProgress(List<TextLine> lines, int collectedHeight, boolean viewHeightExceed) {
        if (!mTextInvalidated) {
            // first call for this page
            mTextInvalidated = true;
            // notifyTextInfoInvalidated();
            onTextInfoInvalidated();
        }
        this.lines = lines;

        // called from TextViewEx.onProgress
        // lines - slice of TextViewEx.lines for current page
        // collectedHeight - total calculated height of text on current page
        // viewHeightExceed == true if bottom bound of current geometry exceed
        if (viewHeightExceed) {
            boolean result = onHeightExceed(collectedHeight);
            if (!result) {
               //  mEvents.add(new ViewHeightExceedEvent(Event.FINISH,0,0,collectedHeight,this.lines.size()));
                onTextReady();
                return false;
            }
        }
        return true;
    }

    @Override
    public void setInvalidateListener(TextLayoutListener listener) {
        if (listener==null) {
            detach();
            return;
        }
        attach(listener);
    }

    @Override
    public void invalidateMeasurement() {
        // avoid nullable this.lines
        if (mEvents==null) {
            Log.e(TAG, "ivalidateMeasurement() called without replies");
            throw new IllegalStateException(TAG+":invalidateMeasurement() called without 'replies'");
        }
        ViewHeightExceedEvent first = mEvents.first();
        this.width = first.width;
        mLayout.pageGeometryBegins(mPosition,first.width,-1,first.height,this);
    }

    @Override
    public LineSpan getLineSpan() {
        return mLayout.getLineSpan();
    }

    @Override
    public char[] getChars() {
        return mLayout.getChars();
    }

    @Override
    public TextPaint getPaint() {
        return mLayout.getPaint();
    }

    @Override
    public ContentView.Options getOptions() {
        return mLayout.getOptions();
    }

    public int getPosition() {
        return mPosition;
    }

    private String firstLine() {
        int ls = getLineStart(0);
        int le = getLineEnd(0);
        try {
            String line = (String) getText().subSequence(ls, le).toString();
            if (debug)
                Log.v(TAG, "PL:"+mPosition+" first line = '" + line + "'");
            return line;
        } catch (StringIndexOutOfBoundsException e) {
            // Log.e(TAG,"invalid values");
            return e.toString();
        }

    }

    private String lastLine() {
        int ln = getLinesCount() - 1;
        int ls = getLineStart(ln);
        int le = getLineEnd(ln);
        try {
            String line = getText().subSequence(ls, le).toString();
            if (debug)
                Log.v(TAG, "PL:"+mPosition+" last line ("+ln+") = '" + line + "'");
            return line;
        } catch (StringIndexOutOfBoundsException e) {
            // Log.e(TAG,"invalid values");
            return e.toString();
        }

    }

    /* */


    enum Event {
        INIT,
        CLONE,
        CHANGE,
        FINISH
    }

    class ViewHeightExceedEvent {
        Event event;
        int width, height, collectedHeight,lines;
        public ViewHeightExceedEvent(Event event,int width, int height, int collectedHeight, int collectedLines) {
            this.event = event;
            this.width = width;
            this.height = height;
            this.collectedHeight = collectedHeight;
            this.lines = collectedLines;
        }

        public String toString() {
            return "" + event;
        }

        public ViewHeightExceedEvent(ViewHeightExceedEvent source) {
            this.event = source.event;
            this.width = source.width;
            this.height = source.height;
            this.collectedHeight = source.collectedHeight;
            this.lines = source.lines;
        }

        public boolean equals(Event e) {
            return e.equals(event);
        }
    }

    class Replies {
        private List<ViewHeightExceedEvent> a_list;
        private int a_current = 0;
        private boolean a_complete = false;

        public Replies(List<ViewHeightExceedEvent> list) {
            a_list = list;
            a_current = 0;
            a_complete = true;
        }

        public Replies() {
            a_list = new ArrayList<ViewHeightExceedEvent>();
            a_current = 0;
            a_complete = false;
        }

        public Replies(Replies r) {
            if (!r.a_complete) {
                throw new RuntimeException("events must be completed before clone");
            }
            a_list = new ArrayList<ViewHeightExceedEvent>(r.a_list.size());
            copy(a_list, r.a_list);
            a_complete = true;
            a_current = 0;
        }

        private void copy(List<ViewHeightExceedEvent> dest, List<ViewHeightExceedEvent> source) {
            for (int i=0; i<source.size(); i++) {
                dest.add(new ViewHeightExceedEvent(source.get(i)));
            }
        }

        public ViewHeightExceedEvent next() {
            if (!a_complete) {
                throw new RuntimeException("events sequence must be completed");
            }
            if (a_current<a_list.size()) {
                return a_list.get(a_current++);
            }
            return null;
        }

        public void add(ViewHeightExceedEvent event) {
            if (event.equals(Event.FINISH)) {
                a_list.add(event);
                a_complete = true;
                a_current = 0;
            } else if (event.equals(Event.CHANGE)) {
                ViewHeightExceedEvent prev = a_list.size()>0 ? a_list.get(a_list.size()-1) : null;
                if (prev!=null && prev.event.equals(Event.CLONE)) {
                    // if previous event == CLONE - replace (onHeightExceed called before updateGeometry)
                    a_list.set(a_list.size()-1,event);
                } else {
                    a_list.add(event);
                }
            } else if (event.equals(Event.CLONE)){
                if (a_list.size()>1) {
                    //Log.e(TAG,"too mach size");
                }
                a_list.add(event);
            } else {
                a_list.add(event);
            }
        }

        public void rewind() {
            a_current = 0;
        }

        public ViewHeightExceedEvent first() {
            rewind();
            return next();
        }
    }

    public void attach(TextLayoutListener listener) {
        // listener may be attached during reflow process on given page geometry - so
        // we need to notify listener about completed views and does not use calls to listener
        // to determine geometry (so we must ignore onViewHeightExceed
        if (debug)
            Log.d(TAG,"PL:"+mPosition+"mAttached");
        if (mEvents!=null && !mEvents.a_complete) {
            mPendingListener = listener;
            return;
            // super.setInvalidateListener(listener);
            // replayEvents(listener);
        } else if (mEvents!=null) {
            replayEvents(listener);
        }
        super.setInvalidateListener(listener);
    }

    private boolean mReplay = false;
    private int mReplayHeight = 0;
    private int mReplayLinesCount = 0;

    @Override
    public int getHeight() {
        if (!mReplay) return super.getHeight();
        // Log.v(TAG,"getHeight() returns fake height="+mReplayHeight);
        return mReplayHeight;
    }

    @Override
    public CharSequence getText() {
        return mLayout.getText();
    }

    @Override
    public int getLinesCount() {
        if (!mReplay) return super.getLinesCount();
        // Log.v(TAG,"getLinesCount() returns fake count="+mReplayLinesCount);
        return mReplayLinesCount;
    }

    private void replayEvents(TextLayoutListener listener) {
        mReplay = true;
        for(ViewHeightExceedEvent event = mEvents.first(); event!=null; event = mEvents.next()) {
            if ( event.equals(Event.INIT)) {
                // we need call 'prepareLayout' on MultiColumnTextViewEx
                listener.onTextInfoInvalidated();
            } else if (event.equals(Event.CLONE)) {
                mReplayHeight = event.collectedHeight;
                mReplayLinesCount = event.lines;
                listener.onHeightExceed(event.collectedHeight);
                // notifyTextHeightChanged();
            } else if (event.equals(Event.CHANGE)) {
                mReplayHeight = event.collectedHeight;
                mReplayLinesCount = event.lines;
                listener.onHeightExceed(event.collectedHeight);
                // notifyTextHeightChanged();
            } else if (event.equals(Event.FINISH)) {
                mReplay = false;
                listener.onHeightExceed(event.collectedHeight);
                break;
            }
        }

    }

    public void detach() {
        if (mEvents!=null && !mEvents.a_complete) {
            throw new RuntimeException("detach view but no replies completed");
        }
        if (debug)
            Log.v(TAG,"detached!");
        // doest not call until current attached listener used to determine page geometry
        // so if text is not ready - throw exception
        super.setInvalidateListener(null);
    }

    @Override
    public void finalize() throws Throwable {
        mPendingListener = null;
        mTextInvalidated = false;
        mLayout = null;
        super.finalize();
    }
}