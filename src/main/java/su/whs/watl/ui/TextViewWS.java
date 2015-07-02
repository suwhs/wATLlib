package su.whs.watl.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Created by igor n. boulliev on 08.03.15.
 * License GPL v3 https://www.gnu.org/copyleft/gpl.html
 */

public class TextViewWS extends TextView {
    private static final String TAG = "TextViewWS";
    private boolean mSelectModeActive = false;
    private int mSelectionCursorColor = Color.BLUE;
    private Point mSelectionCursorStart = new Point();
    private Point mSelectionCursorEnd = new Point();
    private float mSelectionCursorStartLineHeight = 0;
    private float mSelectionCursorEndLineHeight = 0;
    private Drawable mSelectionCursorDrawableStart = null;
    private Drawable mSelectionCursorDrawableEnd = null;
    private float mTapX = 0f;
    private float mTapY = 0f;
    private ActionMode.Callback mCustomActionModeCallback = null;
    // @Attribute
    private int mSelectionColor = Color.BLUE;
    private int mSelectionColorDraw = selectionColor();
    private int mSelectionStart = 0;
    private int mSelectionEnd = 0;
    private int mSelectionAlpha = 70;
    private boolean mMovingSelectionCursor = false;
    private boolean mTextIsSelectable = false;
    // @Attribute
    private static final int NO_ACTIVE_CURSOR = 0;
    private static final int ACTIVE_START_CURSOR = 1;
    private static final int ACTIVE_END_CURSOR = 2;
    private int mActiveSelectionCursor = NO_ACTIVE_CURSOR;
    private ActionMode mActionMode = null;
    /* determine cursor and tap area sizes */

    private static int mDefaultSelectionCursorWidth = 20;
    private static int mDefaultSelectionCursorHeight = 40;
    private static int mDefaultTapAreaThreshold = 40;
    private ClickableSpanListener mClickableSpanListener = null;

    /* future - calculating default cursor sizes */

    private void calculateDeviceScreenInfo() {
        if (getContext() instanceof Activity) {
            Activity activity = (Activity) getContext();
            WindowManager windowManager = activity.getWindowManager();
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
        }
    }

    private OnTouchListener mOnTouchListener = new OnTouchListener() {
        private float tX = 0;
        private float tY = 0;
        private boolean statePressed = false;
        private long touchTime = 0;
        private long longTapTime = 500;

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {

            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (mSelectModeActive && mMovingSelectionCursor) {
                        statePressed = false;
                        if (!moveCursor(motionEvent.getX(), motionEvent.getY())) {
                            return false;
                        }
                        return true;
                    } else if (Math.abs(tX - motionEvent.getX()) < 10 && Math.abs(tY - motionEvent.getY()) < 10) {
                        return true;
                    }
                    statePressed = false;
                    return false;
                case MotionEvent.ACTION_UP:
                    long time = System.currentTimeMillis() - touchTime;
                    mMovingSelectionCursor = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    mActiveSelectionCursor = NO_ACTIVE_CURSOR;
                    if (statePressed) {
                        if (mSelectModeActive && time < longTapTime) {
                            jumpSelectionCursor(motionEvent.getX(), motionEvent.getY());
                        } else
                            processTouchAt(motionEvent.getX(), motionEvent.getY(), time > longTapTime);
                        // Log.v(TAG,"tap : " + motionEvent.getX() + ", "+motionEvent.getY());
                        statePressed = false;
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_DOWN:
                    tX = motionEvent.getX();
                    tY = motionEvent.getY();
                    if (mSelectModeActive && selectCursorAt(tX, tY)) {
                        // request all touch events
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    } else {
                        statePressed = true;
                        touchTime = System.currentTimeMillis();
                        return true;
                    }
            }

            return false;
        }
    };


    public TextViewWS(Context context) {
        this(context, null, 0);
    }

