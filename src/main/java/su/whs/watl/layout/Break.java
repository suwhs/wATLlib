package su.whs.watl.layout;

/**
 * Created by igor n. boulliev <igor@whs.su> on 18.02.17.
 */

/**
 * class Break describe x-coordinate modifications for given position
 */
public class Break {
    int mStart;   // first character of 'break' (also may called 'part of text')
    int mLength;  // length
    boolean mJustification; // allow draw whitespace after text[mStart:mStart+mLength]
    float mWidth; // x-coordinate must be increased by mWidth after draw this break
}
