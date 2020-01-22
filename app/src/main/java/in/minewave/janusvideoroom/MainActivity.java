package in.minewave.janusvideoroom;

import androidx.appcompat.app.AlertDialog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import in.minewave.janusvideoroom.Janus.PeerConnectionClient;
import in.minewave.janusvideoroom.Janus.PeerConnectionParameters.PeerConnectionCameraParameters;
import in.minewave.janusvideoroom.Janus.PeerConnectionParameters.PeerConnectionParameters;
import in.minewave.janusvideoroom.Janus.PeerConnectionParameters.PeerConnectionScreenShareParameters;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    private PeerConnectionClient peerConnectionClient;

    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private void alertBox(String reason_to_finish) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Alert");
        alertDialog.setMessage(reason_to_finish);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
        alertDialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }

    public void init(Intent permission_data, int permission_code) {
        try {
            setContentView(R.layout.activity_main);
            localRender = findViewById(R.id.local_video_view);
            EglBase rootEglBase = EglBase.create();
            localRender.init(rootEglBase.getEglBaseContext(), null);
            localRender.setEnableHardwareScaler(true);

            remoteRender = findViewById(R.id.remote_video_view);
            remoteRender.init(rootEglBase.getEglBaseContext(), null);

            PeerConnectionScreenShareParameters peerConnectionParameters = new PeerConnectionScreenShareParameters(
                    getString(R.string.janus_websocket_uri), this, 360, 480, 30,
                    "H264",
                    0, "opus", false,
                    permission_data,
                    permission_code);

//            PeerConnectionCameraParameters peerConnectionParameters = new PeerConnectionParameters(
//                    getString(R.string.janus_websocket_uri), this, 360, 480, 30,
//                    "H264", PeerConnectionParameters.VideoCapturerType.CAMERA_FRONT,
//                    0, "opus", false);

            peerConnectionClient = new PeerConnectionClient(this, rootEglBase.getEglBaseContext(),
                    peerConnectionParameters,  localRender, remoteRender);
        }
        catch (Exception e) {
            Log.e(TAG, e.getStackTrace().toString());
            alertBox(e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        init(data, resultCode);
        Log.e(TAG, "Was called");
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
