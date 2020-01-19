package in.minewave.janusvideoroom.Janus;

import java.security.InvalidParameterException;

public class PeerConnectionParameters {
  public final int videoWidth;
  public final int videoHeight;
  public final int videoFps;
  public final String videoCodec;
  public final int audioStartBitrate;
  public final String audioCodec;
  public final boolean noAudioProcessing;

  public PeerConnectionParameters(
          int videoWidth, int videoHeight, int videoFps, String videoCodec,
          int audioStartBitrate, String audioCodec,
          boolean noAudioProcessing) {

    // If video resolution is not specified, default to HD.
    if (videoWidth == 0 || videoHeight == 0)
      throw new InvalidParameterException("Video width or height cannot be 0");

    // If fps is not specified, default to 30.
    if (videoFps == 0)
      throw new InvalidParameterException("Video FPS cannot be 0");

    this.videoWidth = videoWidth;
    this.videoHeight = videoHeight;
    this.videoFps = videoFps;
    this.videoCodec = videoCodec;
    this.audioStartBitrate = audioStartBitrate;
    this.audioCodec = audioCodec;
    this.noAudioProcessing = noAudioProcessing;
  }
}
