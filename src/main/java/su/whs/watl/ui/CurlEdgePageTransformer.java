package su.whs.watl.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Created by igor n. boulliev on 30.05.15.
 *
 */
public class CurlEdgePageTransformer implements IPageTransformerExt {
    private ViewPagerExt mContainer;

    /** If TRUE we are currently auto-flipping */
    private boolean bFlipping;

    /** TRUE if the user moves the pages */
    private boolean bUserMoves;

    private FlipAnimationHandler mAnimationHandler;
    /** Px / Draw call */
    private int mCurlSpeed;

    /** Fixed update time used to create a smooth curl animation */
    private int mUpdateRate;

    /** The initial offset for x and y axis movements */
    private int mInitialEdgeOffset;

    /** The mode we will use */
    private int mCurlMode;

    /** Simple curl mode. Curl target will move only in one axis. */
    public static final int CURLMODE_SIMPLE = 0;

    /** Dynamic curl mode. Curl target will move on both X and Y axis. */
    public static final int CURLMODE_DYNAMIC = 1;

    /** Maximum radius a page can be flipped, by default it's the width of the view */
    private float mFlipRadius;

    /** Point used to move */
    private Vector2D mMovement;

    /** The finger position */
    private Vector2D mFinger;

    /** Movement point form the last frame */
    private Vector2D mOldMovement;

    /** Page curl edge */
    private Paint mCurlEdgePaint;

    /** Our points used to define the current clipping paths in our draw call */
    private Vector2D mA, mB, mC, mD, mE, mF, mOldF, mOrigin;

    /** Left and top offset to be applied when drawing */
    private int mCurrentLeft, mCurrentTop;

    /** If false no draw call has been done */
    private boolean bViewDrawn;

    /** Defines the flip direction that is currently considered */
    private boolean bFlipRight;


    /** Used to control touch input blocking */
    private boolean bBlockTouchInput = false;

    /** Enable input after the next draw event */
    private boolean bEnableInputAfterDraw = false;


    /** LAGACY List of pages, this is just temporal */
    private ArrayList<Bitmap> mPages;

    /** LAGACY Current selected page */
    private int mIndex = 0;

