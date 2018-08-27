package jp.shopping_app.seicomart;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import jp.shopping_app.seicomart.util.AlertDialogFragment;
import jp.shopping_app.seicomart.util.M003MenuController;
import jp.shopping_app.seicomart.util.M004ModalController;
import jp.shopping_app.seicomart.util.TitleTabActivity;

public class ScannerPage extends TitleTabActivity implements SurfaceHolder.Callback, View.OnClickListener, Camera.AutoFocusCallback, Camera.PreviewCallback, AlertDialogFragment.Delegate {

    // region static variable
    //This FLAG is to receive result on other Activity which start this by command "startActivityForResult"
    public final static String BARCODE_RESULT = "barcode_result";
    private final static String BARCODE_FORMAT_DIALOG = "scanner_barcode_format_dialog";

    public final static int CAMERA_ORIENTATION = 90;

    private final static int MIN_WIDTH_RECOMMEND = 500;
    private final static int MAX_WIDTH_RECOMMEND = 1920;
    // endregion

    //region Define View
    private FrameLayout scanPanel;
    private LinearLayout cameraPanel;
    private SurfaceView mSurfaceView;
    //endregion

    //region member variable
    private Camera mCamera;
    private HandlerThread mHandlerThread;
    private DecodeHandler mDecodeHandler;
    private Camera.Size mPreviewSize;
    private Camera.Size mPictureSize;
    //endregion

    //region Set Page Title
    @Override
    protected CharSequence getPageTitle() {
        return getResources().getString(R.string.titleAP102b);
    }
    //endregion


    //region Activity methods override
    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scanner_page);
        // Display the close button in the title.
        M003MenuController.setBackButton(this, M003MenuController.TitleBackType.CLOSE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishScanner(Activity.RESULT_CANCELED, null);
            }
        });
        // Hide the setting button of the title.
        M003MenuController.showSettingButton(this, false, null);

        initView();

        //Override animation show and hide this window
        this.overridePendingTransition(R.anim.animation_enter_from_bottom,
                R.anim.animation_leave_to_top);


    }

    private void onReciveiBarcode(Result result) {

    }

    MultiFormatReader multiFormatReader;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onResume() {
        super.onResume();

        //Handler to post frames to other thread which will detect and recognize barcode
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }
        mHandlerThread = new HandlerThread("AP102ScannerPageHandlerThread");
        mHandlerThread.start();
        mDecodeHandler = new DecodeHandler(mHandlerThread.getLooper(), new Handler() {

            public void handleMessage(Message msg) {
                if (mCamera != null) {
                    if (msg.obj == null) {
                        mCamera.setOneShotPreviewCallback(ScannerPage.this);
                    } else {
                        // Toast.makeText(ScannerPage.this, String.valueOf(msg.obj), Toast.LENGTH_SHORT).show();
                        Result result = (Result) msg.obj;
                        int cardType = AP102EntryPage.CARD_TYPE_FLAG;
                        String barcodeResult = result.getText();
                        if ((result.getBarcodeFormat() == BarcodeFormat.EAN_13 && cardType == AP102EntryPage.CARD_TYPE_CLUBCARD && barcodeResult.length() == 13)
                                || (result.getBarcodeFormat() == BarcodeFormat.CODE_128 && cardType == AP102EntryPage.CARD_TYPE_PECOMA) && barcodeResult.length() == 20) {
                            finishScanner(Activity.RESULT_OK, barcodeResult);
                        } else {
                            mCamera.setPreviewCallback(null);
                            //And any Runnable are on Message queue will be remove.
                            removeCallbacksAndMessages(null);
                            M004ModalController.showMessage(ScannerPage.this, BARCODE_FORMAT_DIALOG,
                                    getString(R.string.scanner_page_format_alert_message), true, true);
                        }
                    }
                    ///
                }
            }
        });


        if (mCamera != null) {
            try {
                mCamera.reconnect();
            } catch (Exception e) {

            }
        } else {
            //Add camera and it callback after Preview panel has been render
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setUpCamera();
                }
            }, 100);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
        }
        if (mSurfaceView != null) {
            mSurfaceView.getHolder().removeCallback(ScannerPage.this);
        }

        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }

        this.overridePendingTransition(R.anim.animation_enter_from_top,
                R.anim.animation_leave_to_bottom);
    }
    //endregion

    //region  Camera.AutoFocusCallback implement
    @Override
    public void onAutoFocus(boolean success, Camera camera) {

    }
    //endregion

    //region  SurfaceHolder.Callback implement
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        //Start handlerThread when surface create
        //Bellow step to determine status of handler thread
        if (mHandlerThread != null && mHandlerThread.getState() != Thread.State.RUNNABLE) {
            mHandlerThread.quit();
            mHandlerThread.start();
        }

        try {
            // Camera Open
            if (mCamera == null) {
                return;
            }
            mCamera.setPreviewDisplay(holder);
            mCamera.setOneShotPreviewCallback(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //Stop handlerThread when this activity destroy, if not, application can be crashed
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
    }
    //endregion

    //region View.OnClickListener implement
    @Override
    public void onClick(View v) {
        if (mCamera != null) {
            try {
                mCamera.autoFocus(this);
            } catch (Exception e) {

            }
        }
    }

    //endregion
    private static final String TAG = ScannerPage.class.getSimpleName();
    long time;
    int i = 1;

    //region  Camera.PreviewCallback implement
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mDecodeHandler != null)
            mDecodeHandler.obtainMessage(1, mPreviewSize.width, mPreviewSize.height, data).sendToTarget();


