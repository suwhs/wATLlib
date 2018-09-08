package su.whs.watl.layout;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;

/**
 * Created by igor n. boulliev <igor@whs.su> on 18.02.17.
 */

public class Style {
    ParagraphStyle[] mParagraphStyles;
    CharacterStyle[] mCharacterStyles;

    public void apply(TextPaint paint) {
        if (mCharacterStyles!=null)
            for(CharacterStyle style : mCharacterStyles) {
                style.updateDrawState(paint);
            }
    }
}
