package su.whs.watl.experimental;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by igor n. boulliev on 04.09.15.
 */
public class TableDrawableBuilder {
    private List<List<CharSequence>> content = new ArrayList<List<CharSequence>>();
    private enum State {
        NONE,
        ROW
    }
    private int currentRow = -1;
    private State currentState = State.NONE;
    private int maxColumnsCount = 0;

    public TableDrawableBuilder() {

    }

    public TableDrawableBuilder row() {
        currentRow++;
        currentState = State.ROW;
        return this;
    }

    public TableDrawableBuilder cell(CharSequence content) {
        if (currentState!=State.ROW) {
            row(); // force row begin
        }
        List<CharSequence> cells;
        if (content.length()>currentRow) {
            cells = this.content.get(currentRow);
        } else {
            cells = new ArrayList<CharSequence>();
            this.content.add(cells);
        }
        cells.add(content);
        if (maxColumnsCount<cells.size()) maxColumnsCount = cells.size();
        return this;
    }

    public Drawable build() {
        return new TableDrawable(this.content);
    }
}
