package su.whs.watl.experimental;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by igor n. boulliev on 12.01.16.
 */
public class SpannableStringBuilder2 implements Editable {

    private class Span {
        Object s;
        int start;
        int end;
        int flags;
        public Span(Object s, int start, int end, int flags) {
            this.s = s;
            this.start = start;
            this.end = end;
            this.flags = flags;
        }
        public Span(Span s, int start, int end) {
            flags = s.flags;
            if (s.start<start) {
                this.start = start;
            } else {
                this.start = s.start - start;
            }
            if (s.end>end) {
                this.end = end;
            } else {
                this.end = s.end - start;
            }
            this.s = s;
        }
    }

    private class Chunk implements CharSequence,Spanned {

        char[] mChars;
        List<Span> mSpans;
        Map<Object,Span> mMap;

        public Chunk(CharSequence charSequence) {
            this(charSequence,0,charSequence.length());
        }

        public Chunk(Chunk a, Chunk b) {
            mChars = new char[a.mChars.length + b.mChars.length];
            for (int i=0; i<a.mChars.length; i++) mChars[i] = a.mChars[i];
            int len = a.mChars.length;
            for (int i=0; i<b.mChars.length; i++) mChars[i+len] = b.mChars[i];
            mSpans = a.mSpans;
            mMap = a.mMap;
            for (Span s: b.mSpans) {
                s.start+=len;
                s.end+=len;
                mSpans.add(s);
                mMap.put(s.s,s);
            }
        }

        public Chunk(CharSequence charSequence, int start, int end) {
            mMap = new HashMap<Object, Span>();
            mSpans = new ArrayList<Span>();
            if (charSequence instanceof Chunk) {
                mChars = new char[end-start];
                Chunk src = (Chunk)charSequence;
                for(int i=0; i<mChars.length;i++) {
                    mChars[i] = src.mChars[i+start];
                }
                for (Span span : src.mSpans) {
                    if (span.end>start && span.start<end) {
                        Span s = new Span(span,start,end);
                        mSpans.add(s);
                        mMap.put(s.s,s);
                    }
                }
            } else {
                TextUtils.getChars(charSequence,start,end,mChars,0);
                if (charSequence instanceof Spanned) {
                    Spanned spanned = (Spanned) charSequence;
                    for (Object o : spanned.getSpans(start,end,Object.class)) {
                        int from = spanned.getSpanStart(o);
                        int to = spanned.getSpanEnd(o);
                        int flags = spanned.getSpanFlags(o);
                        Span span = new Span(o,from-start,to-start,flags);
                        mSpans.add(span);
                        mMap.put(o,span);
                    }
                }
            }
        }

        @Override
        public <T> T[] getSpans(int start, int end, Class<T> type) {
            HashSet<T> collected = new HashSet<T>();
                for (Span s : mSpans) {
                    if (type.isAssignableFrom(s.s.getClass())
                            && s.start<end && s.end>start) collected.add((T) s.s);
            }
            T[] result = (T[]) Array.newInstance(type,collected.size());
            Iterator<T> iter = collected.iterator();
            for (int i=0; i<result.length && iter.hasNext(); i++) {
                result[i] = iter.next();
            }
            return result;
        }

        @Override
        public int getSpanStart(Object tag) {
            if (mMap.containsKey(tag)) {
                Span s = mMap.get(tag);
                return s.start;
            }
            return -1;
        }

        @Override
        public int getSpanEnd(Object tag) {
            if (mMap.containsKey(tag)) {
                Span s = mMap.get(tag);
                return s.end;
            }
            return 0;
        }

        @Override
        public int getSpanFlags(Object tag) {
            if (mMap.containsKey(tag)) {
                Span s = mMap.get(tag);
                return s.flags;
            }
            return 0;
        }

        @Override
        public int nextSpanTransition(int start, int limit, Class type) {
            for (int i=start; i<limit; i++) {
                Span span = mSpans.get(i);
                if (type.isAssignableFrom(span.s.getClass())) return i;
            }
            return limit;
        }

        @Override
        public int length() {
            return mChars.length;
        }

        @Override
        public char charAt(int index) {
            return mChars[index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Chunk(this,start,end);
        }

    }


    private List<Chunk> mChunks = new ArrayList<Chunk>();
    private List<InputFilter> mFilters = new ArrayList<InputFilter>();

