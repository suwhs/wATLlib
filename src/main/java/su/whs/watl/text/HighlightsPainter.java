package su.whs.watl.text;

import android.graphics.Canvas;
import android.util.SparseArray;

/**
 * Created by igor n. boulliev on 11.09.15.
 */
class HighlightsPainter {
    private TextLayout mLayout;
    private SparseArray<Integer> mRangeStarts = new SparseArray<Integer>();
    private SparseArray<Integer> mRangeEnds = new SparseArray<Integer>();

    public HighlightsPainter(TextLayout layout) {
        mLayout = layout;
    }

    /**
     *
     * @param start
     * @param end
     * @param color
     */

    void add(int start, int end, int color) {
        int val = mRangeStarts.get(start,-1);
        if (val>-1) {

        }
        mRangeStarts.append(start,color);
        mRangeStarts.append(end,color);
    }

    /**
     *
     * @param start
     * @param end
     */

    void remove(int start, int end) {
        int val = mRangeStarts.get(start,-1);
        if (val>-1) {
            mRangeStarts.remove(start);
            mRangeEnds.remove(end);
        }
    }

    /**
     * remove all highlights with color
     * @param color
     */

    void remove(int color) {

    }
    /**
     * begin paint highlights boxes
     */

    void begin() {

    }

    /**
     *
     * @param canvas
     */

    void draw(Canvas canvas) {
        if (mRangeStarts.size()<1) return; // do nothing
        // draw rectangles over each range
        int nextRangeKey = 0;
        int nextRangeStart = mRangeStarts.keyAt(nextRangeKey);
        int nextRangeEnd = mRangeEnds.keyAt(nextRangeKey);
        int nextRangeColor = mRangeStarts.get(mRangeStarts.get(nextRangeStart));

        TextLayout.LinesIterator iter = mLayout.getLinesIterator();
        float y = 0f;

        for (;iter.hasNext();iter.next()) {
            if (iter.getEnd()>nextRangeStart) {
                if (iter.getStart()<nextRangeEnd) {
                    // range overlaps current line

                } else {
                    nextRangeKey++;
                    if (nextRangeKey>mRangeStarts.size()) return; // finish
                    nextRangeStart = mRangeStarts.keyAt(nextRangeKey);
                    nextRangeEnd = mRangeEnds.keyAt(nextRangeKey);
                }
            }
            y += iter.getHeight();
        }
    }

    /**
     * finish paint highlights boxes
     */
    void end() {

    }
}