//        //Bellow steps is to parse byte array data to bitmap
//        //the process is like this : byte array -> YuvImage -> (by outputStream) Bitmap -> BinaryBitmap(in )
//        time = System.currentTimeMillis();
//        Camera.Parameters parameters = camera.getParameters();
//        Camera.Size size = parameters.getPreviewSize();
//        YuvImage yuvImage = new YuvImage(data, parameters.getPreviewFormat(),
//                size.width, size.height, null);
//        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
//        yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 100, outStream);
//
//        byte[] imageBytes = outStream.toByteArray();
//        final Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//
//        try {
//            outStream.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        Matrix matrix = new Matrix();
//        matrix.postRotate(CAMERA_ORIENTATION);
//        final Bitmap rotatedBitmap = Bitmap.createBitmap(image , 0, 0, image .getWidth(), image .getHeight(), matrix, true);
//
//        //Bitmap data should be recycled so that GC is able to retrieve memory
//        image.recycle();
//        Log.e(TAG, String.format("onPreviewFrame: %s", System.currentTimeMillis()-time));
//        //bitmap data will be posted to handlerThread.
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                time = System.currentTimeMillis();
//                final Result barcodeResult = readBarcodeBitmap(rotatedBitmap);
//                Log.e(TAG, String.format("onPreviewFrame: %s", System.currentTimeMillis()-time));
//                Log.e(TAG, String.format("run: %s",barcodeResult));
//                if (barcodeResult != null && barcodeResult.getBarcodeFormat() == BarcodeFormat.EAN_13){
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            finishScanner(Activity.RESULT_OK, barcodeResult.getText());
//                        }
//                    });
//                } else if (barcodeResult != null && barcodeResult.getBarcodeFormat() != BarcodeFormat.EAN_13) {
//                    mCamera.setPreviewCallback(null);
//                    //And any Runnable are on Message queue will be remove.
//                    mHandler.removeCallbacksAndMessages(null);
//
//                    M004ModalController.showMessage(ScannerPage.this, BARCODE_FORMAT_DIALOG,
//                            getString(R.string.scanner_page_format_alert_message), true, true);
//                }
//            }
//        });
    }
    //endregion

    //region private method

    /**
     * @param bMap This Bitmap which contain barcode, barcode should be in center of the bitmap to improve speed of process and be prioritized.
     * @return String : Returned data will be null if the recognition process fail.
     */
    private Result readBarcodeBitmap(Bitmap bMap) {
//        String contents = null;
        Result contents = null;
        int[] intArray = new int[bMap.getWidth() * bMap.getHeight()];
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());

        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Reader reader = new MultiFormatReader();
        try {
            contents = reader.decode(bitmap);
        } catch (NotFoundException e) {
//            e.printStackTrace();
        } catch (ChecksumException e) {
//            e.printStackTrace();
        } catch (FormatException e) {
//            e.printStackTrace();
        } catch (Exception e) {
//            e.printStackTrace();
        } finally {
            bMap.recycle();
        }
        return contents;
    }

    /**
     * @param activityResult :
     * @param barcodeResult
     */
    private void finishScanner(int activityResult, String barcodeResult) {
        mCamera.setPreviewCallback(null);
        //And any Runnable are on Message queue will be remove.
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
        }
        if (mSurfaceView != null) {
            mSurfaceView.getHolder().removeCallback(ScannerPage.this);
        }

        //return the result with intent
        Intent intent = new Intent();

        if (barcodeResult != null) intent.putExtra(BARCODE_RESULT, barcodeResult);

        setResult(activityResult, intent);
        finish();
    }

    @Override
    public void alertDidPositive(AlertDialogFragment fragment, DialogInterface dialog) {
        String tag = fragment.getTag();
        if (tag.equals(BARCODE_FORMAT_DIALOG)) {
            mCamera.setPreviewCallback(this);
        }
    }

    /**
     * Check if this device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    /**
     *
     */
    private void setUpCamera() {

        if (cameraPanel != null && mSurfaceView != null) {
            cameraPanel.removeView(mSurfaceView);
        }

        mSurfaceView = new SurfaceView(getApplicationContext());

        if (mCamera == null) {
            mCamera = getCameraInstance();
        }

        int numCameras = Camera.getNumberOfCameras();
        int back_camera_flg = 0;
        int index = 0;
        Camera.CameraInfo cameraInfo;
        int result = 0;

        while (index < numCameras) {
            cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && back_camera_flg == 0) {
                result = (cameraInfo.orientation) % 360;
                result = (360 - result) % 360;
            }

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                back_camera_flg = 1;
                result = (cameraInfo.orientation + 360) % 360;
                break;
            }
            index++;
        }
        mCamera.setDisplayOrientation(result);

        Camera.Parameters parameters = mCamera.getParameters();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        // parameters.setPreviewFormat(ImageFormat.NV21);
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        mPreviewSize = previewSizes.get(0);
        for (Camera.Size determineSize : previewSizes) {
            if (MIN_WIDTH_RECOMMEND <= determineSize.width && determineSize.width <= MAX_WIDTH_RECOMMEND) {
                mPreviewSize = determineSize;
                break;
            }
        }

        List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
        mPictureSize = pictureSizes.get(0);
        for (Camera.Size determineSize : pictureSizes) {
            if (MIN_WIDTH_RECOMMEND <= determineSize.width && determineSize.width <= MAX_WIDTH_RECOMMEND) {
                mPictureSize = determineSize;
                break;
            }
        }

        if (mPreviewSize != null) {
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        }
        if (mPictureSize != null) {
            parameters.setPictureSize(mPictureSize.width, mPictureSize.height);
        }

        mCamera.setParameters(parameters);

        double ratio = (double) mPreviewSize.height / mPreviewSize.width;

        int width = (int) (cameraPanel.getHeight() * ratio);
        int height = cameraPanel.getHeight();
        if (width < cameraPanel.getWidth()) {
            height = (int) (cameraPanel.getWidth() / ratio);
            width = cameraPanel.getWidth();
        }

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
        mSurfaceView.setLayoutParams(lp);
        cameraPanel.addView(mSurfaceView);
        mSurfaceView.getHolder().addCallback(ScannerPage.this);
    }

    private static int mScanHeight;

    private void initView() {
        // Mapping View
        cameraPanel = (LinearLayout) findViewById(R.id.cameraPanel);
        scanPanel = (FrameLayout) findViewById(R.id.scanPanel);
        //Set on Click listener for tapping autofocus
        cameraPanel.setOnClickListener(this);

        //Adjust height of preview area
        //Height of preview area is equal to width of screen, or this area will be a square
        //Top and bottom overlay panels will be same dimension each other.
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.widthPixels * 17 / 32;
        mScanHeight = height;
        LinearLayout linearLayout = new LinearLayout(getApplicationContext());
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
        linearLayout.setAlpha(0.0f);
        scanPanel.addView(linearLayout);

        TextView tvTopText = (TextView) findViewById(R.id.tv_top_text);
        if (AP102EntryPage.CARD_TYPE_FLAG == AP102EntryPage.CARD_TYPE_PECOMA)
            tvTopText.setText(getString(R.string.barcodeReadingTopMessage_precoma_card));
        else
            tvTopText.setText(getString(R.string.barcodeReadingTopMessage_club_card));
    }

    static byte[] rotate(final byte[] yuv, final int width, final int height, final int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }
        final byte[] output = new byte[yuv.length];
        final int frameSize = width * height;
        final boolean swap = rotation % 180 != 0;
        final boolean xflip = rotation % 270 != 0;
        final boolean yflip = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;
                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }

        return output;
    }

    private static class DecodeHandler extends Handler {
        private Map<DecodeHintType, Object> hints;
        private MultiFormatReader multiFormatReader;
        private Handler mUIHandler;
        private Result rawResult;

        public DecodeHandler(Looper looper, Handler handler) {
            super(looper);

            hints = new EnumMap<>(DecodeHintType.class);
            Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
            decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
            multiFormatReader = new MultiFormatReader();
            multiFormatReader.setHints(hints);

            mUIHandler = handler;
        }

        @Override
        public void handleMessage(Message msg) {
            int previewWith = msg.arg1; //1920
            int previewHeight = msg.arg2;//1080
            byte[] rotate = rotate((byte[]) msg.obj, previewWith, previewHeight, 90);

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(rotate, previewHeight,
                    previewWith, 0, (previewWith - mScanHeight) / 2,
                    previewHeight, mScanHeight, false);

            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            rawResult = null;
            try {
                rawResult = multiFormatReader.decodeWithState(bitmap);

            } catch (Exception e) {
                e.printStackTrace();

            } finally {
                multiFormatReader.reset();
            }
            Message message = new Message();
            message.obj = rawResult;
            message.what = 1;
            mUIHandler.sendMessage(message);
        }
    }

    //endregion
}
