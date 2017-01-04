package su.whs.watl.text;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 18.08.16.
 */
class Highlights {
    public interface HighlightAnimationListener {
        void onHighlightAnimationStarts(int highlight);
        void onHighlightAnimationUpdate(int highlight);
        void onHighlightAnimationFinished(int highlight);
    }

    private class Entry {
        int start;
        int end;
        int color;
        float phase;

        public Entry(int start, int end, int color) {
            this.start = start; this.end = end; this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Entry) {
                Entry other = (Entry)o;
                return start == other.start && end == other.end && color == other.color;
            }
            return super.equals(o);
        }
    }
    private List<Entry> mEntries = new ArrayList<Entry>();

    public Highlights() {}

    public int add(int start, int end, int color) {
        return add(start,end,color,null,0);
    }

    public int add(int start, int end, int color, HighlightAnimationListener listener, int duration) {
        Entry newEntry = new Entry(start,end,color);
        if (mEntries.contains(newEntry)) return mEntries.indexOf(newEntry);
        boolean crossed = false;
        for (Entry exists : mEntries) {
            if (start<exists.start && end>exists.start && end < exists.end) {
                if (color==exists.color) {
                    exists.start = start;
                } else {
                    exists.start = end;

                }
                mEntries.add(newEntry);
                break;
            } else if (start>exists.start && start<exists.end && end>exists.end) {
                break;
            } else if (start<exists.start && end>exists.end) {
                break;
            }
        }
        if (Build.VERSION.SDK_INT>=11 && listener!=null) {

        }
        return mEntries.indexOf(newEntry);
    }

    public void clear() {
        mEntries.clear();
    }

    public void remove(int entryIndex) {
        remove(entryIndex,null,0);
    }

    public void remove(int entryIndex, HighlightAnimationListener listener, int duration) {

    }

    public void remove(int start, int end) {
        // removes highlight
    }

}
