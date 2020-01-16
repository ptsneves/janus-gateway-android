package in.minewave.janusvideoroom;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RTCStatsReport;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.math.BigInteger;
import in.minewave.janusvideoroom.PeerConnectionClient.PeerConnectionParameters;
import in.minewave.janusvideoroom.PeerConnectionClient.PeerConnectionEvents;

public class MainActivity extends AppCompatActivity implements JanusRTCInterface, PeerConnectionEvents {
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
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
        alertDialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.activity_main);
        mWebSocketChannel = new WebSocketChannel();
        try {
            mWebSocketChannel.initConnection(getString(R.string.janus_websocket_uri));
        } catch (Exception e) {
            e.printStackTrace();
            alertBox("Failed to connect. Will finish the application.\n" + e.getMessage());
        }
        mWebSocketChannel.setDelegate(this);

        createLocalRender();
        remoteRender = findViewById(R.id.remote_video_view);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        peerConnectionParameters  = new PeerConnectionParameters(false, 360, 480, 20, "H264", true, 0, "opus", false, false, false, false, false);
        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(this, peerConnectionParameters, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        peerConnectionClient.startVideoSource();
    }

    private void createLocalRender() {
        localRender = findViewById(R.id.local_video_view);
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setEnableHardwareScaler(true);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(this)) {
            Log.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            Log.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    // interface JanusRTCInterface
    @Override
    public void onPublisherJoined(final BigInteger handleId) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                videoCapturer = createVideoCapturer();
                if (peerConnectionClient == null)
                    Log.e("sadsdsd", "Shitsz");
                peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), localRender, videoCapturer, handleId);

                peerConnectionClient.createOffer(handleId);
            }
        });

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

    // interface PeerConnectionClient.PeerConnectionEvents
    @Override
    public void onLocalDescription(final SessionDescription sdp, final BigInteger handleId) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Log.e(TAG, sdp.type.toString());
                mWebSocketChannel.publisherCreateOffer(handleId, sdp);
            }
        });

    }

    @Override
    public void onRemoteDescription(final SessionDescription sdp, final BigInteger handleId) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Log.e(TAG, sdp.type.toString());
                mWebSocketChannel.subscriberCreateAnswer(handleId, sdp);
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate, final BigInteger handleId) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Log.e(TAG, "=========onIceCandidate========");
                if (candidate != null) {
                    mWebSocketChannel.trickleCandidate(handleId, candidate);
                } else {
                    mWebSocketChannel.trickleCandidateComplete(handleId);
                }
            }
        });

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {

    }

    @Override
    public void onIceConnected() {

    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(RTCStatsReport reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {

    }

    @Override
    public void onRemoteRender(final JanusConnection connection) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                remoteRender = new SurfaceViewRenderer(MainActivity.this);
//                remoteRender.init(rootEglBase.getEglBaseContext(), null);
//                LinearLayout.LayoutParams params  = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
//                rootView.addView(remoteRender, params);
                connection.videoTrack.addRenderer(new VideoRenderer(remoteRender));
            }
        });
    }
}
