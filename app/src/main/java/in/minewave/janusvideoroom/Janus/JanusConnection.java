package in.minewave.janusvideoroom.Janus;

import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;

import java.math.BigInteger;

public class JanusConnection {
    public enum ConnectionType {
        REMOTE,
        LOCAL
    };
    public BigInteger handleId;
    public PeerConnection peerConnection;
    public SDPObserver sdpObserver;
    public VideoTrack videoTrack;
    public ConnectionType type;
}
