package su.whs.watl.experimental;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by igor n.boulliev on 11.01.16.
 */
public class HtmlToSpanned {

    private static class Parser {
        private char[] mChars;
        Parser(CharSequence charSequence) {
            mChars = new char[charSequence.length()];
            TextUtils.getChars(charSequence,0,charSequence.length(),mChars,0);
        }

        /**
         *  |<html><body class ="value" other= "2"><div>text</br><span name=':('>test text &lt;</span>not spanned "text"<span id="23"></span></div></body></html>
         * T|G^^^^TG^^^^XA^^^^LVQN^^^^LXA^^^^VMQNXTG^^^T^^^^GC^^TG^^^^XA^^^VQ^^XT^^^^X^^^^XE^^TGC^^^^T^^^X^^^^^^^XQ^^^^^G^^^^XA^VQN^XTGC^^^^TGC^^^TGC^^^^TGC^^^^T
         *  |++++..++++.+++++..+++++...++++..+...+++.++++..++..++++.++++..++..++++++++++.oo...++++.++++++++++++++++++.++++.++..++....++++...+++...++++...++++.
         *
         *  states:
         *  T - text
         *  G - tag, wait for name - MODIFIER
         *  X -
         *      if G - commit tag name, else skip
         *  A - attribute name
         *  L -
         *      if A - commit attribute name
         *      if N - commit attribute value
         *      if L - skip
         *  V - if A or L - switch to M
         *  Q - quote
         *
         * @param imageGetter
         * @param tagHandler
         */

        private void parse(ImageGetter imageGetter, TagHandler tagHandler) {
            Map<String,String> attributes = new HashMap<String, String>();
            for (int i=0; i<mChars.length; i++) {
                char ch = mChars[i];
                switch (ch) {
                    case '<':
                        break;
                    case '>':
                        break;
                    case '"':
                        break;
                    case '\'':
                        break;
                    case '&':
                        break;
                    case ';':
                        break;
                    case '\\':
                        break;
                    case '=':
                        break;
                    case '/':
                        break;
                    case ' ':
                    case '\n':
                    case '\t':
                        break;
                    default:
                }
            }
        }

        private String entityValue(String entity) {
            return "&"+entity+";";
        }

    }

    public interface TagHandler {
        void onTag(String tag, boolean opening, Map<String,String> attributes);
    }

    public abstract class ImageGetter {
        public abstract Drawable getDrawable(String source);
    }

    /* */
    public class DefaultTagHandler implements TagHandler {

        @Override
        public void onTag(String tag, boolean opening, Map<String, String> attributes) {
            // a
            // b|bold|strong
            // i|italic
            // font
            // quote|blockquote
            // p
            // table
            // tbody
            // tr
            // td
            // li
            // ul
            // ol
            // img
            // embed
            // video
            // audio
            // center
            // align
            // script
        }

    }
}
