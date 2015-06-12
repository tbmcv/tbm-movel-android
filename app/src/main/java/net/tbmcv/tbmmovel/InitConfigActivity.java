package net.tbmcv.tbmmovel;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class InitConfigActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init_config);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_init_config, menu);
        return true;
    }

    private void setControlsEnabled(boolean enabled) {
        findViewById(R.id.okButton).setEnabled(enabled);
        findViewById(R.id.helpButton).setEnabled(enabled);
        findViewById(R.id.passwordEntry).setEnabled(enabled);
        findViewById(R.id.usernameEntry).setEnabled(enabled);
    }

    private void onLoginError(Exception err) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(
                        InitConfigActivity.this, R.string.login_error, Toast.LENGTH_LONG).show();
                ((EditText) findViewById(R.id.passwordEntry)).setText("");
                setControlsEnabled(true);
            }
        });
    }

    public void onOkButtonClick(View view) throws JSONException {
        setControlsEnabled(false);

        final String acctName = "c/" + ((EditText) findViewById(R.id.usernameEntry)).getText();
        String tmpPw = ((EditText) findViewById(R.id.passwordEntry)).getText().toString();

        final JsonRestClient restClient = getRestClient();
        restClient.setAuth(acctName, tmpPw);
        Uri uri = Uri.parse("/idens").buildUpon()
                .appendEncodedPath(acctName).appendPath("pw")
                .build();
        JSONObject body = new JSONObject().put("reset", "base64").put("size", 4);
        restClient.fetch("POST", uri, body, new JsonRestClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                try {
                    String newPw = result.getString("pw");
                    // TODO store new password
                    restClient.setAuth(acctName, newPw);
                    // TODO configure phone line?
                    // TODO move to another activity
                } catch (JSONException e) {
                    onLoginError(e);
                }
            }

            @Override
            public void onFailure(Exception err) {
                onLoginError(err);
            }
        });
    }
}
