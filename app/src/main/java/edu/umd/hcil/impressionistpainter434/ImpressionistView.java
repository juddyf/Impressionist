package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.ImageView;

import java.text.MessageFormat;
import java.util.Random;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private int _defaultRadius = 25;
    private Point _lastPoint = null;
    private long _lastPointTime = -1;
    private boolean _useMotionSpeedForBrushStrokeSize = true;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private float _minBrushRadius = 5;
    private Bitmap _imageViewBitmap;
    private VelocityTracker _velocityTracker = null;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(40);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);

    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView) {
        _imageView = imageView;
        _imageViewBitmap = imageView.getDrawingCache();
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType) {
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        _offScreenBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        _offScreenCanvas = new Canvas(_offScreenBitmap);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        _imageViewBitmap = _imageView.getDrawingCache();

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        if (_imageViewBitmap == null) {
            return false;
        }

        float touchX = motionEvent.getX();
        float touchY = motionEvent.getY();

        Rect bitmapPosition = getBitmapPositionInsideImageView(_imageView);

        if (touchX < bitmapPosition.left) touchX = bitmapPosition.left;
        if (touchY < bitmapPosition.top) touchY = bitmapPosition.top;
        if (touchX >= bitmapPosition.right) touchX = bitmapPosition.right - 1;
        if (touchY >= bitmapPosition.bottom) touchY = bitmapPosition.bottom - 1;

        int pixel = _imageViewBitmap.getPixel((int) touchX, (int) touchY);

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //set up velocity tracker
                if(_velocityTracker == null) {
                    _velocityTracker = VelocityTracker.obtain();
                }
                else {
                    _velocityTracker.clear();
                }
                _velocityTracker.addMovement(motionEvent);

                _paint.setColor(pixel);

                break;
            case MotionEvent.ACTION_MOVE:
                _paint.setColor(pixel);
                int historySize = motionEvent.getHistorySize();

                for (int i = 0; i < historySize; i++) {

                    float histX = motionEvent.getHistoricalX(i);
                    float histY = motionEvent.getHistoricalY(i);

                    drawShape(touchX, touchY, motionEvent);
                }

                drawShape(touchX, touchY, motionEvent);

                break;
            case MotionEvent.ACTION_UP:
                break;
        }

        invalidate();
        return true;
    }




    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }

    private void drawShape(float touchX, float touchY, MotionEvent motionEvent) {
        float radius = _paint.getStrokeWidth();

        if (_brushType == BrushType.Circle) {
            _offScreenCanvas.drawCircle(touchX, touchY, radius, _paint);
        } else if (_brushType == BrushType.Square) {
            radius /= 2;
            _offScreenCanvas.drawRect(touchX, touchY, touchX + radius, touchY + radius, _paint);
        } else if (_brushType == BrushType.CircleSplatter) {
            Random r = new Random();
            _velocityTracker.addMovement(motionEvent);

            int index = motionEvent.getActionIndex();
            int pointerId = motionEvent.getPointerId(index);
            _velocityTracker.computeCurrentVelocity(1000, 500);

            int xSpread = Math.abs((int) _velocityTracker.getXVelocity(pointerId)) / 5 + 1;
            int ySpread = Math.abs((int) _velocityTracker.getYVelocity(pointerId)) / 5 + 1;

            for (int i = 0; i < 5; i++) {
                //subtract half so we have some negative scatter as well
                int xPos = r.nextInt(xSpread);
                xPos -= (xSpread / 2);
                int yPos = r.nextInt(ySpread) - (ySpread / 2);

                _offScreenCanvas.drawCircle(touchX + xPos, touchY + yPos, 2, _paint);
            }

        }
    }

    public void drawStep() {
        Rect bitmapPosition = getBitmapPositionInsideImageView(_imageView);

        Random random = new Random();
        int touchX = random.nextInt(bitmapPosition.right - bitmapPosition.left) + bitmapPosition.left;
        int touchY = random.nextInt(bitmapPosition.bottom - bitmapPosition.top) + bitmapPosition.top;

        int pixel = _imageViewBitmap.getPixel((int) touchX, (int) touchY);
        _paint.setColor(pixel);

        _offScreenCanvas.drawCircle(touchX, touchY, 30, _paint);
        invalidate();
    }

    public BrushType getBrushType() {
        return _brushType;
    }

    public Bitmap getBitmap() {
        return _offScreenBitmap;
    }
}

