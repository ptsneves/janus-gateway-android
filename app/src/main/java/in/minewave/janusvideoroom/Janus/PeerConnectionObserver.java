package in.minewave.janusvideoroom.Janus;


import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.math.BigInteger;
import java.util.Arrays;

// Implementation detail: observe ICE & stream changes and react accordingly.
public class PeerConnectionObserver implements PeerConnection.Observer {
    static String TAG = "PeerConnectionObserver";
    final private BigInteger handleId;
    final private VideoSink _renderer;
    final private WebSocketChannel _websocket_channel;

    PeerConnectionObserver(VideoSink renderer, WebSocketChannel wsc,
                           BigInteger handleId) {
        _renderer = renderer;
        this.handleId = handleId;
        _websocket_channel = wsc;
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        Log.d(TAG, "=========onIceCandidate========");
        if (candidate != null) {
            _websocket_channel.trickleCandidate(handleId, candidate);
        } else {
            _websocket_channel.trickleCandidateComplete(handleId);
        }
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        Log.i(TAG, String.format("Removed Ice Candidates %s", Arrays.toString(candidates)));
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        Log.d(TAG, "SignalingState: " + newState);
    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
        Log.d(TAG, "IceConnectionState: " + newState);
        if (newState == PeerConnection.IceConnectionState.CONNECTED) {
            Log.i(TAG, "Ice connected");
        } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
            Log.i(TAG, "Ice disconnected");
        } else if (newState == PeerConnection.IceConnectionState.FAILED) {
            Log.i(TAG, "Ice connection failed");
        }
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        Log.d(TAG, "IceGatheringState: " + newState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
    }

    @Override
    public void onAddStream(final MediaStream stream) {
        Log.d(TAG, "=========== onAddStream ==========");
        if (stream.videoTracks.size() == 1) {
            VideoTrack videoTrack = stream.videoTracks.get(0);
            videoTrack.setEnabled(true);
            if (_renderer != null)
               videoTrack.addSink(_renderer);
        }
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
        Log.i(TAG, "Removed stream");
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
        Log.d(TAG, "New Data channel " + dc.label());
    }

    @Override
    public void onRenegotiationNeeded() {
        // No need to do anything; AppRTC follows a pre-agreed-upon
        // signaling/negotiation protocol.
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
    }
}
