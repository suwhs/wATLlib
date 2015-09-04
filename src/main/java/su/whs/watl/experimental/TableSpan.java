package su.whs.watl.experimental;

import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;

/**
 * Created by igor n. boulliev on 04.09.15.
 */
public class TableSpan extends DynamicDrawableSpan {
    private TableDrawable table;
    public TableSpan(TableDrawable table) {
        this.table = table;
    }
    @Override
    public Drawable getDrawable() {
        return this.table;
    }
}