    /**
     * Inner class used to make a fixed timed animation of the curl effect.
     */
    class FlipAnimationHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            CurlEdgePageTransformer.this.FlipAnimationStep();
        }

        public void sleep(long millis) {
            this.removeMessages(0);
            sendMessageDelayed(obtainMessage(0), millis);
        }
    }

    public void attach(ViewPagerExt pager) {
        mContainer = pager;
    }

    /**
     * Execute a step of the flip animation
     */
    public void FlipAnimationStep() {
        if ( !bFlipping )
            return;

        int width = mContainer.getWidth();

        // No input when flipping
        bBlockTouchInput = true;

        // Handle speed
        float curlSpeed = mCurlSpeed;
        if ( !bFlipRight )
            curlSpeed *= -1;

        // Move us
        mMovement.x += curlSpeed;
        mMovement = CapMovement(mMovement, false);

        // Create values
        DoPageCurl();

        // Check for endings :D
        if (mA.x < 1 || mA.x > width - 1) {
            bFlipping = false;
            if (bFlipRight) {
                nextView();
            }
            ResetClipEdge();

            // Create values
            DoPageCurl();

            // Enable touch input after the next draw event
            bEnableInputAfterDraw = true;
        }
        else
        {
            mAnimationHandler.sleep(mUpdateRate);
        }

        // Force a new draw call
        mContainer.invalidate();
    }

    /**
     * Do the page curl depending on the methods we are using
     */
    private void DoPageCurl()
    {
        if(bFlipping){
            if ( IsCurlModeDynamic() )
                doDynamicCurl();
            else
                doSimpleCurl();

        } else {
            if ( IsCurlModeDynamic() )
                doDynamicCurl();
            else
                doSimpleCurl();
        }
    }

    /**
     * Do a simple page curl effect
     */
    private void doSimpleCurl() {
        int width = mContainer.getWidth();
        int height = mContainer.getHeight();

        // Calculate point A
        mA.x = width - mMovement.x;
        mA.y = height;

        // Calculate point D
        mD.x = 0;
        mD.y = 0;
        if (mA.x > width / 2) {
            mD.x = width;
            mD.y = height - (width - mA.x) * height / mA.x;
        } else {
            mD.x = 2 * mA.x;
            mD.y = 0;
        }

        // Now calculate E and F taking into account that the line
        // AD is perpendicular to FB and EC. B and C are fixed points.
        double angle = Math.atan((height - mD.y) / (mD.x + mMovement.x - width));
        double _cos = Math.cos(2 * angle);
        double _sin = Math.sin(2 * angle);

        // And get F
        mF.x = (float) (width - mMovement.x + _cos * mMovement.x);
        mF.y = (float) (height - _sin * mMovement.x);

        // If the x position of A is above half of the page we are still not
        // folding the upper-right edge and so E and D are equal.
        if (mA.x > width / 2) {
            mE.x = mD.x;
            mE.y = mD.y;
        }
        else
        {
            // So get E
            mE.x = (float) (mD.x + _cos * (width - mD.x));
            mE.y = (float) -(_sin * (width - mD.x));
        }
    }

    /**
     * Calculate the dynamic effect, that one that follows the users finger
     */
    private void doDynamicCurl() {
        int width = mContainer.getWidth();
        int height = mContainer.getHeight();

        // F will follow the finger, we add a small displacement
        // So that we can see the edge
        mF.x = width - mMovement.x+0.1f;
        mF.y = height - mMovement.y+0.1f;

        // Set min points
        if(mA.x==0) {
            mF.x= Math.min(mF.x, mOldF.x);
            mF.y= Math.max(mF.y, mOldF.y);
        }

        // Get diffs
        float deltaX = width-mF.x;
        float deltaY = height-mF.y;

        float BH = (float) (Math.sqrt(deltaX * deltaX + deltaY * deltaY) / 2);
        double tangAlpha = deltaY / deltaX;
        double alpha = Math.atan(deltaY / deltaX);
        double _cos = Math.cos(alpha);
        double _sin = Math.sin(alpha);

        mA.x = (float) (width - (BH / _cos));
        mA.y = height;

        mD.y = (float) (height - (BH / _sin));
        mD.x = width;

        mA.x = Math.max(0,mA.x);
        if(mA.x==0) {
            mOldF.x = mF.x;
            mOldF.y = mF.y;
        }

        // Get W
        mE.x = mD.x;
        mE.y = mD.y;

        // Correct
        if (mD.y < 0) {
            mD.x = width + (float) (tangAlpha * mD.y);
            mE.y = 0;
            mE.x = width + (float) (Math.tan(2 * alpha) * mD.y);
        }
    }


    /**
     * Swap to next view
     */
    private void nextView() {
        int foreIndex = mIndex + 1;
        if(foreIndex >= mPages.size()) {
            foreIndex = 0;
        }
        int backIndex = foreIndex + 1;
        if(backIndex >= mPages.size()) {
            backIndex = 0;
        }
        mIndex = foreIndex;
        setViews(foreIndex, backIndex);
    }

    /**
     * Swap to previous view
     */
    private void previousView() {
        int backIndex = mIndex;
        int foreIndex = backIndex - 1;
        if(foreIndex < 0) {
            foreIndex = mPages.size()-1;
        }
        mIndex = foreIndex;
        setViews(foreIndex, backIndex);
    }

    private void setViews(int foreIndex, int backIndex) {

    }

    public boolean IsCurlModeDynamic()
    {
        return mCurlMode == CURLMODE_DYNAMIC;
    }

    private Vector2D CapMovement(Vector2D point, boolean bMaintainMoveDir)
    {
        // Make sure we never ever move too much
        if (point.distance(mOrigin) > mFlipRadius)
        {
            if ( bMaintainMoveDir )
            {
                // Maintain the direction
                point = mOrigin.sum(point.sub(mOrigin).normalize().mult(mFlipRadius));
            }
            else
            {
                // Change direction
                if ( point.x > (mOrigin.x+mFlipRadius))
                    point.x = (mOrigin.x+mFlipRadius);
                else if ( point.x < (mOrigin.x-mFlipRadius) )
                    point.x = (mOrigin.x-mFlipRadius);
                point.y = (float) (Math.sin(Math.acos(Math.abs(point.x-mOrigin.x)/mFlipRadius))*mFlipRadius);
            }
        }
        return point;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!bBlockTouchInput) {

            // Get our finger position
            mFinger.x = event.getX();
            mFinger.y = event.getY();
            int width = mContainer.getWidth();

            // Depending on the action do what we need to
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mOldMovement.x = mFinger.x;
                    mOldMovement.y = mFinger.y;

                    // If we moved over the half of the display flip to next
                    if (mOldMovement.x > (width >> 1)) {
                        mMovement.x = mInitialEdgeOffset;
                        mMovement.y = mInitialEdgeOffset;

                        // Set the right movement flag
                        bFlipRight = true;
                    } else {
                        // Set the left movement flag
                        bFlipRight = false;

                        // go to next previous page
                        previousView();

                        // Set new movement
                        mMovement.x = IsCurlModeDynamic()?width<<1:width;
                        mMovement.y = mInitialEdgeOffset;
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    bUserMoves=false;
                    bFlipping=true;
                    FlipAnimationStep();
                    break;
                case MotionEvent.ACTION_MOVE:
                    bUserMoves=true;

                    // Get movement
                    mMovement.x -= mFinger.x - mOldMovement.x;
                    mMovement.y -= mFinger.y - mOldMovement.y;
                    mMovement = CapMovement(mMovement, true);

                    // Make sure the y value get's locked at a nice level
                    if ( mMovement.y  <= 1 )
                        mMovement.y = 1;

                    // Get movement direction
                    if (mFinger.x < mOldMovement.x ) {
                        bFlipRight = true;
                    } else {
                        bFlipRight = false;
                    }

                    // Save old movement values
                    mOldMovement.x  = mFinger.x;
                    mOldMovement.y  = mFinger.y;

                    // Force a new draw call
                    DoPageCurl();
                    mContainer.invalidate();
                    break;
            }

        }

        return true;
    }


    /**
     * Reset points to it's initial clip edge state
     */

    public void ResetClipEdge()
    {
        // Set our base movement
        mMovement.x = mInitialEdgeOffset;
        mMovement.y = mInitialEdgeOffset;
        mOldMovement.x = 0;
        mOldMovement.y = 0;

        // Now set the points
        // TODO: OK, those points MUST come from our measures and
        // the actual bounds of the view!
        mA = new Vector2D(mInitialEdgeOffset, 0);
        mB = new Vector2D(mContainer.getWidth(), mContainer.getHeight());
        mC = new Vector2D(mContainer.getWidth(), 0);
        mD = new Vector2D(0, 0);
        mE = new Vector2D(0, 0);
        mF = new Vector2D(0, 0);
        mOldF = new Vector2D(0, 0);

        // The movement origin point
        mOrigin = new Vector2D(mContainer.getWidth(), 0);
    }


    public void drawFinished() {
        if (bEnableInputAfterDraw) {
            bBlockTouchInput=false;
            recycleCaches();
        }
    }

    // TODO: extract to interface
    public boolean dispatchDraw(Canvas canvas) {
        if (bUserMoves||bFlipping) {

            drawFinished();
            return true;
        }
        return false;
    }

    private void obtainCaches() {

    }

    private void recycleCaches() {

    }

    /**
     * Inner class used to represent a 2D point.
     */


    private class Vector2D
    {
        public float x,y;
        public Vector2D(float x, float y)
        {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            // TODO Auto-generated method stub
            return "("+this.x+","+this.y+")";
        }

        public float length() {
            return (float) Math.sqrt(x * x + y * y);
        }

        public float lengthSquared() {
            return (x * x) + (y * y);
        }

        public boolean equals(Object o) {
            if (o instanceof Vector2D) {
                Vector2D p = (Vector2D) o;
                return p.x == x && p.y == y;
            }
            return false;
        }

        public Vector2D reverse() {
            return new Vector2D(-x,-y);
        }

        public Vector2D sum(Vector2D b) {
            return new Vector2D(x+b.x,y+b.y);
        }

        public Vector2D sub(Vector2D b) {
            return new Vector2D(x-b.x,y-b.y);
        }

        public float dot(Vector2D vec) {
            return (x * vec.x) + (y * vec.y);
        }

        public float cross(Vector2D a, Vector2D b) {
            return a.cross(b);
        }

        public float cross(Vector2D vec) {
            return x * vec.y - y * vec.x;
        }

        public float distanceSquared(Vector2D other) {
            float dx = other.x - x;
            float dy = other.y - y;

            return (dx * dx) + (dy * dy);
        }

        public float distance(Vector2D other) {
            return (float) Math.sqrt(distanceSquared(other));
        }

        public float dotProduct(Vector2D other) {
            return other.x * x + other.y * y;
        }

        public Vector2D normalize() {
            float magnitude = (float) Math.sqrt(dotProduct(this));
            return new Vector2D(x / magnitude, y / magnitude);
        }

        public Vector2D mult(float scalar) {
            return new Vector2D(x*scalar,y*scalar);
        }
    }
}
