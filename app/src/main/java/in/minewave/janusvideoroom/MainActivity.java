package in.minewave.janusvideoroom;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import in.minewave.janusvideoroom.Janus.PeerConnectionClient;
import in.minewave.janusvideoroom.Janus.PeerConnectionParameters;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

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
        try {
            setContentView(R.layout.activity_main);
            localRender = findViewById(R.id.local_video_view);
            EglBase rootEglBase = EglBase.create();
            localRender.init(rootEglBase.getEglBaseContext(), null);
            localRender.setEnableHardwareScaler(true);

            remoteRender = findViewById(R.id.remote_video_view);
            remoteRender.init(rootEglBase.getEglBaseContext(), null);


            PeerConnectionParameters peerConnectionParameters = new PeerConnectionParameters(
                    getString(R.string.janus_websocket_uri), this, 360, 480, 30,
                    "H264", PeerConnectionParameters.VideoCapturerType.CAMERA_FRONT,
                    0, "opus", false);

            peerConnectionClient = new PeerConnectionClient(this, rootEglBase.getEglBaseContext(),
                    peerConnectionParameters,  localRender, remoteRender);
        }
        catch (Exception e) {
            Log.e(TAG, e.getStackTrace().toString());
            alertBox(e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        peerConnectionClient.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        peerConnectionClient.close();
    }
}
