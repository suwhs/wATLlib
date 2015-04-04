package su.whs.watl.text;

/**
 * Created by igor n. boulliev <igor@whs.su> on 30.11.14.
 */

import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.util.Log;

import org.xml.sax.XMLReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class HtmlTagHandler implements Html.TagHandler {
    private static final String TAG = "HtmlTagHandler";
    private int mListItemCount = 0;
    private Vector<String> mListParents = new Vector<String>();

    private class LMS extends LeadingMarginSpan.Standard {

        public LMS(int every) {
            super(every);
        }
    }

    private class BS extends BulletSpan {
        public BS(int every) {
            super(every);
        }
    }

    @Override
    public void handleTag(final boolean opening, final String tag, Editable output, final XMLReader xmlReader) {

        if (tag.equals("ul") || tag.equals("ol") || tag.equals("dd")) {
            if (opening) {
                mListItemCount = 0;
                mListParents.add(tag);
            } else {
                mListParents.remove(tag);
                output.append('\n');
            }

            mListItemCount = 0;
        } else if (tag.equals("li")) {
            if (opening)
                handleListTag(output);
            else
                handleListTagClose(output);
        } else if (tag.equals("center")) {
            if (opening) {
                openAlignment(Layout.Alignment.ALIGN_CENTER, output);
            } else {
                closeAlignment(Layout.Alignment.ALIGN_CENTER, output);
            }
        } else if (tag.equals("right")) {
            if (opening)
                openAlignment(Layout.Alignment.ALIGN_OPPOSITE, output);
            else
                closeAlignment(Layout.Alignment.ALIGN_OPPOSITE, output);
        }
    }


    private void handleListTag(Editable output) {
        if (mListParents.lastElement().equals("ul")) {
            output.append('\n');
            int len = output.length();
            output.setSpan(new BS(15 * mListParents.size()), len, len, Spanned.SPAN_MARK_MARK);
        } else if (mListParents.lastElement().equals("ol")) {
            output.append('\n');
            int len = output.length();
            output.setSpan(new LMS(15 * mListParents.size()), len, len, Spanned.SPAN_MARK_MARK);
        }
    }

    private void handleListTagClose(Editable output) {
        if (mListParents.lastElement().equals("ul")) {
            BS last = (BS) getLast(output, BS.class);
            int start = output.getSpanStart(last);
            output.removeSpan(last);
            output.append('\n');
            int len = output.length();
            output.setSpan(last, start, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        } else if (mListParents.lastElement().equals("ol")) {
            mListItemCount++;
            LMS last = (LMS) getLast(output, LMS.class);
            int start = output.getSpanStart(last);
            output.insert(start, mListItemCount + ".");
            output.removeSpan(last);
            output.append('\n');
            int len = output.length();
            output.setSpan(last, start, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private List<Layout.Alignment> mAlignmentStack = new ArrayList<Layout.Alignment>();

    private void openAlignment(final Layout.Alignment alignment, Editable output) {
        output.append("\n"); // new paragraph before aligment span!
        int len = output.length();
        AlignmentSpan span;
        switch (alignment) {
            case ALIGN_OPPOSITE:
                span = new OppositeAlignment();
                break;
            case ALIGN_CENTER:
                span = new CenterAlignment();
                break;
            default:
                return;
        }
        output.setSpan(span, len, len, Spanned.SPAN_MARK_MARK);
        mAlignmentStack.add(0, alignment);
    }

    private void closeAlignment(Layout.Alignment alignment, Editable output) {
        Class<?> cls = null;
        switch (alignment) {
            case ALIGN_OPPOSITE:
                cls = OppositeAlignment.class;
                break;
            case ALIGN_CENTER:
                cls = CenterAlignment.class;
                break;
            default:
                return;
        }
        Layout.Alignment testAlignment = mAlignmentStack.remove(0);
        if (testAlignment != alignment) {
            Log.e(TAG, "aligmnent intersections!");
        }

        AlignmentSpan last = (AlignmentSpan) getLast(output, cls);
        if (last != null) {
            int start = output.getSpanStart(last);
            output.removeSpan(last);
            output.append("\n"); // new line after aligment
            int len = output.length();
            output.setSpan(last, start, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            Log.e(TAG, "close not-opened tag?");
        }
    }

    private Object getLast(Editable text, Class kind) {
        Object[] objs = text.getSpans(0, text.length(), kind);

        if (objs.length == 0) {
            return null;
        } else {
            for (int i = objs.length; i > 0; i--) {
                if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) {
                    return objs[i - 1];
                }
            }
            return null;
        }
    }

    public static class CenterAlignment implements AlignmentSpan {

        @Override
        public Layout.Alignment getAlignment() {
            return Layout.Alignment.ALIGN_CENTER;
        }
    }

    public static class OppositeAlignment implements AlignmentSpan {

        @Override
        public Layout.Alignment getAlignment() {
            return Layout.Alignment.ALIGN_OPPOSITE;
        }
    }
}