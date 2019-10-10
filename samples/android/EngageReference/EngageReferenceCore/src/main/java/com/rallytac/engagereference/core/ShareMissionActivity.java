//
//  Copyright (c) 2019 Rally Tactical Systems, Inc.
//  All rights reserved.
//

package com.rallytac.engagereference.core;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class ShareMissionActivity extends AppCompatActivity
{
    private static String TAG = SettingsActivity.class.getSimpleName();

    private EngageApplication _app = null;

    private String _pwd;
    private String _deflectionUrl;
    private Bitmap _bm;
    private boolean _qrCodeZoomed = false;
    private String _base91DataString = null;
    private JSONObject _jsonConfiguration = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_mission);

        _app = (EngageApplication)getApplication();
        ((EditText)findViewById(R.id.etDeflectionUrl)).setText(Globals.getSharedPreferences().getString(PreferenceKeys.LAST_QRCODE_DEFLECTION_URL, ""));

        LinearLayout lay = findViewById(R.id.layoutQrCode);
        lay.setVisibility(View.INVISIBLE);

        ActionBar ab = getSupportActionBar();
        if(ab != null)
        {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setTitle("Share");

        restoreSavedState(savedInstanceState);
        setElements();
        setBitmap();
        updateZoomView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        Log.d(TAG, "onSaveInstanceState");
        saveState(outState);
        super.onSaveInstanceState(outState);
    }

    private void saveState(Bundle bundle)
    {
        getElements();

        bundle.putString("PWD", _pwd);
        bundle.putString("DEFLECTION_URL", _deflectionUrl);
        bundle.putString("DATASTRING", _base91DataString);
        bundle.putBoolean("ZOOMED", _qrCodeZoomed);
    }

    private void restoreSavedState(Bundle bundle)
    {
        if(bundle == null)
        {
            return;
        }

        _pwd = bundle.getString("PWD");
        _deflectionUrl = bundle.getString("DEFLECTION_URL", null);
        _base91DataString = bundle.getString("DATASTRING", null);
        _qrCodeZoomed = bundle.getBoolean("ZOOMED", false);

        buildBitmap();
    }

    public void onClickQrCode(View view)
    {
        _qrCodeZoomed = !_qrCodeZoomed;
        updateZoomView();
    }

    private void updateZoomView()
    {
        LinearLayout lay = findViewById(R.id.layoutQrControls);
        if(_qrCodeZoomed)
        {
            lay.setVisibility(View.GONE);
        }
        else
        {
            lay.setVisibility(View.VISIBLE);
        }
    }

    private void getElements()
    {
        _pwd = ((EditText)findViewById(R.id.etPassword)).getText().toString();
        _deflectionUrl = ((EditText)findViewById(R.id.etDeflectionUrl)).getText().toString();
    }

    private void setElements()
    {
        ((EditText)findViewById(R.id.etPassword)).setText(_pwd);
        ((EditText)findViewById(R.id.etDeflectionUrl)).setText(_deflectionUrl);
    }

    private void setBitmap()
    {
        ImageView iv = findViewById(R.id.ivQrCode);
        LinearLayout lay = findViewById(R.id.layoutQrCode);

        if(_bm != null)
        {
            iv.setImageBitmap(_bm);
            lay.setVisibility(View.VISIBLE);
        }
        else
        {
            lay.setVisibility(View.INVISIBLE);
        }
    }

    private void buildBitmap()
    {
        if(!Utils.isEmptyString(_base91DataString))
        {
            _bm = Utils.stringToQrCodeBitmap(_base91DataString, Constants.QR_CODE_WIDTH, Constants.QR_CODE_HEIGHT);
        }
        else
        {
            _bm = null;
        }
    }

    public void onClickGenerateQrCode(View view)
    {
        Utils.hideKeyboardFrom(this, view);

        _jsonConfiguration = _app.getActiveConfiguration().makeTemplate();
        if(_jsonConfiguration == null)
        {
            Utils.showPopupMsg(ShareMissionActivity.this, getString(R.string.share_failed_to_create_configuration_package));
            finish();
            return;
        }

        String json = _jsonConfiguration.toString();

        if(json != null)
        {
            getElements();

            // This is our data record (header signature + version + json)
            String textRecord = (Constants.QR_CODE_HEADER + Constants.QR_VERSION) + json;

            // Compress it
            byte[] dataBytes = textRecord.getBytes(Utils.getEngageCharSet());
            byte[] compressedDataBytes = Utils.compress(dataBytes, 0, dataBytes.length);

            // It gets encrypted if we got a password
            if(!Utils.isEmptyString(_pwd))
            {
                String pwdHexString = Utils.toHexString(_pwd.getBytes(Utils.getEngageCharSet()));
                compressedDataBytes = Globals.getEngageApplication().getEngine().encryptSimple(compressedDataBytes, pwdHexString);
                if(compressedDataBytes == null)
                {
                    Utils.showPopupMsg(ShareMissionActivity.this,getString(R.string.share_failed_to_encrypt_configuration_package));
                    finish();
                    return;
                }
            }

            // Convert to a Base91-encoded string
            _base91DataString = new String(Base91.encode(compressedDataBytes), Utils.getEngageCharSet());

            // Precede with a deflection url if any (and save whatever was there anyway - even if it's nothing)
            Globals.getSharedPreferencesEditor().putString(PreferenceKeys.LAST_QRCODE_DEFLECTION_URL, _deflectionUrl);
            Globals.getSharedPreferencesEditor().apply();

            if(!Utils.isEmptyString(_deflectionUrl))
            {
                // Note the Constants.QR_DEFLECTION_URL_SEP - its for the decoder to see if there's a deflection URL
                _base91DataString = _deflectionUrl + Constants.QR_DEFLECTION_URL_SEP + _base91DataString;
            }

            // Finally, we can create our QR code!
            buildBitmap();
            setBitmap();
        }
        else
        {
            Utils.showPopupMsg(ShareMissionActivity.this,getString(R.string.share_failed_to_create_shareable_configuration_package));
            finish();
        }
    }

    public void onClickShare(View view)
    {
        ActiveConfiguration ac = _app.getActiveConfiguration();
        String downloadUrl = null;

        if(((Switch) findViewById(R.id.swUpload)).isChecked())
        {
            UploadMissionTask umt = new UploadMissionTask(new Handler()
            {
                @Override
                public void handleMessage(Message msg)
                {
                    int responseCode = msg.arg1;
                    String resultMsg = msg.getData().getString(UploadMissionTask.BUNDLE_RESULT_MSG);

                    if(responseCode >= 200 && responseCode <= 299)
                    {
                        Toast.makeText(ShareMissionActivity.this, "Mission uploaded", Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        Toast.makeText(ShareMissionActivity.this, "Mission upload failed - " + resultMsg, Toast.LENGTH_LONG).show();
                    }
                }
            });

            String urlBase = Constants.DEF_CONFIGHUB_BASE_URL;
            String fn = ac.getMissionId() + ".json";
            String content = _jsonConfiguration.toString();

            umt.execute(urlBase, fn, content);

            downloadUrl = urlBase + "/" + fn;
        }

        try
        {
            File fd = File.createTempFile("qr-" + ac.getMissionName().replace(" ", "-"), ".jpg",
                                            Environment.getExternalStorageDirectory());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            _bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            byte[] bitmapdata = bos.toByteArray();

            FileOutputStream fos = new FileOutputStream(fd);
            fos.write(bitmapdata);
            fos.close();

            Uri u = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", fd);

            fd.deleteOnExit();

            String extraText;
            ShareableData data = new ShareableData();

            if(Utils.isEmptyString(downloadUrl))
            {
                extraText = String.format(getString(R.string.fmt_scan_this_qr_code_to_join_the_mission), ac.getMissionName());
            }
            else
            {
                data.setUrl(downloadUrl);

                StringBuilder sb = new StringBuilder();
                sb.append("Join the '<b>");
                sb.append(ac.getMissionName());
                sb.append("</b>' mission by scanning the QR code below or downloading from the mission hub at <a href=\"");
                sb.append(downloadUrl);
                sb.append("\">");
                sb.append(downloadUrl);
                sb.append("</a>.");
                extraText = sb.toString();
            }

            data.setUri(u);
            data.setText("Engage Mission");
            //data.setTwitterAccount("@some_twitter_account");
            data.setHtml(extraText);
            data.setSubject(getString(R.string.app_name) + " : " + ac.getMissionName());
            startActivity(ShareHelper.buildShareIntent(this, data, "Share ..."));
        }
        catch (Exception e)
        {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}