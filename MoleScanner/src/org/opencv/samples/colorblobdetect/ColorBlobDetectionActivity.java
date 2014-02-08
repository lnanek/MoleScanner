package org.opencv.samples.colorblobdetect;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;

import java.util.LinkedList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.samples.colorblobdetectnt.R;

public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
        
        Log.i(TAG, "OpenCV loaded successfully");
        mOpenCvCameraView.enableView();
        mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);

    }
    
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }
    

    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    public boolean selectTouchedColor(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }
    
    public void selectCenterColor() {
        int cols = mRgba.cols();
        int rows = mRgba.rows();
        final int centerRow = mRgba.rows() / 2;
        final int centerCol = mRgba.cols() / 2;

        int x = centerCol;
        int y = centerRow;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();
    }    

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        
        selectCenterColor();
        
        final int screenCenterY = mRgba.rows() / 2;
        final int screenCenterX = mRgba.cols() / 2;
        // TODO don't log in draw loop for performance
        Log.i(TAG, "Screen Center: " + screenCenterX + ", " + screenCenterY);

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            
            
            // Vertical neon green line across screen
            Core.line(mRgba, 
                    new Point(0, mRgba.rows() / 2), 
                    new Point(mRgba.cols(), mRgba.rows() / 2), 
                    new Scalar(0, 255, 0, 255), 
                    3);

            // Horizontal neon green line across screen
            Core.line(mRgba, 
                    new Point(mRgba.cols() / 2, 0), 
                    new Point(mRgba.cols() / 2, mRgba.rows()), 
                    new Scalar(0, 255, 0, 255), 
                    3);
            
            
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
            
            MatOfPoint centerContour = null;
            // For each contour
            for ( MatOfPoint contour : contours ) {
                // Count how much in each quadrant of screen
                int countUpperLeft = 0;
                int countLowerLeft = 0;
                int countUpperRight = 0;
                int countLowerRight = 0;
                Double maxX = null, minX = null, maxY = null, minY = null;
                for( Point p : contour.toList() ) {
                    
                    Log.i(TAG, "Countour point: " + p.x + ", " + p.y);
                    
                    if ( p.x < screenCenterX && p.y < screenCenterY ) {
                        countLowerLeft++;
                    } else if ( p.x < screenCenterX && p.y >= screenCenterY ) {
                        countUpperLeft++;
                    } else if ( p.x >= screenCenterX && p.y < screenCenterY ) {
                        countLowerRight++;
                    } else if ( p.x >= screenCenterX && p.y >= screenCenterY ) {
                        countUpperRight++;
                    }
                    
                    maxX = null == maxX ? p.x : Math.max(maxX, p.x);
                    maxY = null == maxY ? p.y : Math.max(maxY, p.y);
                    
                    minX = null == minX ? p.x : Math.min(minX, p.x);
                    minY = null == minY ? p.y : Math.min(minY, p.y);
                }
                if ( null == minX || null == minY || null == maxX || null == maxY ) {
                    continue;
                }
                
                // If contour is in all quadrants it is our center contour
                if ( countUpperLeft > 0 &&
                        countUpperRight > 0 &&
                        countLowerLeft > 0  &&
                        countLowerRight > 0 ) {
                    
                    Log.i(TAG, "Found contour across center");
                    centerContour = contour;
                    
                    double contourCenterX = minX + ((maxX - minX) / 2);
                    double contourCenterY = minY + ((maxY - minY) / 2);
                    Log.i(TAG, "Countour center: " + contourCenterX + ", " + contourCenterY);
                    Core.circle(mRgba, 
                            new Point(contourCenterX, contourCenterY), 
                            3, 
                            new Scalar(255, 0, 0, 255), 
                            -1);
                    
                    // TODO don't allocate in draw loop for performance
                    List<MatOfPoint> centerContourList = new LinkedList<MatOfPoint>();
                    centerContourList.add(centerContour);
                    
                    // For now draw it in a new color
                    Imgproc.drawContours(mRgba, centerContourList, -1, new Scalar(0,0,255,255));
                    
                    
                    // Count how much in each quadrant of contour
                    countUpperLeft = 0;
                    countLowerLeft = 0;
                    countUpperRight = 0;
                    countLowerRight = 0;
                    for( Point p : contour.toList() ) {
                        if ( p.x < contourCenterX && p.y < contourCenterY ) {
                            countLowerLeft++;
                        } else if ( p.x < contourCenterX && p.y >= contourCenterY ) {
                            countUpperLeft++;
                        } else if ( p.x >= contourCenterX && p.y < contourCenterY ) {
                            countLowerRight++;
                        } else if ( p.x >= contourCenterX && p.y >= contourCenterY ) {
                            countUpperRight++;
                        }
                    }
                    
                    
                    
                    Core.putText(mRgba, 
                            "UL size: " + countUpperLeft, 
                            new Point(screenCenterX / 2,  screenCenterY * 3 / 2), 
                            Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
                    Core.putText(mRgba, 
                            "UR size: " + countUpperRight, 
                            new Point(screenCenterX * 3 / 2,  screenCenterY * 3 / 2), 
                            Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
                    Core.putText(mRgba, 
                            "LL size: " + countLowerLeft, 
                            new Point(screenCenterX / 2,  screenCenterY / 2), 
                            Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
                    Core.putText(mRgba, 
                            "LR size: " + countLowerRight, 
                            new Point(screenCenterX * 3 / 2,  screenCenterY / 2), 
                            Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
                    
                    final int maxQuadrant = Math.max(Math.max(countUpperLeft, countUpperRight), 
                            Math.max(countLowerLeft, countLowerRight));
                    final int minQuadrant = Math.min(Math.min(countUpperLeft, countUpperRight), 
                            Math.min(countLowerLeft, countLowerRight));
                    final int range = maxQuadrant - minQuadrant;
                    

                    Core.putText(mRgba, 
                            "Difference: " + range, 
                            new Point(screenCenterX,  screenCenterY), 
                            Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
                    
                    
                    break;
                }
            }
            

            if ( null == centerContour ) {

                Core.putText(mRgba, 
                        "Place mole in center", 
                        new Point(screenCenterX,  screenCenterY), 
                        Core.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));                
            }
            

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }
        
        return mRgba;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