    public TextViewWS(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextViewWS(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnTouchListener(mOnTouchListener);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TextViewWS(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnTouchListener(mOnTouchListener);
    }

    public TextViewWS setSelectionColor(int color) {
        mSelectionColor = color;
        return this;
    }


    protected boolean isSelectModeActive() {
        return mSelectModeActive;
    }

    protected void drawAllSelectionCursors(Canvas canvas) {
            /* draw selection cursor */
        if (getSelectionStart() > getStart() - 1) {
            drawSelectionCursor(canvas, mSelectionCursorStart.x, mSelectionCursorStart.y, mSelectionCursorStartLineHeight, true);
        }
        if (getSelectionEnd() < getEnd()) {
            drawSelectionCursor(canvas, mSelectionCursorEnd.x, mSelectionCursorEnd.y, mSelectionCursorEndLineHeight, false);
        }
    }

    /* move out allocation from draw() */
    Paint cursorPaint = new Paint();

    /**
     * called for painting selection cursor at starts and ends
     * <p/>
     * need inverse mode, if cursor out of clipRect (
     * inverse start horizontal
     * inverse end horizontal
     * inverse start vertical (if bottom line)
     * inverse end vertical (if bottom line)
     *
     * @param canvas     - canvas for paint
     * @param x          - x coordinate of cursor
     * @param y          - y coordinate of baseline of text (IMPORTANT)
     * @param lineHeight - height of line
     * @param start      - if true, it is cursor for selection's starts, else - for ends
     */

    protected void drawSelectionCursor(Canvas canvas, float x, float y, float lineHeight, boolean start) {
        x += getCompoundPaddingLeft();
        y += getCompoundPaddingTop();
        int side = start ? mDefaultSelectionCursorWidth : -mDefaultSelectionCursorWidth;
        if (mSelectionCursorDrawableStart != null) {
            // TODO: implement resource drawable
            if (start) {

            } else {
                if (mSelectionCursorDrawableEnd == null) {

                } else {

                }
            }
            return;
        }
        if (x + side < 0 || x + side > canvas.getWidth()) {
            // invert cursor draw horizontal
            side *= -1;
        }

        cursorPaint.setColor(Color.BLUE);
        cursorPaint.setStyle(Paint.Style.FILL);
        cursorPaint.setShader(new LinearGradient(x, y, x - side, y+mDefaultSelectionCursorHeight, Color.BLUE, Color.GRAY, Shader.TileMode.CLAMP));

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x - side, y + mDefaultSelectionCursorHeight / 2);
        path.lineTo(x - side, y + mDefaultSelectionCursorHeight);
        path.lineTo(x, y + mDefaultSelectionCursorHeight);

        path.close();
        cursorPaint.setColor(mSelectionCursorColor);
        cursorPaint.setPathEffect(new CornerPathEffect(3f));

        canvas.drawPath(path, cursorPaint);

        cursorPaint.setColor(Color.BLACK);
        cursorPaint.setStyle(Paint.Style.STROKE);
        // cursorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR)); // BUG on hwaccell
        canvas.drawPath(path, cursorPaint);

    }

    public void setSelectionCursorDrawable(int resourceId) {
        mSelectionCursorDrawableStart = getResources().getDrawable(resourceId);
    }

    public void setSelectionCursorDrawable(int startResourceId, int endResourceId) {
        mSelectionCursorDrawableStart = getResources().getDrawable(startResourceId);
        mSelectionCursorDrawableEnd = getResources().getDrawable(endResourceId);
    }

    private int textAreaWidth() {
        int cpT = getCompoundPaddingTop();
        int cpB = getCompoundPaddingBottom();
        int cpL = getCompoundPaddingLeft();
        int cpR = getCompoundPaddingRight();
        return getWidth() - (cpL + cpR);
    }

    private void calculateSelectionCursorPositionsRaw() {
        Log.d(TAG,"calculateSelectionCursorPositions()");

        Rect startBounds = new Rect();
        Rect endBounds = new Rect();

        int want = textAreaWidth();
        int selectionStart = getSelectionStart();
        int selectionEnds = getSelectionEnd();

        int selectionStartsLine = getLineForPosition(selectionStart);
        int selectionEndsLine = getLineForPosition(selectionEnds);

        mSelectionCursorStart.y = getLineBounds(selectionStartsLine, startBounds);
        mSelectionCursorEnd.y = getLineBounds(selectionEndsLine, endBounds);

        float startCursorOffsetX = getPrimaryHorizontal(selectionStartsLine, selectionStart, want);
        float endCursorOffsetX = getPrimaryHorizontal(selectionEndsLine, selectionEnds+1, want);

        mSelectionCursorStart.x = (int) startCursorOffsetX;

        mSelectionCursorEnd.x = (int) endCursorOffsetX;

        mSelectionCursorStartLineHeight = startBounds.height();
        mSelectionCursorEndLineHeight = endBounds.height();
    }


    /* calculate selection cursors positions */
    private void calculateSelectionCursorPositions() {
        try {
            calculateSelectionCursorPositionsRaw();
        } catch (NullPointerException e) {
//            this.postDelayed(new Runnable() { // dirty hack: need carefully work while reflow in background
//                @Override
//                public void run() {
//                    invalidate();
//                }
//            }, 100);
        }
    }


    private double hypot(double a, double b) {
        return Math.sqrt(a * a + b * b);
    }

    private boolean selectCursorAt(float x, float y) {
        return selectCursorAt(x, y, 0);
    }

    protected boolean selectCursorAt(float x, float y, int startsFromLine) {
        double distanceToStartCursor = hypot(x - mSelectionCursorStart.x, y - mSelectionCursorStart.y);
        double distanceToEndCursor = hypot(x - mSelectionCursorEnd.x, y - mSelectionCursorEnd.y);

        mActiveSelectionCursor = NO_ACTIVE_CURSOR;
        // Log.v(TAG, "deselect active cursor");

        if (distanceToStartCursor > mDefaultTapAreaThreshold && distanceToEndCursor > mDefaultTapAreaThreshold)
            return false;

        if (distanceToEndCursor > distanceToStartCursor) {
            // Log.v(TAG, "selected start cursor");
            mActiveSelectionCursor = ACTIVE_START_CURSOR;
        } else {
            // Log.v(TAG, "selected end cursor");
            mActiveSelectionCursor = ACTIVE_END_CURSOR;
        }
        mTapX = x;
        mTapY = y;
        mMovingSelectionCursor = true;
        return true;
    }

    private void unselectCursor() {
        mActiveSelectionCursor = NO_ACTIVE_CURSOR;
        invalidate();
    }

    private void processTouchAt(float x, float y, boolean longTap) {
        processTouchAt(x, y, longTap, 0);
    }
    /**
     *
     * @return true if text are selectable
     */

    /**
     * @param X       - touch x coord
     * @param Y       - touch y coord
     * @param longTap
     */

    protected void processTouchAt(float X, float Y, boolean longTap, int startsFromLine) {
        int position = getOffsetForCoordinates(X, Y, startsFromLine);
        if (position > -1) {
            if (longTap && isTextSelectable()) {
                Log.v(TAG, "long tap");
                onLongTapCharacter(position);
                return;
            }
            if (getText() instanceof Spanned) {
                ClickableSpan[] spans = ((Spanned) getText()).getSpans(position, position + 1, ClickableSpan.class);
                if (spans != null && spans.length > 0) {
                    ClickableSpan cs = spans[0];
                    if (cs instanceof URLSpan)
                        onUrlClicked(((URLSpan) cs).getURL(), position, spans[0]);
                    return;
                }
                DynamicDrawableSpan[] drawables = ((Spanned) getText()).getSpans(position, position + 1, DynamicDrawableSpan.class);
                if (drawables != null && drawables.length > 0) {
                    DynamicDrawableSpan ds = drawables[0];
                    onDrawableClicked(ds.getDrawable(), position, drawables[0]);
                }
            }
        }
        postInvalidateDelayed(10);
    }


    private int selectionColor() {
        int r = Color.red(mSelectionColor);
        int g = Color.green(mSelectionColor);
        int b = Color.blue(mSelectionColor);
        return Color.argb(mSelectionAlpha, r, g, b);
    }


    protected void onLongTapCharacter(int character) {
        int start = character; // - mTextLayout.getStart();
        int end = character + 1; // - mTextLayout.getStart();
        if (mSelectModeActive) {
            onSelectionModeEnds();
            return;
        }
        CharSequence text = getText();
        int lStart = getStart();
        int lEnd = getEnd();

        boolean letter = Character.isLetter(text.charAt(character));

        while (start > lStart && Character.isLetter(text.charAt(start - 1)) == letter)
            start--;
        while (end < lEnd && Character.isLetter(text.charAt(end)) == letter) end++;
        String __text = getText().subSequence(start, end).toString();
        Log.d(TAG, "long tap text: '" + __text + "'" + end);
        setSelection(start, end, selectionColor());
        mSelectModeActive = true;
        calculateSelectionCursorPositions();
        onSelectionModeStarts(start, end);
        invalidate();
    }


    private boolean moveCursor(float x, float y) {
        return moveCursor(x, y, 0);
    }

    /**
     * moving selection cursor with magnet effect
     * do not change line if y distance less than lineHeight from active y position
     *
     * @param x
     * @param y
     * @return
     */
    protected boolean moveCursor(float x, float y, int startsFromLine) {
        Point cursorPoint = getSelectedCursorPoint();
        if (cursorPoint == null) {
            Log.w(TAG, "move cursor action, but no active cursor");
            mMovingSelectionCursor = false;
            return false;
        }

        float vX = Math.abs(mTapX - x);
        float vY = Math.abs(mTapY - y);

        if (vX > vY && vY < mDefaultTapAreaThreshold) y = mTapY;
        mTapX = x;
        int position = getOffsetForCoordinates(x, y, startsFromLine);
        int activeStart = getSelectionStart();
        int activeEnd = getSelectionEnd();

        if (mActiveSelectionCursor == ACTIVE_START_CURSOR && position != activeStart) {
            // Log.v(TAG,"move start cursor");
            if (position > activeEnd) {
                int a = position;
                position = activeEnd;
                activeEnd = a;
            }
            setSelection(position, activeEnd, selectionColor());
        } else if (mActiveSelectionCursor == ACTIVE_END_CURSOR && position != activeEnd) {
            // Log.v(TAG,"move end cursor");
            if (position < activeStart) {
                int a = position;
                position = activeStart;
                activeStart = a;
            }
            setSelection(activeStart, position, selectionColor());
        } else {
            return true;
        }

        calculateSelectionCursorPositions();
        destroyDrawingCache();
        invalidate();
        return true;
    }

    private void jumpSelectionCursor(float x, float y) {
        jumpSelectionCursor(x, y, 0);
    }

    protected void jumpSelectionCursor(float x, float y, int startsFromLine) {
        // Log.v(TAG,"jump cursor");
        int position = getOffsetForCoordinates(x, y, startsFromLine);
        int selectStart = getSelectionStart();
        int selectEnd = getSelectionEnd();

        if (position == selectStart || position == selectEnd)
            return;

        if (position < selectStart) {
            selectStart = position;
        } else if (position > selectEnd) {
            selectEnd = position;
        } else {
            // move nearest cursor inside selection
            double distanceToStartCursor = hypot(x - mSelectionCursorStart.x, y - mSelectionCursorStart.y);
            double distanceToEndCursor = hypot(x - mSelectionCursorEnd.x, y - mSelectionCursorEnd.y);
            if (distanceToStartCursor < distanceToEndCursor) {
                selectStart = position;
            } else {
                selectEnd = position;
            }
        }
        /* */
        setSelection(selectStart, selectEnd, selectionColor());
        calculateSelectionCursorPositions();
        invalidate();
    }

    private Point getSelectedCursorPoint() {
        if (mActiveSelectionCursor == ACTIVE_START_CURSOR) return mSelectionCursorStart;
        else if (mActiveSelectionCursor == ACTIVE_END_CURSOR) return mSelectionCursorEnd;
        return null;
    }


    public void setCustomSelectionActionModeCallback(ActionMode.Callback callback) {
        mCustomActionModeCallback = callback;
    }

    @SuppressLint("NewApi")
    protected void onSelectionModeStarts(int start, int end) {
        if (mActionMode != null)
            mActionMode.finish();
        if (mCustomActionModeCallback != null) {
            if (Build.VERSION.SDK_INT > 10)
                mActionMode = getParent().
                        startActionModeForChild(this, mCustomActionModeCallback);
            else {
                Log.v(TAG, "action mode callbacks supported from API v11");
            }
        }
    }
    @SuppressLint("NewApi")
    protected void onSelectionModeEnds() {
        if (!mSelectModeActive) return;
        mSelectModeActive = false;
        setSelection(0, 0, selectionColor());
        if (mActionMode != null && Build.VERSION.SDK_INT > 10)
            mActionMode.finish();
        super.setSelected(false);
        mActionMode = null;
        invalidate();
    }

    public void setCustomSelectionActionModeCallback(final android.support.v7.view.ActionMode.Callback actionModeCompat) {
        Log.e(TAG, "unsupported action mode version");
    }

    public int getSelectionStart() {
        return mSelectionStart;
    }

    public int getSelectionEnd() {
        return mSelectionEnd;
    }

    public void setSelected(boolean selected) {
        if (mSelectModeActive!=selected) {
            super.setSelected(selected);
            if (!selected && getText() instanceof Spannable) {
                Selection.removeSelection((Spannable) getText());
                mSelectModeActive = false;
                mSelectionStart = 0;
                mSelectionEnd = 0;
            }
            calculateSelectionCursorPositions();
            invalidate();
        }
    }

    public void setSelection(int start, int end, int color) {
        // super.setSelected(false);
        /* remove built-in selection used for paint in TextView */
        if (getText() instanceof Spannable) {
            Spannable text = (Spannable) getText();
            if (start>-1 && start < end)
                Selection.setSelection((Spannable)getText(),start,end);
            else
                Selection.removeSelection((Spannable)getText());
        }
        mSelectionStart = start;
        mSelectionEnd = end;
        mSelectionColorDraw = color;
    }

    /*layout-depended methods */
    protected int getStart() {
        return 0;
    }

    protected int getEnd() {
        return getText() == null ? 0 : getText().length();
    }

    /**
     * calculate horizontal offset of character
     *  used from calculateSelectionCrusorPosition()
     * @param line          - lineNumber
     * @param postionAtLine - character index (in getText()). poasitionAtLine >= getLineStart(line) && positionAtLine < getLineEnd(line)
     * @param viewWidth     - current view width (required for calculating offset correctly, with justification and alignment)
     * @return - x-coordinate of left bound of character
     */

    protected float getPrimaryHorizontal(int line, int postionAtLine, int viewWidth) {
        return getLayout()
                .getPrimaryHorizontal(postionAtLine)
                - getLayout()
                    .getPrimaryHorizontal(getLayout().getLineStart(line));
    }

    /**
     * calculate character index for given coordinates on canvas (x and y)
     *
     * @param x         - horizontal coordinate
     * @param y         - vertical coordinate
     * @param startLine - count from line number (usually 0)
     * @return index of character
     */

    protected int getOffsetForCoordinates(float x, float y, int startLine) {
        Layout l = getLayout();
        int line = l.getLineForVertical((int) y) + startLine;
        getLineBounds(line,debugClickedLineBound);
        return l.getOffsetForHorizontal(line, x);
    }

    protected int getLineForPosition(int position) {
        return getLayout().getLineForOffset(position);
    }

    protected void onUrlClicked(String url, int position, ClickableSpan span) {
        if (span!=null) span.onClick(this);
    }

    protected void onDrawableClicked(Drawable drawable, int position, DynamicDrawableSpan dds) {
        if (mClickableSpanListener!=null) {
            int line = getLineForPosition(position);
            Rect bounds = new Rect();
            getLineBounds(line,bounds);
            getLineBounds(line,debugClickedLineBound);
            float left = getPrimaryHorizontal(line, position, textAreaWidth()) + getCompoundPaddingLeft();
            float right = getPrimaryHorizontal(line, position + 1, textAreaWidth()) + getCompoundPaddingLeft();
            bounds.left = (int) left;
            bounds.right = (int) right;
            mClickableSpanListener.onClick(this,dds,position,position+1,bounds);
        }
    }

    // public boolean isTextSelectable() { return super.isTextSelectable(); }
    /*
        drawing functions
     */

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInEditMode()) return;
        drawText(canvas);
        drawOverlay(canvas);
    }

    protected void drawText(Canvas canvas) {

    }

    protected void drawOverlay(Canvas canvas) {
        if (isSelectModeActive())
            drawAllSelectionCursors(canvas);
    }

    @Override
    public void setTextIsSelectable(boolean selectable) {
        if (Build.VERSION.SDK_INT > 10)
            super.setTextIsSelectable(selectable);
        else {
            mTextIsSelectable = selectable;
        }
        if (!selectable) {
            if (mSelectModeActive) {
                onSelectionModeEnds();
            }
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text,type);
    }

    @Override
    public boolean isTextSelectable() {
        if (Build.VERSION.SDK_INT > 10)
            return super.isTextSelectable();
        return mTextIsSelectable;
    }

    public void setClickableSpanListener(ClickableSpanListener listener) {
        mClickableSpanListener = listener;
    }

    /* debug */
    protected Rect debugClickedLineBound = new Rect();
}
