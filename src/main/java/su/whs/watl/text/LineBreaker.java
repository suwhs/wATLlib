package su.whs.watl.text;

/**
 * interface for linebreaker-v2 library
 *
 * @author igor n. boulliev <igor@whs.su>
 * @hide
 *
 */
public abstract class LineBreaker {
    /**
     * LineBreaker interface
     *
     * @param text
     * @param start
     * @param end
     * @return type and position of linebreak between start and end NOTE: position of
     * last character at the line
     */

    protected static final int HYPHEN = 0xf0000000;

    public abstract int nearestLineBreak(char[] text, int start, int end, int limit);

    public static int getPosition(int value) {
        return value & 0x0fffffff;
    }

    public static boolean isHyphen(int value) {
        return (value & HYPHEN) == HYPHEN;
    }

    /* punctuation ranges */
    // 20-3f
    // 5b-60
    // 7b-7e

    public boolean isLetter(char ch) {
        return Character.isLetter(ch);
    }

}
