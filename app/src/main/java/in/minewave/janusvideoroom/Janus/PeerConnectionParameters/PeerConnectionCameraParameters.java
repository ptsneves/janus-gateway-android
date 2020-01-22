package in.minewave.janusvideoroom.Janus.PeerConnectionParameters;

import android.app.Activity;

public class PeerConnectionCameraParameters extends PeerConnectionParameters {
    public PeerConnectionCameraParameters(String janus_web_socket_uri, Activity activity,
                                          int videoWidth, int videoHeight, int videoFps,
                                          String videoCodec, VideoCapturerType capturerType,
                                          int audioStartBitrate, String audioCodec,
                                          boolean noAudioProcessing) {
        super(janus_web_socket_uri, activity, videoWidth, videoHeight, videoFps, videoCodec,
                capturerType, audioStartBitrate, audioCodec, noAudioProcessing);
    }
}
