package su.whs.watl.text;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by igor n. boulliev on 13.03.15.
 */
class LineSpanBreak {
    public int position = -1; // position < lineSpan.end && position >= lineSpan.start !
    public LineSpanBreak next = null;
    public float width;
    public int skip = 0; // how many characters must be skipped for next line
    public boolean strong = true;
    public boolean hyphen = false;
    /* marks position where span changes direction (next dir = current_dir * -1)
     * for android, it cycle switches from LTR to RTL and back */
    public boolean directionSwitch = false;
    public boolean carrierReturn = false;
    public float tail = -1f;
    // public float align = 0f;

    public static int flagsToInt(LineSpanBreak lineBreak) {
        return
                (lineBreak.strong ? 0x01 : 0x00) |
                        (lineBreak.directionSwitch ? 0x08 : 0x00) |
                        (lineBreak.hyphen ? 0x02 : 0x00) |
                        (lineBreak.carrierReturn ? 0x04 : 0x00);
    }

    public static void flagsFromInt(LineSpanBreak lineBreak, int flags) {
        if ((flags & 0x01) != 0x01) lineBreak.strong = false;
        if ((flags & 0x02) == 0x02) lineBreak.hyphen = true;
        if ((flags & 0x04) == 0x04) lineBreak.carrierReturn = true;
        if ((flags & 0x08) == 0x08) lineBreak.directionSwitch = true;
    }

    public static void Serialize(DataOutputStream osw, LineSpanBreak lineBreak) throws IOException {
        LineSpanBreak counting = lineBreak;
        int count = 0;
        while (counting != null) {
            count++;
            counting = counting.next;
        }
        osw.writeInt(count);
        while (lineBreak != null) {
            osw.writeInt(lineBreak.position); // 4
            osw.writeFloat(lineBreak.width); // 4              8
            osw.writeInt(lineBreak.skip);   // 4              12
            osw.writeInt(flagsToInt(lineBreak)); // 4         16
            osw.writeFloat(lineBreak.tail); //4               20
            lineBreak = lineBreak.next;
        }
    }

    public static LineSpanBreak Deserialize(DataInputStream isw) throws IOException {
        int count = isw.readInt();
        if (count < 1) return null;
        LineSpanBreak result = new LineSpanBreak();
        LineSpanBreak current = result;
        LineSpanBreak last = result;
        for (int i = 0; i < count; i++) {
            current.position = isw.readInt();
            current.width = isw.readFloat();
            current.skip = isw.readInt();
            int flags = isw.readInt();
            flagsFromInt(current, flags);
            current.tail = isw.readFloat();
            current.next = new LineSpanBreak();
            last = current;
            current = current.next;
        }
        last.next = null;
        return result;
    }

    public void release() {
        LineSpanBreak _next = next;
        while (_next != null) {
            LineSpanBreak curr = _next;
            _next = _next.next;
            curr.next = null;
        }
        next = null;
    }

}
