package com.github.devishankar.paint;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.github.devishankar.paint.utils.UploadPicture;
import com.github.devishankar.paint.utils.Utils;
import com.github.devishankar.paint.views.DrawingView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PERMISSION_REQUEST_TO_READ_WRITE_OPERATION_IN_STORAGE = 1;
    private static final int PERMISSION_REQUEST_TO_ACCESS_CAMERA = 2;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int FILE_CHOOSER_REQUEST_CODE = 100;
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 101;
    private static final String APP_KEY = "5z03yneabeo71xw";
    private static final String APP_SECRET = "budku7rupy8qpr4";
    private static final String ACCOUNT_PREFS_NAME = "prefs";
    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";
    private static boolean CMD_UPLOAD = false;
    private static boolean USE_OAUTH1 = false;
    private final String PHOTO_DIR = "/Paint_test/";
    private final int step = 1;
    private final int min = 5;
    private final int max = 20;

    DropboxAPI<AndroidAuthSession> mApi;
    String mPath = Environment.getExternalStorageDirectory().toString() + "/paint/";
    File dir = new File(mPath);
    Context context;

    private DrawingView drawView;
    private ImageButton currPaint, drawBtn, eraseBtn, newBtn, saveBtn, upload, shapes;
    private float smallBrush, mediumBrush, largeBrush;
    private String fileUri = "";
    private Uri cameraFile;
    private String path = "";
    private int seekedSize = 5;

    public static File getOutputMediaFile() {

        // External sdcard location
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Paint");

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Oops! Failed create directory");
                return null;
            }
        }

        // Create a media file name
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + UUID.randomUUID().toString() + ".jpg");

        return mediaFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        context = this;
        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<>(session);

        //get drawing view
        drawView = (DrawingView) findViewById(R.id.drawing);

        //get the palette and first color button
        LinearLayout paintLayout = (LinearLayout) findViewById(R.id.paint_colors);
        currPaint = (ImageButton) paintLayout.getChildAt(0);
        currPaint.setImageDrawable(getResources().getDrawable(R.drawable.paint_pressed));

        //sizes from dimensions
        smallBrush = getResources().getInteger(R.integer.small_size);
        mediumBrush = getResources().getInteger(R.integer.medium_size);
        largeBrush = getResources().getInteger(R.integer.large_size);

        //draw button
        drawBtn = (ImageButton) findViewById(R.id.draw_btn);
        drawBtn.setOnClickListener(this);

        //set initial size
        drawView.setBrushSize(mediumBrush);

        //erase button
        eraseBtn = (ImageButton) findViewById(R.id.erase_btn);
        eraseBtn.setOnClickListener(this);

        //new button
        newBtn = (ImageButton) findViewById(R.id.new_btn);
        newBtn.setOnClickListener(this);

        //save button
        saveBtn = (ImageButton) findViewById(R.id.save_btn);
        saveBtn.setOnClickListener(this);

        upload = (ImageButton) findViewById(R.id.upload);
        upload.setOnClickListener(this);
        disableUploadButton(true);

        shapes = (ImageButton) findViewById(R.id.shapes);
        shapes.setOnClickListener(this);

        dir.mkdir();

        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        CMD_UPLOAD = false;

        if (intent.getData() != null) {
            try {
                fileUri = intent.getData().toString();
                if (fileUri.startsWith("content://")) {
                    Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(Uri.parse(fileUri)));
                    Log.d(TAG, "content data " + intent.getData().toString());
                    drawView.setFile(bitmap);
                    disableUploadButton(true);
                } else if (fileUri.startsWith("file://")) {
                    String temp = fileUri.replace("file://", "");
                    path = temp;
                    Log.d(TAG, "file data " + intent.getData().toString());
                    drawView.setFilePath(temp);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    private void disableUploadButton(boolean enabled) {
        upload.setEnabled(!enabled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AndroidAuthSession session = mApi.getSession();

        // The next part must be inserted in the onResume() method of the
        // activity from which session.startAuthentication() was called, so
        // that Dropbox authentication completes properly.
        Log.d(TAG, "session auto " + session.authenticationSuccessful());
        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();

                // Store it locally in our app for later use
                storeAuth(session);
                if (CMD_UPLOAD)
                    uploadToDrive();
                CMD_UPLOAD = false;
            } catch (IllegalStateException e) {
                Utils.showSnackBar(findViewById(R.id.coord_layout), "Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                Log.i(TAG, "Error authenticating", e);
            }
        }
    }

    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
        // Store the OAuth 1 access token, if there is one.  This is only necessary if
        // you're still using OAuth 1.
        AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
        if (oauth1AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
            edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
            edit.commit();
            return;
        }
    }

    //user clicked paint
    public void paintClicked(View view) {
        //use chosen color

        //set erase false
        drawView.setErase(false);
        drawView.setBrushSize(drawView.getLastBrushSize());

        if (view != currPaint) {
            ImageButton imgView = (ImageButton) view;
            String color = view.getTag().toString();
            drawView.setColor(color);
            //update ui
            imgView.setImageDrawable(getResources().getDrawable(R.drawable.paint_pressed));
            currPaint.setImageDrawable(getResources().getDrawable(R.drawable.paint));
            currPaint = (ImageButton) view;
        }
    }

    @Override
    public void onClick(View view) {

        if (view.getId() == R.id.draw_btn) {
            setBrushSize(1);
        } else if (view.getId() == R.id.erase_btn) {
            setBrushSize(2);
        } else if (view.getId() == R.id.new_btn) {
            //new button
            AlertDialog.Builder newDialog = new AlertDialog.Builder(this);
            newDialog.setTitle("New drawing");
            newDialog.setMessage("Start new drawing (you will lose the current drawing)?");
            newDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    drawView.startNew();
                    dialog.dismiss();
                }
            });
            newDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            newDialog.show();
            disableUploadButton(true);
        } else if (view.getId() == R.id.save_btn) {
            //save drawing
            AlertDialog.Builder saveDialog = new AlertDialog.Builder(this);
            saveDialog.setTitle("Save drawing");
            saveDialog.setMessage("Save drawing to device Gallery?");
            saveDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    getPermissionAndSave();
                }
            });
            saveDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            saveDialog.show();
        } else if (view.getId() == R.id.upload) {
            CMD_UPLOAD = true;
            AndroidAuthSession session = mApi.getSession();
            if (!session.authenticationSuccessful())
                mApi.getSession().startOAuth2Authentication(MainActivity.this);
            else
                uploadToDrive();
        } else if (view.getId() == R.id.shapes) {
            PopupMenu popupMenu = new PopupMenu(context, view);
            MenuInflater inflater = popupMenu.getMenuInflater();
            inflater.inflate(R.menu.shapes, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.line:
                            drawView.drawLine(10, 10, 100, 100);
                            break;
                        case R.id.circle:
                            drawView.drawCircle(100, 100, 50);
                            break;
                        case R.id.rect:
                            drawView.drawRec(100, 100, 400, 400);
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            });
            popupMenu.show();
        }
    }

    private void setBrushSize(final int type) {
        String msg = "";
        if (type == 1) {
            msg = "Brush size:";
        } else {
            msg = "Eraser size:";
        }
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.seekbar, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(layout).setMessage(msg).setPositiveButton("set", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (type == 1) {
                            drawView.setErase(false);
                            drawView.setBrushSize(seekedSize);
                            drawView.setLastBrushSize(seekedSize);

                        } else {
                            drawView.setErase(true);
                            drawView.setBrushSize(seekedSize);
                        }
                        dialog.dismiss();

                    }
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        SeekBar sb = (SeekBar) layout.findViewById(R.id.seekBar1);
        final TextView text = (TextView) layout.findViewById(R.id.text);
        final int maxVal = (max - min) / step;
        sb.setMax(maxVal);
        sb.setProgress(seekedSize - 5);
        text.setText("" + seekedSize + "/" + maxVal);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekedSize = min + (progress * step);
                text.setText("" + seekedSize + "/" + maxVal);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void uploadToDrive() {
        if (!path.equals("")) {
            File file = new File(path);

            UploadPicture upload = new UploadPicture(this, mApi, PHOTO_DIR, file);
            upload.execute();
            path = "";
            disableUploadButton(true);
        } else {
            Utils.showSnackBar(findViewById(R.id.coord_layout), "Save image before upload");
        }
    }

    private void getPermissionAndSave() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_TO_READ_WRITE_OPERATION_IN_STORAGE);
            } else {
                saveDrawing();
            }
        } else {
            saveDrawing();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_TO_READ_WRITE_OPERATION_IN_STORAGE: {
                if (grantResults.length >= 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveDrawing();
                } else {
                    Utils.showSnackBar(findViewById(R.id.coord_layout), "Write permission denied");
                }
            }
            break;
            case PERMISSION_REQUEST_TO_ACCESS_CAMERA:
                if (grantResults.length >= 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraActivity();
                } else {
                    Utils.showSnackBar(findViewById(R.id.coord_layout), "Camera permission denied");
                }
                break;
            default:
                break;
        }
    }

    private void saveDrawing() {
        try {
            drawView.setDrawingCacheEnabled(true);
            path = mPath + UUID.randomUUID().toString() + ".paint";
            if (!dir.exists()) {
                dir.mkdir();
            }

            File imageFile = new File(path);
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            drawView.getDrawingCache().compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
            drawView.destroyDrawingCache();

            Toast savedToast = Toast.makeText(getApplicationContext(),
                    "Drawing saved to Gallery!", Toast.LENGTH_SHORT);
            savedToast.show();
            disableUploadButton(false);
        } catch (Exception e) {
            Toast unsavedToast = Toast.makeText(getApplicationContext(),
                    "Oops! Image could not be saved.", Toast.LENGTH_SHORT);
            unsavedToast.show();

            e.printStackTrace();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_choose:
                Intent intent = new Intent(this, FileChooserActivity.class);
                intent.putExtra("path", dir.toString());
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            case R.id.take_photo:
                checkCameraActivity();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case FILE_CHOOSER_REQUEST_CODE:
                    path = fileUri = data.getStringExtra("file_uri");
                    Log.d(TAG, "file " + fileUri);
                    drawView.setFilePath(fileUri);
                    break;
                case CAMERA_CAPTURE_IMAGE_REQUEST_CODE:
                    previewCapturedImage();
                    break;
                default:
                    break;
            }
        }
    }

    public Uri getOutputMediaFileUri() {
        return Uri.fromFile(getOutputMediaFile());
    }


    private void checkCameraActivity() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            String permission = Manifest.permission.CAMERA;
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_TO_ACCESS_CAMERA);
            } else {
                startCameraActivity();
            }
        } else {
            startCameraActivity();
        }

    }

    private void startCameraActivity() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraFile = getOutputMediaFileUri();
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFile);
        startActivityForResult(intent, CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    private void previewCapturedImage() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();

            options.inSampleSize = 2;
            Log.d(TAG, "fileUri.getPath() " + cameraFile.getPath());
            final Bitmap bitmap = BitmapFactory.decodeFile(cameraFile.getPath(), options);

            Matrix mat = new Matrix();

            ExifInterface exif;
            try {
                exif = new ExifInterface(cameraFile.getPath());
                String orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
                int orientation = orientString != null ? Integer.parseInt(orientString) : ExifInterface.ORIENTATION_NORMAL;
                int rotateAngle = 0;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
                    rotateAngle = 90;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
                    rotateAngle = 180;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
                    rotateAngle = 270;

                mat.setRotate(rotateAngle, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bitmap bmpPic1 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
            drawView.setFile(bmpPic1);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }

    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }
}
