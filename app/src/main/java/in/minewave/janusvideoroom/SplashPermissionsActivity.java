package in.minewave.janusvideoroom;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/*
"Stolen" from https://stackoverflow.com/a/37845874/227990
 */
public class SplashPermissionsActivity extends Activity {
    static final String[] _requestPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
    };

    @Override
    protected void onStart() {
        super.onStart();

        this.checkPermissions(0);   //start at zero, of course
    }

    private void checkPermissions(int permissionIndex) {
        if(permissionIndex >= _requestPermissions.length) {
            //i.e. we have requested all permissions, so show the splash screen
            this.showSplash();
        }
        else {
            this.askForPermission(_requestPermissions[permissionIndex], permissionIndex);
        }
    }

    private void askForPermission(String permission, int permissionIndex) {
        if (ContextCompat.checkSelfPermission(
                this,
                permission)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            //  if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {

            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.

            //} else {

            // No explanation needed, we can request the permission.

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permission},
                    permissionIndex //permissionIndex will become the requestCode on callback
            );
        }
        else {
            this.checkPermissions(permissionIndex+1); //check the next permission
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //Regardless of whether the permission was granted we carry on.
        //If perms have been denied then the app must cater for it

        this.checkPermissions(requestCode+1); //check the next permission
    }

    private void showSplash() {
        //(ta da)

        //once splashed, start the main activity
        this.startMainActivity();
    }

    private void startMainActivity() {
        Intent mainIntent = new Intent(
                this,
                MainActivity.class
        );
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        this.startActivity(mainIntent);

        this.finish();
    }
}
