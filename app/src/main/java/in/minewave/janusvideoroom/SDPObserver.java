package in.minewave.janusvideoroom;


import android.util.Log;

import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.math.BigInteger;

class SDPObserver implements SdpObserver {
    private static String TAG = "SDPObserver";
    private WebSocketChannel _webSocketChannel;
    private PeerConnection _peerConnection;
    private BigInteger _handleId;
    private SessionDescription _localSdp;
    private JanusConnection.ConnectionType _type;

    public SDPObserver(WebSocketChannel webSocketChannel, PeerConnection peerConnection, BigInteger handleId,
                       JanusConnection.ConnectionType type) {
        _webSocketChannel = webSocketChannel;
        _peerConnection = peerConnection;
        _handleId = handleId;
        _type = type;
    }

    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
        Log.e(TAG, "SDP on create success");
        final SessionDescription sdp = new SessionDescription(origSdp.type, origSdp.description);
        _localSdp = sdp;
        if (_peerConnection != null) {
            Log.d(TAG, "Set local SDP from " + sdp.type);
            _peerConnection.setLocalDescription(this, sdp);
        }
    }

    @Override
    public void onSetSuccess() {
        if (_peerConnection == null) {
            return;
        }
        if (_type == JanusConnection.ConnectionType.LOCAL) {
            if (_peerConnection.getRemoteDescription() == null) {
                Log.d(TAG, "Local SDP set successfully");
                Log.e(TAG, _localSdp.type.toString());
                _webSocketChannel.publisherCreateOffer(_handleId, _localSdp);
            } else {
                Log.d(TAG, "Remote SDP set successfully");
            }
        } else {
            if (_peerConnection.getLocalDescription() != null) {
                Log.d(TAG, "answer Local SDP set successfully");
                Log.e(TAG, _localSdp.type.toString());
                _webSocketChannel.subscriberCreateAnswer(_handleId, _localSdp);
            } else {
                Log.d(TAG, "answer Remote SDP set successfully");
            }
        }
    }

    @Override
    public void onCreateFailure(final String error) {
        Log.e(TAG, "createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
        Log.e(TAG, "setSDP error: " + error);
    }
}