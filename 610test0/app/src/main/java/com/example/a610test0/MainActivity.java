package com.example.a610test0;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.MediaStore;
import android.content.ContentUris;
import android.database.Cursor;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    final int FILE_SELECT_CODE = 0;
    private static final String TAG1 = "FileChoose";
    private static int PERMISSION_READ_EX = 1;
    private static int PERMISSION_WRITE_EX = 2;
    private boolean load_result = false;
    private Interpreter tflite = null;
    private List<String> resultLabel = new ArrayList<>();
    private int[] ddims = {1, 3, 224, 224};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button select_button = (Button)findViewById(R.id.button);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_READ_EX);
        }
        else
        {
            Toast.makeText(getApplicationContext(),"已获得读取权限",Toast.LENGTH_SHORT).show();
        }

        readCacheLabelFromLocalFile();
        load_model("mobilenet_v1_he");

        select_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, FILE_SELECT_CODE);
            }
        });
    }


    public static String getPath(final Context context, final Uri uri) {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

   @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data)
   {
       ImageView show_image = (ImageView)findViewById(R.id.img_view);
       TextView path_textview = (TextView)findViewById(R.id.textView1);
       RequestOptions options = new RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE);
       if(resultCode == Activity.RESULT_OK)
       {
           if(requestCode == FILE_SELECT_CODE)
           {
               Uri uri = data.getData();
               String spath = null;
               spath = getPath(this,uri);
               path_textview.setText(spath);
               if(load_result)
               {
                    Glide.with(MainActivity.this).load(uri).apply(options).into(show_image);
                   System.out.println("开始预测了哦！！！！！！！！！！！！！！！！！！");
                    predict_image(spath);
                   System.out.println("结束预测了哦！！！！！！！！！！！！！！！！！！");
               }
               else
               {
                   Toast.makeText(getApplicationContext(),"未正确载入模型",Toast.LENGTH_SHORT).show();
               }
           }
       }
       else
       {
           Log.e(TAG1,"onActivityResult() error,resultCode:"+resultCode);
       }
       super.onActivityResult(requestCode,resultCode,data);
   }

    //memory-map the model file in assets
    private MappedByteBuffer loadModelFile(String model) throws IOException {
        AssetFileDescriptor fileDescriptor = getApplicationContext().getAssets().openFd(model + ".tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //load infer model
    private void load_model(String model) {
        try {
            tflite = new Interpreter(loadModelFile(model));
            Toast.makeText(MainActivity.this, model + " model load success", Toast.LENGTH_SHORT).show();
            tflite.setNumThreads(4);
            load_result = true;
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, model + " model load fail", Toast.LENGTH_SHORT).show();
            load_result = false;
            e.printStackTrace();
        }
    }

    private void readCacheLabelFromLocalFile() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("labels.txt")));
            String readLine = null;
            while ((readLine = reader.readLine()) != null) {
                resultLabel.add(readLine);
            }
            reader.close();
        } catch (Exception e) {
            Log.e("labelCache", "error " + e);
        }
    }

    private void predict_image(String image_path) {
        // picture to float array
        TextView pre_cate = (TextView)findViewById(R.id.textView_cate);
        TextView pre_poss = (TextView)findViewById(R.id.textView_poss);

        Bitmap bmp = PhotoUtil.getScaleBitmap(image_path);
        ByteBuffer inputData = PhotoUtil.getScaledMatrix(bmp, ddims);

        try {
            // Data format conversion takes too long
            // Log.d("inputData", Arrays.toString(inputData));
            float[][] labelProbArray = new float[1][1001];
            tflite.run(inputData, labelProbArray);
            float[] results = new float[labelProbArray[0].length];
            System.arraycopy(labelProbArray[0], 0, results, 0, labelProbArray[0].length);
            // show predict result and time
            int r = get_max_result(results);
            pre_cate.setText(resultLabel.get(r));
            pre_poss.setText(Float.toString(results[r]));
            //String show_text = "result：" + r + "\nname：" + resultLabel.get(r) + "\nprobability：" + results[r] + "\ntime：" + time + "ms";
            //result_text.setText(show_text);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("出错了哦line279!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private int get_max_result(float[] result) {
            float probability = result[0];
            int r = 0;
            for (int i = 0; i < result.length; i++) {
                if (probability < result[i]) {
                    probability = result[i];
                    r = i;
                }
            }
            return r;
        }

}