package in.minewave.janusvideoroom.Janus.PeerConnectionParameters;

import android.app.Activity;
import android.content.Intent;

public class PeerConnectionScreenShareParameters extends PeerConnectionParameters {
    final public Intent permission_data;
    final public int permission_result_code;

    public PeerConnectionScreenShareParameters(String janus_web_socket_uri, Activity activity,
                                          int videoWidth, int videoHeight, int videoFps,
                                          String videoCodec,
                                          int audioStartBitrate, String audioCodec,
                                          boolean noAudioProcessing,
                                          Intent permission_data,
                                          int permission_result_code) {

        super(janus_web_socket_uri, activity, videoWidth, videoHeight, videoFps, videoCodec,
                VideoCapturerType.SCREEN_SHARE, audioStartBitrate, audioCodec, noAudioProcessing);

        this.permission_data = permission_data;
        this.permission_result_code = permission_result_code;
    }
}
