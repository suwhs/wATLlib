package su.whs.watl.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 18.08.16.
 */
class Highlights {
    private class Entry {
        int start;
        int end;
        int color;

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

    public void add(int start, int end, int color) {
        Entry newEntry = new Entry(start,end,color);
        if (mEntries.contains(newEntry)) return;
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
    }

    public void clear() {
        mEntries.clear();
    }
}
