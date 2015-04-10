package su.whs.watl.text.hyphen;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by igor n. boulliev on 10.04.15.
 */

public class PatternsLoader {
    private static final String TAG = "PatternsLoader";
    private static PatternsLoader mInstance = null;
    private Context mContext = null;
    private Map<String,HyphenPattern> mCache = new HashMap<String,HyphenPattern>();
    private PatternsLoader(Context context) {
        mContext = context;
    }

    public static PatternsLoader getInstance(Context context) {
        synchronized (PatternsLoader.class) {
            if (mInstance == null)
                mInstance = new PatternsLoader(context);
        }
        return mInstance;
    }

    public HyphenPattern getHyphenPatternAssets(String fileName) {
        synchronized (mCache) {
            if (mCache.containsKey(fileName))
                return mCache.get(fileName);
            AssetManager am = mContext.getAssets();
            if (am != null) {
                try {
                    DataInputStream in = new DataInputStream(am.open(fileName));
                    mCache.put(fileName,new HyphenPattern(in));
                    in.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error loading hyphenation rules:" + e);
                    return null;
                }
            }
            return mCache.get(fileName);
        }
    }
}