    public SpannableStringBuilder2() {

    }

    public SpannableStringBuilder2(CharSequence charSequence) {

    }

    @Override
    public Editable replace(int st, int en, CharSequence source, int start, int end) {
        if (source==null) {
            // delete semantic

            return this;
        }
        Chunk inserted = new Chunk(source,start,end);
        if (en==st) {
            if (st<length()) {
                // insert semantic
                for(Chunk chunk : mChunks) {
                    if (st>chunk.length()) {
                        st-=chunk.length();
                        continue;
                    }
                    Chunk left = null;
                }
            } else {
                // append semantic
            }
        } else {
            // replace semantic
        }

        // lookup affected chunk, split it to parts, and insert new
        return this;
    }

    private Editable replace(int st, int en, char ch) {
        char[] chars = new char[en-st];
        for(int i=0;i<chars.length;i++) chars[i] = ch;
        return this.replace(st,en,new String(chars));
    }

    @Override
    public Editable replace(int st, int en, CharSequence text) {
        return replace(st,en,text,0,text.length());
    }

    @Override
    public Editable insert(int where, CharSequence text, int start, int end) {
        return replace(where,where,text,start,end);
    }

    @Override
    public Editable insert(int where, CharSequence text) {
        return replace(where,where,text,0,text.length());
    }

    private static final String nullSequence = new String("");

    @Override
    public Editable delete(int st, int en) {
        return replace(st,en,nullSequence);
    }

    @Override
    public Editable append(CharSequence text) {
        return replace(length(),length(),text,0,text.length());
    }

    @Override
    public Editable append(CharSequence text, int start, int end) {
        return replace(length(),length(),text,start,end);
    }

    @Override
    public Editable append(char text) {
        return replace(length(),length(),text);
    }

    @Override
    public void clear() {
        mChunks.clear();
    }

    @Override
    public void clearSpans() {
        for(Chunk ch : mChunks) {
            ch.mSpans.clear();
            ch.mMap.clear();
        }
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public InputFilter[] getFilters() {
        return new InputFilter[0];
    }

    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        int len = this.length();
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {

    }

    @Override
    public void removeSpan(Object what) {

    }

    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {

        return null;
    }

    @Override
    public int getSpanStart(Object tag) {
        int i=0;
        for(Chunk chunk : mChunks) {
            if (chunk.mMap.containsKey(tag)) {
                return chunk.mMap.get(tag).start+i;
            }
            i+=chunk.length();
        }
        return -1;
    }

    @Override
    public int getSpanEnd(Object tag) {
        return 0;
    }

    @Override
    public int getSpanFlags(Object tag) {
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        if (limit>length()) limit = length();
        Chunk last = null;
        int skipped = 0;
        for(Chunk chunk : mChunks) {
            last = null;
            if (start-skipped>chunk.length()) {
                skipped += chunk.length();
                continue;
            }
            int next = chunk.nextSpanTransition(start-skipped,limit-skipped,type);
            if (next<chunk.length() && limit-skipped > chunk.length()) {
                last = chunk;
            } else {
                return next+skipped;
            }
        }

        return limit+skipped;
    }

    @Override
    public int length() {
        int result = 0;
        for(Chunk chunk : mChunks) { result += chunk.length(); }
        return result;
    }

    @Override
    public char charAt(int index) {
        int request = index;
        for(Chunk chunk : mChunks) {
            if (index < chunk.length()) return chunk.mChars[index];
            index -= chunk.length();
        }
        throw new IndexOutOfBoundsException(String.format("Error: %s: (index %d) > (length  %d)",getClass().getName(),request,length()));
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new SpannableStringBuilder2(this,start,end);
    }

    public SpannableStringBuilder2(CharSequence source, int start, int end) {

        if (source instanceof SpannableStringBuilder2) {
            for(Chunk chunk : mChunks) {
                if (start>chunk.length()) {
                    start -= chunk.length();
                    end -= chunk.length();
                    continue;
                }

                if (end>chunk.length()) {

                    break;
                }
            }
        } else if (source instanceof Spanned) {
            mChunks.add(new Chunk(source.subSequence(start,end)));
        } else if (source instanceof String) {
            mChunks.add(new Chunk(source.subSequence(start,end)));
        }
    }
}
