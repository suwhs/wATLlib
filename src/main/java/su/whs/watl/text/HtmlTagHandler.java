package su.whs.watl.text;

/**
 * Created by igor n. boulliev <igor@whs.su> on 30.11.14.
 */

import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.util.Log;

import org.xml.sax.XMLReader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import su.whs.watl.experimental.ThirdPartyUtils;
import su.whs.watl.text.style.PreformattedSpan;
import su.whs.watl.text.style.VideoThumbnailSpan;

/**
 * supported tags:
 *  UL
 *  OL
 *  DD
 *  IFRAME (filtered out)
 *  CENTER
 *  RIGHT
 *  RTL
 *  LTR
 */

public class HtmlTagHandler implements Html.TagHandler, Html.ImageGetter {
    private static final String TAG = "HtmlTagHandler";
    private int mListItemCount = 0;
    private Vector<String> mListParents = new Vector<String>();
    private int mIframeStarts = 0;
    private Html.ImageGetter mImageGetter = null;
    private List<Integer> mPreformattedStarts = new ArrayList<Integer>();
    private List<Integer> mPreformattedEnds = new ArrayList<Integer>();
    public boolean hasPreformatted() { return mPreformattedStarts.size() > 0; }

    @Override
    public Drawable getDrawable(String source) {
        if (mImageGetter!=null) return mImageGetter.getDrawable(source);
        return null;
    }

    private class LMS extends LeadingMarginSpan.Standard {
        public LMS(int every) {
            super(every);
        }
    }

    private class BS extends BulletSpan {
        private int offset;
        private int everyc;
        public BS(int every, int offset) {
            super(every+offset);
        }

        // @Override
        // public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout l) {
        //    super.drawLeadingMargin(c, p, x+offset-everyc, dir, top, baseline, bottom, text, start, end, first, l);
        // }
    }

    public HtmlTagHandler(Html.ImageGetter imageGetter) {
        mImageGetter = imageGetter;
    }

    public HtmlTagHandler() {
    }

