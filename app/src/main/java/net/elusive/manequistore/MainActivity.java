package net.elusive.manequistore;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int SELECT_PICTURE = 1;
    private static final String TAG = "MainActivity";

    private String mSelectedImagePath;
    private Mat mOriginalImage;
    private Mat mSampledImage;
    private ImageView mImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.image);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public void onResume(){
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mBaseLoaderCallBack);
    }

    private BaseLoaderCallback mBaseLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }

        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_OpenGallery){
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,"Select Picture"),SELECT_PICTURE);
            return true;
        }else if(id == R.id.action_harris){
            if (mSampledImage == null){
                Toast.makeText(getApplicationContext(),getString(R.string.toast_must_load_image),Toast.LENGTH_SHORT).show();
                return true;
            }

            Mat greyImage = new Mat();
            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            Imgproc.cvtColor(mSampledImage, greyImage, Imgproc.COLOR_RGB2GRAY);

            FeatureDetector detector = FeatureDetector.create(FeatureDetector.HARRIS);
            detector.detect(greyImage, keyPoints);
            Features2d.drawKeypoints(greyImage, keyPoints, greyImage);
            displayImage(greyImage);
            return true;
        }else if(id == R.id.action_line_crop){
            if (mSampledImage == null){
                Toast.makeText(getApplicationContext(),getString(R.string.toast_must_load_image),Toast.LENGTH_SHORT).show();
                return true;
            }

            Mat gray = new Mat();
            Imgproc.cvtColor(mSampledImage, gray, Imgproc.COLOR_RGB2GRAY);

            Mat edgeImage = new Mat();
            Imgproc.Canny(gray, edgeImage, 100, 200);

            // Dilate
            Mat dilated = new Mat();
            Mat kernel;
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(3, 3));
            Imgproc.dilate(edgeImage, dilated, kernel);

            /* estas usando este ejemplo
            https://github.com/LowWeiLin/OpenCV_ImageBackgroundRemoval/blob/master/OpenCV_ImageBackgroundRemoval/main.cpp

            el manual del flodfill
            http://docs.opencv.org/2.4/modules/imgproc/doc/miscellaneous_transformations.html

            Me parece que el problema es que la mascara esta quedando toda igual, fijate en el manual que dice que el flood fill no puede ir por cierto tipo de pixeles.
            De todas formas la llamada a flood fill que estas haciendo ahora esta distinta a la del ejemplo.
             */

            // Flod fill
            Mat flodFill = Mat.zeros(dilated.rows()+2,dilated.cols()+2, CvType.CV_8UC1);
            Imgproc.floodFill(dilated,flodFill,new Point(0,0),new Scalar(0,0,255));

            displayImage(flodFill);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK){
            if (requestCode == SELECT_PICTURE){
                Uri selectedImageUri = data.getData();
                mSelectedImagePath = getRealPathFromURI_API19(selectedImageUri);
                Log.i(TAG, "Selected image path" + mSelectedImagePath);
                loadImage(mSelectedImagePath);
                displayImage(mSampledImage);

            }
        }
    }

    public String getRealPathFromURI_API19(Uri uri){
        String filePath = "";
        String wholeID = DocumentsContract.getDocumentId(uri);

        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        String[] column = { MediaStore.Images.Media.DATA };

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, new String[]{id}, null);

        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        }
        cursor.close();
        return filePath;
    }

    private void loadImage(String path){
        mOriginalImage = Imgcodecs.imread(path);
        Mat rgbImage = new Mat();

        Imgproc.cvtColor(mOriginalImage, rgbImage, Imgproc.COLOR_BGR2RGB);
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;
        mSampledImage = new Mat();

        double downSampleRatio = calculateSubSampleSize(rgbImage, width, height);
        Imgproc.resize(rgbImage, mSampledImage, new Size(), downSampleRatio, downSampleRatio, Imgproc.INTER_AREA);
        try {
            ExifInterface exif = new ExifInterface(mSelectedImagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
            switch (orientation){
                case ExifInterface.ORIENTATION_ROTATE_90:
                    //get the mirrored image
                    mSampledImage = mSampledImage.t();
                    //flip the y-axis
                    Core.flip(mSampledImage, mSampledImage, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    //get the side up image
                    mSampledImage = mSampledImage.t();
                    //flip on the x-axis
                    Core.flip(mSampledImage,mSampledImage,0);
                    break;
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    private static double calculateSubSampleSize(Mat srcImage, int reqWidth, int reqHeight){
        //Raw Width and Height of image
        final int height = srcImage.height();
        final int width = srcImage.width();
        double inSampleSize = 1;

        if(height > reqHeight || width > reqWidth){
            //calculate ratios of requested height and width to the raw height and width
            final double heightRatio = (double) reqHeight / (double) height;
            final double widthRatio = (double) reqWidth / (double) width;
            //chose the smallest ratios as inSampleSize value, this will guarrantee final imagen with both dimensions lager than or equal to the requested dimensions
            inSampleSize = heightRatio<widthRatio ? heightRatio :widthRatio;
        }
        return inSampleSize;
    }

    private void displayImage(Mat image){
        //create bitmap
        Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.RGB_565);
        //convert to bitmap
        Utils.matToBitmap(image, bitmap);
        mImageView.setImageBitmap(bitmap);
    }

}
