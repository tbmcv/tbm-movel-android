package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;

public class MainActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences config = getSharedPreferences(
                getString(R.string.settings_key), Context.MODE_PRIVATE);
        if (!(config.contains(getString(R.string.setting_acctname))
                && config.contains(getString(R.string.setting_password)))) {
            startActivity(new Intent(MainActivity.this, InitConfigActivity.class));
            finish();
        }
    }
}
