//
//  Copyright (c) 2019 Rally Tactical Systems, Inc.
//  All rights reserved.
//

package com.rallytac.engagereference.core;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class OfflineDeactivationActivity extends AppCompatActivity
{
    private static String TAG = OfflineDeactivationActivity.class.getSimpleName();

    public static String EXTRA_LICENSE_KEY = "$LICENSEKEY";
    public static String EXTRA_DEVICE_ID = "$DEVICEID";

    private String _licenseKey = null;
    private String _deviceId = null;
    private Bitmap _bm = null;
    private Intent _resultIntent = new Intent();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_deactivation);

        ActionBar ab = getSupportActionBar();
        if(ab != null)
        {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        if(intent != null)
        {
            _licenseKey = intent.getStringExtra(EXTRA_LICENSE_KEY);
            _deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
        }

        buildBitmap();
        setBitmap();
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

    private void setBitmap()
    {
        ImageView iv = findViewById(R.id.ivQrCode);

        if(_bm != null)
        {
            iv.setImageBitmap(_bm);
            iv.setVisibility(View.VISIBLE);
        }
        else
        {
            iv.setVisibility(View.INVISIBLE);
        }
    }

    private void buildBitmap()
    {
        if(_bm == null && !Utils.isEmptyString(_licenseKey) && !Utils.isEmptyString(_deviceId))
        {
            String entitlementKey = getString(R.string.licensing_entitlement);
            String stringToHash = _licenseKey + _deviceId + entitlementKey;
            String hValue = Utils.md5HashOfString(stringToHash);

            StringBuilder sb = new StringBuilder();

            if(Globals.getSharedPreferences().getBoolean(PreferenceKeys.DEVELOPER_USE_DEV_LICENSING_SYSTEM, false))
            {
                sb.append(getString(R.string.offline_licensing_deactivation_url_dev));
            }
            else
            {
                sb.append(getString(R.string.offline_licensing_deactivation_url_prod));
            }

            sb.append("?");
            sb.append("licenseId=");
            sb.append(_licenseKey);
            sb.append("&");
            sb.append("deviceSerialNumber=");
            sb.append(_deviceId);
            sb.append("&");
            sb.append("h=");
            sb.append(hValue);

            String url = sb.toString();

            _bm = Utils.stringToQrCodeBitmap(url, Constants.QR_CODE_WIDTH, Constants.QR_CODE_HEIGHT);
        }
    }
}