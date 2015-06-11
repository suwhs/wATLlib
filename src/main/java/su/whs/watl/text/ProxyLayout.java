package su.whs.watl.text;

import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 09.06.15.
 *
 * @hide
 */
class ProxyLayout extends TextLayout implements TextLayoutEx.TextLayoutListenerAdv {
    private static final String TAG="ProxyLayout";
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
        mPosition = pageNo;
        mLayout = textLayoutEx;
    }

    /**
     * create ProxyLayout over 'events' sequence
     * @param textLayoutEx
     * @param pageNo
     * @param replies - 'events' sequence
     */

    public ProxyLayout(TextLayoutEx textLayoutEx,int pageNo, Replies replies) {
        // replay replies when invalidate,
        mPosition = pageNo;
        mEvents = new Replies(replies); // clone replies, so a_current == zero
        mLayout = textLayoutEx;
        ViewHeightExceedEvent first = mEvents.first();
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
        mAttached = true;
        mEvents = new Replies();
        mEvents.add(new ViewHeightExceedEvent(Event.INIT,width,viewHeight,0,0));
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
        // stub
        if (!mAttached) {
            if (mEvents==null) {
                throw new RuntimeException("ProxyLayout create with no Replies");
            }
            mEvents.rewind();
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
                return false;
            }
            if (event.event.equals(Event.CHANGE)) {
                mCGWidth = event.width;
                mCGHeight = event.height;
                mGeometryChanged = true;
                return true;
            }
        } else { // attached
            boolean result = this.listener.onHeightExceed(collectedHeight);
            if (!result) { // this page does not need more views
                mEvents.add(new ViewHeightExceedEvent(Event.FINISH,0,0,collectedHeight,this.lines.size()));
                return false;
            } else
                mEvents.add(new ViewHeightExceedEvent(Event.CLONE, 0, 0, collectedHeight, this.lines.size()));
            return result;
        }
        return false;
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
            notifyTextInfoInvalidated();
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
                notifyTextReady();
                return false;
            }
        }
        return true;
    }

    @Override
    public void setInvalidateListener(TextLayoutListener listener) {
        attach(listener);
    }

    @Override
    public void invalidateMeasurement() {
        // avoid nullable this.lines
    }

    @Override
    public void setPaint(TextPaint paint) {
        // avoid nullable this.lines
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
            a_list = r.a_list;
            a_complete = true;
            a_current = 0;
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
        if (mEvents!=null && !mEvents.a_complete) {
            mPendingListener = listener;
            replayEvents(listener);
        } else {
            super.setInvalidateListener(listener);
            if (mEvents!=null) {
                // we have completed events
                replayEvents(listener);
                notifyTextReady();
            }
        }
    }

    private boolean mReplay = false;
    private int mReplayHeight = 0;
    private int mReplayLinesCount = 0;

    @Override
    public int getHeight() {
        if (!mReplay) return super.getHeight();
        return mReplayHeight;
    }

    @Override
    public CharSequence getText() {
        return mLayout.getText();
    }

    @Override
    public int getLinesCount() {
        if (!mReplay) return super.getLinesCount();
        return mReplayLinesCount;
    }

    private void replayEvents(TextLayoutListener listener) {
        mReplay = true;
        for(ViewHeightExceedEvent event = mEvents.first(); event!=null; event = mEvents.next()) {
            if (Event.INIT.equals(event)) {
                // pass
            } else if (Event.CLONE.equals(event)) {
                mReplayHeight = event.collectedHeight;
                mReplayLinesCount = event.lines;
                notifyTextHeightChanged();
            } else if (Event.CHANGE.equals(event)) {
                mReplayHeight = event.collectedHeight;
                mReplayLinesCount = event.lines;
                notifyTextHeightChanged();
            } else if (Event.FINISH.equals(event)) {
                break;
            }
        }
        mReplay = false;
    }

    public void detach() {
        if (mEvents!=null && !mEvents.a_complete) {
            throw new RuntimeException("detach view but no replies completed");
        }
        // doest not call until current attached listener used to determine page geometry
        // so if text is not ready - throw exception
    }

    @Override
    public void finalize() throws Throwable {
        mPendingListener = null;
        mTextInvalidated = false;
        mLayout = null;
        super.finalize();
    }
}