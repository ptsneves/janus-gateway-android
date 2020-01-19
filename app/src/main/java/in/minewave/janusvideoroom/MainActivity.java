package in.minewave.janusvideoroom;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import org.json.JSONObject;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

import java.io.InvalidObjectException;
import java.math.BigInteger;
import in.minewave.janusvideoroom.PeerConnectionClient.PeerConnectionParameters;

public class MainActivity extends AppCompatActivity implements JanusRTCInterface {
    private static final String TAG = "MainActivity";

    private PeerConnectionClient peerConnectionClient;
    private PeerConnectionParameters peerConnectionParameters;

    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private WebSocketChannel mWebSocketChannel;
    LinearLayout rootView;


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
        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.activity_main);
        try {
            mWebSocketChannel = WebSocketChannel.createWebSockeChannel(this, getString(R.string.janus_websocket_uri));
        } catch (Exception e) {
            e.printStackTrace();
            alertBox("Failed to connect. Will finish the application.\n" + e.getMessage());
        }
        mWebSocketChannel.setDelegate(this);

        localRender = findViewById(R.id.local_video_view);
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setEnableHardwareScaler(true);

        remoteRender = findViewById(R.id.remote_video_view);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        peerConnectionParameters  = new PeerConnectionParameters(360,
                480, 30, "H264", 0, "opus", false);
        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(this, rootEglBase.getEglBaseContext(),
                peerConnectionParameters, remoteRender, mWebSocketChannel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        peerConnectionClient.startVideoSource();
    }

    private VideoCapturer createVideoCapturer() throws InvalidObjectException {

        if (Camera2Enumerator.isSupported(this)) {
            CameraEnumerator enumerator = new Camera2Enumerator(this);
            final String[] deviceNames = enumerator.getDeviceNames();
            for (String device_name : deviceNames) {
                if (enumerator.isFrontFacing(device_name)) {
                    Log.d(TAG, "Creating capturer using camera2 API.");
                    return new Camera2Capturer(this, device_name, null);
                }
            }
        }
        throw new InvalidObjectException("Could not find front camera or camera2enumerator is not supported");
    }

    // interface JanusRTCInterface
    @Override
    public void onPublisherJoined(final BigInteger handleId) {
        try {
            videoCapturer = createVideoCapturer();
        } catch (InvalidObjectException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), localRender, videoCapturer, handleId);
        peerConnectionClient.createOffer(handleId);
    }

    @Override
    public void onPublisherRemoteJsep(final BigInteger handleId, final JSONObject jsep) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.optString("type"));
                String sdp = jsep.optString("sdp");
                SessionDescription sessionDescription = new SessionDescription(type, sdp);
                peerConnectionClient.setRemoteDescription(handleId, sessionDescription);
            }
        });

    }

    @Override
    public void subscriberHandleRemoteJsep(final BigInteger handleId, final JSONObject jsep) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.optString("type"));
                String sdp = jsep.optString("sdp");
                SessionDescription sessionDescription = new SessionDescription(type, sdp);
                peerConnectionClient.subscriberHandleRemoteJsep(handleId, sessionDescription);
            }
        });
    }

    @Override
    public void onLeaving(BigInteger handleId) {

    }
}
