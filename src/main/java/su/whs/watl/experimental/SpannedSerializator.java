/*
 * Copyright 2015 whs.su
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package su.whs.watl.experimental;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TabStopSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.SparseArray;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by igor n. boulliev on 22.08.15.
 */

/**
 * Helper, writes Spanned to DataOutputStream, and reads it's back - from DataInputStream to Spanned
 *
 * to support custom CharacterStyle and ParagrpahStyle - override class and implements
 * own read(int tag, DataInputStream dis) and write(Object o, DataOutputStream dos)
 *
 * write(Object o, DataOutputStream dos)
 * must write int 'tag' (>100), and Span paramteres to stream,
 *
 * and read will be called with 'tag' - and correct instance must be constructed, used prevously written N bytes from stream
 *
 */
public class SpannedSerializator {
    public class SpannedSerializationException extends IOException {
        public SpannedSerializationException(String msg) {
            super(msg);
        }
    }
    public class InvalidVersionException extends SpannedSerializationException {
        public InvalidVersionException(String msg) {
            super(msg);
        }
    }

    public class ReadError extends SpannedSerializationException {
        public ReadError(String msg) {
            super(msg);
        }
    }

    private static final String TAG="SpannedSerializator";
    private Spanned mString;
    private Map<Object,Integer> mWrittenTags = new HashMap<Object,Integer>();
    private SparseArray<Object> mWrittenTagsReverse = new SparseArray<Object>();

    private static final int VERSION = 1;

    private class SpanPlacementInfo {
        Object span;
        int start;
        int end;
        int mode;
    }


    public SpannedSerializator(Spanned string) {
        mString = string;
    }

    protected SpannedSerializator() {

    }

    public static Spanned read(DataInputStream dis) throws IOException, InvalidVersionException, ReadError {
        SpannedSerializator ss = new SpannedSerializator();
        return ss.deserialize(dis);
    }

    private int getClassNameHash() {
        return getClass().getCanonicalName().hashCode();
    }

    /**
     * serialize Spanned to DataOutputStream
     * @param dos
     * @throws IOException
     */

    public void serialize(DataOutputStream dos) throws IOException {
        CharacterStyle[] styles = mString.getSpans(0, mString.length(), CharacterStyle.class);
        ParagraphStyle[] paragraphs = mString.getSpans(0, mString.length(), ParagraphStyle.class);
        dos.writeInt(VERSION);
        dos.writeInt(getClassNameHash());
        dos.writeUTF(mString.toString());
        dos.writeInt(0x1030);
        writeStyles(styles, dos);
        dos.writeInt(0x1030);
        writeParagraphs(paragraphs,dos);
        dos.writeInt(0x1030);
    }