    @Override
    public void handleTag(final boolean opening, final String _tag, Editable output, final XMLReader xmlReader) {
        String tag = _tag.toLowerCase();
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
        } else if (tag.equals("iframe")) {
            if (opening)
                mIframeStarts = output.length();
            else {
                output.delete(mIframeStarts,output.length());
            }

        } else if (tag.equals("pre")) {
            if (opening)
                openPreformatted(output);
            else
                closePreformatted(output);
        } else if (mImageGetter!=null && tag.equals("video")) {
            // handle embedded video - need new DynamicDrawableSpan ?
            if (opening) {
                // width, height, src, poster
                handleVideoTag(xmlReader,output);
            }
        } else if (tag.equals("source")) {
            // add source urls to active 'video' tag
            if (opening) {

            }
        }
    }


    private void handleListTag(Editable output) {
        if (mListParents.lastElement().equals("ul")) {
            output.append('\n');
            int len = output.length();
            output.setSpan(new BS(2 * mListParents.size(),0), len, len, Spanned.SPAN_MARK_MARK);
        } else if (mListParents.lastElement().equals("ol")) {
            output.append('\n');
            int len = output.length();
            output.setSpan(new LMS(2 * mListParents.size()), len, len, Spanned.SPAN_MARK_MARK);
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

    private void openPreformatted(Editable output) {
        output.append("\n");
        int len = output.length();
        PreformattedSpan span = new PreformattedSpan();
        output.setSpan(span, len, len, Spanned.SPAN_MARK_MARK);
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

    private void closePreformatted(Editable output) {
        PreformattedSpan last = (PreformattedSpan) getLast(output,PreformattedSpan.class);
        if (last!=null) {
            int start = output.getSpanStart(last);
            output.removeSpan(last);
            output.append("\n");
            int len = output.length();
            mPreformattedStarts.add(start);
            mPreformattedEnds.add(len);
            output.setSpan(last,start,len,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void handleVideoTag(XMLReader xmlReader, Editable output) {
        final Map<String,String> attrs = new HashMap<String,String>();
        processAttributes(xmlReader, new String[]{"src", "width", "height", "poster"},
                new AttributeHandler() {
            @Override
            public void onAttribute(String name, String value) {
                attrs.put(name,value);
            }
        });
        if (attrs.containsKey("poster")) {
            output.append('\uFFFC');
            int start = output.length()-1;
            int end = output.length();
            int width = -1;
            int height = -1;
            if (attrs.containsKey("width")) {
                try { width = Integer.parseInt(attrs.get("width")); } catch (NumberFormatException e) {}
            }
            if (attrs.containsKey("height")) {
                try { width = Integer.parseInt(attrs.get("height")); } catch (NumberFormatException e) {}
            }
            output.setSpan(new VideoThumbnailSpan(attrs.get("poster"), attrs.get("src"), width, height, mImageGetter),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private interface AttributeHandler {
        void onAttribute(String name, String value);
    }

    private void processAttributes(final XMLReader xmlReader, String[] attrs, AttributeHandler handler) {
        try {
            Field elementField = xmlReader.getClass().getDeclaredField("theNewElement");
            elementField.setAccessible(true);
            Object element = elementField.get(xmlReader);
            Field attsField = element.getClass().getDeclaredField("theAtts");
            attsField.setAccessible(true);
            Object atts = attsField.get(element);
            Field dataField = atts.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            String[] data = (String[])dataField.get(atts);
            Field lengthField = atts.getClass().getDeclaredField("length");
            lengthField.setAccessible(true);
            int len = (Integer)lengthField.get(atts);
            for(int i = 0; i < len; i++)
                handler.onAttribute(data[i * 5 + 1], data[i * 5 + 4]);
        }
        catch (Exception e) {
            Log.d(TAG, "Exception: " + e);
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

    /*
    * replacement for Html.fromHtml - for easy wrap imageGetter (required to handle <video> tag)
    *
    * */

    public static CharSequence fromHtml(String html, ImageGetter imageGetter) {
        HtmlTagHandler th = new HtmlTagHandler(imageGetter);
        CharSequence s = Html.fromHtml(html, imageGetter, th);
        if (th.hasPreformatted()) {
            s = th.restoreFormatForPreformatted(html,s);
        }
        return s;
    }

    private class Range {
        int start;
        int end;
    }

    private CharSequence restoreFormatForPreformatted(String source, CharSequence charSequence) {
        SpannableStringBuilder ssb;
        if (charSequence instanceof SpannableStringBuilder)
            ssb = (SpannableStringBuilder)charSequence;
        else
            ssb = new SpannableStringBuilder(charSequence);
        Pattern p = Pattern.compile("<pre[^>]*>(.*?)</pre>",Pattern.DOTALL);
        Matcher m = p.matcher(source);
        List<Range> pres = new ArrayList<Range>();

        while(m.find()) {
            Range r = new Range();
            r.start = m.start(1);
            r.end = m.end(1);
            pres.add(r);
        }
        int shift = 0;
        for (int i=0; i<mPreformattedStarts.size() && i < pres.size(); i++) {
            int pstarts = mPreformattedStarts.get(i) + shift;
            int pends = mPreformattedEnds.get(i) + shift;
            Range r = pres.get(i);
            int plen = pends-pstarts;
            CharSequence target = formatPreformatted(source.substring(r.start, r.end));
            ssb.replace(pstarts,pends,target);
            shift -= plen-target.length();
        }
        return charSequence;
    }

    // private static Pattern REMOVE_TAGS_RX = Pattern.compile("")

    protected CharSequence formatPreformatted(String html) {
        return ThirdPartyUtils.unescape(html.replaceAll("</?(code|span){1}.*?/?>", ""));
    }

    public interface ImageGetter extends Html.ImageGetter {
        Drawable getDrawable(String source, int width, int height);
        Drawable getDrawable(String preview, String full, int width, int height);
    }
}