    /**
     *
     * @param dis - DataInputStream
     * @return Spanned instance, reconstructed from stream
     * @throws IOException
     * @throws InvalidVersionException - if spanned will serialized with different version
     */
    public Spanned deserialize(DataInputStream dis) throws IOException, InvalidVersionException, ReadError {
        int version = dis.readInt();
        int classNameHash = dis.readInt();
        if (version!=VERSION || classNameHash != getClassNameHash()) {
            throw new InvalidVersionException("expected version "+VERSION+", found version "+version);
        }
        String s = dis.readUTF();
        if (dis.readInt()!=0x1030) {
            throw new ReadError("fatal error while deserialize string content");
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(s);
        SpanPlacementInfo[] cs = readStyles(dis);
        if (dis.readInt()!=0x1030) {
            throw new ReadError("fatal error while deserialize spans");
        }
        SpanPlacementInfo[] ps = readParagraphs(dis);
        if (dis.readInt()!=0x1030) {
            throw new ReadError("fatal error while deserialize paragraphs");
        }
        if (cs!=null) {
            for(SpanPlacementInfo spi : cs) {
                if (spi!=null&&spi.span!=null)
                    ssb.setSpan(spi.span,spi.start,spi.end,spi.mode);
            }
        }
        if (ps!=null) {
            for(SpanPlacementInfo spi : ps) {
                if (spi!=null&&spi.span!=null) {
                    ssb.setSpan(spi.span,spi.start,spi.end,spi.mode);
                }
            }
        }
        return ssb;
    }

    private void writeStyles(CharacterStyle styles[], DataOutputStream dos) throws IOException {
        dos.writeInt(styles.length);

        for(CharacterStyle characterStyle : styles) {
            writeSingleCharacterStyle(characterStyle,dos);
//            if (characterStyle.getUnderlying()!=null) {
//                CharacterStyle underLying = characterStyle.getUnderlying();
//                if (!mWrittenTags.containsKey(underLying)) {
//                    writeSingleCharacterStyle(underLying,dos);
//                }
//                int writtenTag = mWrittenTags.get(underLying);
//                dos.writeInt(-2);
//                dos.writeInt(writtenTag);
//            } else {
//
//            }
            dos.writeInt(0xf00f); // sync mark
        }
        dos.writeInt(0xf00e); // sync mark
    }

    private void writeSingleCharacterStyle(CharacterStyle style, DataOutputStream dos) throws IOException {
        if (style!=style.getUnderlying() && style.getUnderlying()!=null) {
            if (!mWrittenTags.containsKey(style.getUnderlying())) {
                // underlying span does not written yet;
                writeSingleCharacterStyle(style.getUnderlying(),dos);
            }
            int ref = mWrittenTags.get(style.getUnderlying());
            dos.writeInt(mString.getSpanStart(style));
            dos.writeInt(mString.getSpanEnd(style));
            dos.writeInt(mString.getSpanFlags(style));
            dos.writeInt(-2);   // write special tag
            dos.writeInt(ref);  // write reference id
            return;
        } else if (mWrittenTags.containsKey(style)) {
            // we already written!
            return;
        }
        int start = mString.getSpanStart(style);
        int end = mString.getSpanEnd(style);
        int mode = mString.getSpanFlags(style);
        String clazz = style.getClass().getSimpleName();
        if (mCharacterStylesTags.containsKey(clazz)) {
            dos.writeInt(start);
            dos.writeInt(end);
            dos.writeInt(mode);
            int tag;
            if (style instanceof DynamicDrawableSpan) {
                tag = 11;
            } else
                tag = mCharacterStylesTags.get(clazz);
            dos.writeInt(tag);
            switch (tag) {
                case 1: // BackgroundColorSpan
                    dos.writeInt(((BackgroundColorSpan)style).getBackgroundColor());
                    break;
                case 3: // ForegroundColorSpan
                    dos.writeInt(((ForegroundColorSpan)style).getForegroundColor());
                    break;
                case 7:
                    break;
                case 8: // SuggestionSpan - UNSUPPORTED, SKIP
                case 9: // UnderlineSpan
                    // none to write
                    break;
                case 10: // AbsoluteSizeSpan
                    dos.writeInt(((AbsoluteSizeSpan) style).getSize());
                    dos.writeBoolean(((AbsoluteSizeSpan) style).getDip());
                    break;
                case 11:
                    writeDynamicDrawableSpan((DynamicDrawableSpan)style,dos);
                    break;
                case 13: // LocaleSpan - UNSUPPORTED, SKIP
                    break;
                case 14: // RelativeSizeSpan
                    dos.writeFloat(((RelativeSizeSpan)style).getSizeChange());
                    break;
                case 16: // ScaleXSpan
                    dos.writeFloat(((ScaleXSpan)style).getScaleX());
                    break;
                case 17: // StyleSpan
                    dos.writeInt(((StyleSpan)style).getStyle());
                    break;
                case 18: // SubscriptSpan
                case 19: // SuperscriptSpan
                    // none to write
                    break;
                case 20: // TextAppearanceSpan
                    TextAppearanceSpan s = (TextAppearanceSpan)style;
                    dos.writeInt(s.getTextSize());
                    dos.writeUTF(s.getFamily());
                    ColorStateList cslT = s.getTextColor();
                    ColorStateList cslL = s.getLinkTextColor();
                    writeColorStateListToDataOutputStream(cslT,dos);
                    writeColorStateListToDataOutputStream(cslL,dos);
                    // does not used directly?
                    break;
                case 21: // TypefaceSpan
                    dos.writeUTF(((TypefaceSpan)style).getFamily());
                    break;
                case 22: // UrlSpan
                    dos.writeUTF(((URLSpan)style).getURL());
                    break;
                default:

            }
            mWrittenTags.put(style,mWrittenTags.size());
        } else {
            write(style,dos); // we don't known, how to write this
        }
    }

    private SpanPlacementInfo[] readStyles(DataInputStream dis) throws IOException, ReadError {
        int count = dis.readInt();
        SpanPlacementInfo[] result = new SpanPlacementInfo[count];
        for(int i=0; i<count; i++) {
            result[i] = readSingleCharacterStyle(dis);
            if (dis.readInt()!=0xf00f) { // sync mark
                throw new ReadError("lost sync while read character style: " + i);
            }
        }
        int i = dis.readInt();
        if (i!=0xf00e) {
            throw new ReadError("lost sync after read character styles");
        }
        return result;
    }

    private SpanPlacementInfo readSingleCharacterStyle(DataInputStream dis) throws IOException {
        SpanPlacementInfo spi = new SpanPlacementInfo();
        spi.start = dis.readInt();
        spi.end = dis.readInt();
        spi.mode = dis.readInt();
        int tag = dis.readInt();
        switch (tag) {
            case -1:
                return spi; // we does not known this span type
            case -2: // underlying?
                int ref = dis.readInt();
                spi.span = CharacterStyle.wrap((CharacterStyle) mWrittenTagsReverse.get(ref));
                return spi;
            case 1: // BackgroundColorSpan
                spi.span = new BackgroundColorSpan(dis.readInt());
                break;
            case 3: // ForegroundColorSpan
                spi.span = new ForegroundColorSpan(dis.readInt());
                break;
            case 7: // StrikeTroughSpan
                spi.span = new StrikethroughSpan();
                break;
            case 8: // SuggestionSpan
                Log.w(TAG, "SuggestionSpan not supported");
                break;
            case 9: // UnderlineSpan
                spi.span = new UnderlineSpan();
                break;
            case 10: // AbsoluteSizeSpan
                spi.span = new AbsoluteSizeSpan(dis.readInt(),dis.readBoolean());
                break;
            case 11:
                spi.span = readDynamicDrawableSpan(dis);
                break;
            case 12: // ImageSpan
                spi.span = readImageSpanData(dis);
                break;
            case 13: // LocaleSpan
                // unsupported
                break;
            case 14: // RelativeSizeSpan
                spi.span = new RelativeSizeSpan(dis.readFloat());
                break;
            case 16: // ScaleXSpan
                spi.span = new ScaleXSpan(dis.readFloat());
                break;
            case 17: // StyleSpan
                spi.span = new StyleSpan(dis.readInt());
                break;
            case 18: // SubscriptSpan
                spi.span = new SubscriptSpan();
                break;
            case 19: // SuperscriptSpan
                spi.span = new SuperscriptSpan();
                break;
            case 20: // TextAppearanceSpan
                // spi.span = new TextAppearanceSpan();
                break;
            case 21: // TypefaceSpan
                String fontFamily = dis.readUTF();
                spi.span = new TypefaceSpan(fontFamily);
                break;
            case 22: // UrlSpan
                spi.span = new URLSpan(dis.readUTF());
                break;
            default:
                spi.span = read(tag,dis);
        }
        if (spi.span!=null) {
            mWrittenTagsReverse.put(mWrittenTagsReverse.size(), spi.span);
        }
        return spi;
    }

    private void writeParagraphs(ParagraphStyle paragraphStyle[], DataOutputStream dos) throws IOException {
        dos.writeInt(paragraphStyle.length);
        for (ParagraphStyle style : paragraphStyle) {
            writeSingleParagraphStyle(style, dos);
            dos.writeInt(0xe00e); // sync mark
        }
    }

    private void writeSingleParagraphStyle(ParagraphStyle style, DataOutputStream dos) throws IOException {
        Class clazz = style.getClass();
        dos.writeInt(mString.getSpanStart(style));
        dos.writeInt(mString.getSpanEnd(style));
        dos.writeInt(mString.getSpanFlags(style));
        if (mCharacterStylesTags.containsKey(clazz.getSimpleName())) {
            int tag = mCharacterStylesTags.get(clazz.getSimpleName());
            if (mCharacterStylesTags.containsKey(clazz.getSimpleName())) {
                dos.writeInt(tag);
            }
            switch (tag) {
                case 24: // AligmentSpan.Standard
                    AlignmentSpan.Standard as2 = (AlignmentSpan.Standard)style;
                    dos.writeInt(as2.getAlignment().ordinal());
                    break;
                case 25: // BulletSpan
                    BulletSpan bs = (BulletSpan)style;
                    dos.writeInt(bs.getLeadingMargin(true));
                    dos.writeInt(bs.getLeadingMargin(false));

                    break;
                case 30: // LeadingMarginSpan.Sandard
                    LeadingMarginSpan.Standard lms = (LeadingMarginSpan.Standard)style;
                    dos.writeInt(lms.getLeadingMargin(true));
                    dos.writeInt(lms.getLeadingMargin(false));
                    break;
                case 34: // QuoteSpan
                    QuoteSpan qs = (QuoteSpan)style;
                    dos.writeInt(qs.getColor());
                    break;
                case 36: // TabStopSpan.Standard
                    TabStopSpan.Standard tss = (TabStopSpan.Standard)style;
                    dos.writeInt(tss.getTabStop());
                    break;
                default:
            }
        } else {
            write(style,dos);
        }
    }

    private SpanPlacementInfo[] readParagraphs(DataInputStream dis) throws IOException, ReadError {
        int count = dis.readInt();
        SpanPlacementInfo[] result = new SpanPlacementInfo[count];
        for(int i=0; i<count; i++) {
            result[i] = readSingleParagraph(dis);
            int syncMark = dis.readInt();
            if (syncMark!=0xe00e) {
                throw new ReadError("out of sync while read paragraphstyle " + i);
            }
        }
        return result;
    }

    private SpanPlacementInfo readSingleParagraph(DataInputStream dis) throws IOException {
        SpanPlacementInfo spi = new SpanPlacementInfo();
        spi.start = dis.readInt();
        spi.end = dis.readInt();
        spi.mode = dis.readInt();
        int tag = dis.readInt(); // mCharacterStylesTags.get(clazz.getSimpleName());
        switch (tag) {
            case 24: // AligmentSpan.Standard
                spi.span = new AlignmentSpan.Standard(Alignment.values()[dis.readInt()]);
                break;
            case 25: // BulletSpan
                spi.span = new BulletSpan(dis.readInt());
                dis.readInt(); // skip gap width for other lines
                break;
            case 30: // LeadingMarginSpan.Sandard
                spi.span = new LeadingMarginSpan.Standard(dis.readInt(),dis.readInt());
                break;
            case 34: // QuoteSpan
                spi.span = new QuoteSpan(dis.readInt());
                break;
            case 36: // TabStopSpan.Standard
                spi.span = new TabStopSpan.Standard(dis.readInt());
                break;
            case 80: // RemoteDrawableSpan
                break;
            default:
                spi.span = read(tag,dis);
        }
        return spi;
    }

    private static Map<String,Integer> mCharacterStylesTags = new HashMap<String,Integer>();
    static {
        mCharacterStylesTags.put("BackgroundColorSpan",1);
        mCharacterStylesTags.put("ForegroundColorSpan",3);
        mCharacterStylesTags.put("StrikethroughSpan",7);
        mCharacterStylesTags.put("SuggestionSpan",8);
        mCharacterStylesTags.put("UnderlineSpan",9);
        mCharacterStylesTags.put("AbsoluteSizeSpan", 10);
        mCharacterStylesTags.put("DynamicDrawableSpan", 11);    // WARN: special case
        mCharacterStylesTags.put("ImageSpan",12);               // WARN: special case
        mCharacterStylesTags.put("LocaleSpan",13);
        mCharacterStylesTags.put("RelativeSizeSpan",14);
        mCharacterStylesTags.put("ScaleXSpan",16);
        mCharacterStylesTags.put("StyleSpan",17);
        mCharacterStylesTags.put("SubscriptSpan",18);
        mCharacterStylesTags.put("SuperscriptSpan",19);
        mCharacterStylesTags.put("TextAppearanceSpan",20);
        mCharacterStylesTags.put("TypefaceSpan",21);
        mCharacterStylesTags.put("URLSpan",22);

        /* paragraph styles */
        mCharacterStylesTags.put("AlignmentSpan.Standard",24);
        mCharacterStylesTags.put("BulletSpan",25);
        mCharacterStylesTags.put("LeadingMarginSpan.Standard",30);
        mCharacterStylesTags.put("QuoteSpan",34);
        mCharacterStylesTags.put("TabStopSpan.Standard",36);

        mCharacterStylesTags.put("RemoteDrawableSpan",80);
    }

    private void writeColorStateListToDataOutputStream(ColorStateList colorStates, DataOutputStream dos) throws IOException {
        boolean isStateFul = colorStates.isStateful();
        int defaultColor = colorStates.getDefaultColor();
        dos.writeBoolean(isStateFul);
        dos.writeInt(defaultColor);
        /*if (isStateFul) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_enabled}, // enabled
                    new int[]{-android.R.attr.state_enabled}, // disabled
                    new int[]{-android.R.attr.state_checked}, // unchecked
                    new int[]{android.R.attr.state_pressed}  // pressed
            };
            dos.write(states.length);
            for (int i=0; i<states.length; i++) {
                int c = colorStates.getColorForState(states[i],defaultColor);
                dos.writeInt(states[i][0]);
                dos.writeInt(c);
            }
        } */

        // states.getColorForState()
    }

    private ColorStateList readColorStateListFromDataInputStream(DataInputStream dis) throws IOException {
        boolean isStateful = dis.readBoolean();
        int defaultColor = dis.readInt();
        ColorStateList r;
        if (isStateful) {
            int variants = dis.readInt();
            int[][] states = new int[4][];
            int[] colors = new int[dis.readInt()];
            r = new ColorStateList(states,colors);
        } else {
            int[][] states = new int[][] {
            };
            int[] colors = new int[] {
                    defaultColor
            };
            r = new ColorStateList(states,colors);
        }
        return r;
    }

    /**
     *
     * @param span
     * @param dos
     */

    private void writeImageSpanData(DynamicDrawableSpan span, DataOutputStream dos) {
        Drawable d = span.getDrawable();
        Bitmap bitmap;
        if (d instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable)d).getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            d.draw(canvas);
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90,dos);
        bitmap.recycle();
    }

    private ImageSpan readImageSpanData(DataInputStream dis) throws IOException {
        int vA = dis.readInt();
        ImageSpan span = new ImageSpan(new BitmapDrawable(null,readBitmapData(dis)),vA);
        return span;
    }

    private Bitmap readBitmapData(DataInputStream dis) {
        Bitmap bitmap = BitmapFactory.decodeStream(dis);
        return bitmap;
    }

    /**
     * WARNING: at least one dos.writeInt() must be called in custom implementation
     *
     * @param obj - CharacterStyle or ParagraphStyle to serialize
     * @param dos
     * @throws IOException
     */

    protected void write(Object obj, DataOutputStream dos) throws IOException {
        Log.e(TAG, "unsupported class: " + obj.getClass().getName());
        dos.writeInt(-1); // we MUST write tag stub
    }

    protected Object read(int tag, DataInputStream dis) {
        Log.e(TAG, "unsupported tag: " + tag);
        return null;
    }

    /**
     * by default - calls getDrawable() and store drawable AS BitmapDrawable to stream
     * @param dds
     * @param dos
     * @throws IOException
     */

    protected void writeDynamicDrawableSpan(DynamicDrawableSpan dds, DataOutputStream dos) throws IOException {
        dos.writeInt(dds.getVerticalAlignment());
        writeImageSpanData(dds, dos);
    }

    /**
     * by default - reads drawable from stream
     * @param dis
     * @return
     * @throws IOException
     */
    protected DynamicDrawableSpan readDynamicDrawableSpan(DataInputStream dis) throws IOException, ReadError {

        // final BitmapDrawable drawable = new BitmapDrawable(null,readImageSpanData(dis));
        // DynamicDrawableSpan dds = new ImageSpan(drawable,vA);
        return readImageSpanData(dis);
    }
}
