package in.minewave.janusvideoroom;

import android.content.Context;
import android.util.Log;
import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

public class PeerConnectionClient {
  public static final String VIDEO_TRACK_ID = "ARDAMSv0";
  public static final String AUDIO_TRACK_ID = "ARDAMSa0";
  public static final String VIDEO_TRACK_TYPE = "video";
  private static final String TAG = "PCRTCClient";
  private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
  private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
  private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
  private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

  private static final PeerConnectionClient instance = new PeerConnectionClient();

  private Context context;
  private PeerConnectionFactory factory;
  private ConcurrentHashMap<BigInteger, JanusConnection> peerConnectionMap;

  private AudioSource audioSource;
  private VideoSource videoSource;
  private boolean videoCapturerStopped;
  private boolean isError;
  private VideoSink localRender;
  private MediaConstraints sdpMediaConstraints;
  public PeerConnectionParameters peerConnectionParameters;
  private MediaStream mediaStream;
  private VideoCapturer videoCapturer;
  // enableVideo is set to true if video should be rendered and sent.
  private boolean renderVideo;
  private VideoTrack localVideoTrack;
  private VideoTrack remoteVideoTrack;
  // enableAudio is set to true if audio should be sent.
  private boolean enableAudio;
  private AudioTrack localAudioTrack;
  private SurfaceViewRenderer viewRenderer;
  private WebSocketChannel _webSocketChannel;

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
      if (videoWidth == 0 || videoHeight == 0) {
        throw new InvalidParameterException("Video width or height cannot be 0");
      }

      // If fps is not specified, default to 30.
      if (videoFps == 0) {
        throw new InvalidParameterException("Video FPS cannot be 0");
      }
      Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);

      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
      this.videoCodec = videoCodec;
      this.audioStartBitrate = audioStartBitrate;
      this.audioCodec = audioCodec;
      this.noAudioProcessing = noAudioProcessing;
    }
  }

  private PeerConnectionClient() {
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    peerConnectionMap = new ConcurrentHashMap<>();
  }

  public static PeerConnectionClient getInstance() {
    return instance;
  }

  public void createPeerConnectionFactory(final Context context,
      final EglBase.Context renderEGLContext,
      final PeerConnectionParameters peerConnectionParameters,
                                          final SurfaceViewRenderer viewRenderer,
                                          final WebSocketChannel webSocketChannel) {
    this.peerConnectionParameters = peerConnectionParameters;
    // Reset variables to initial states.
    this.context = null;
    factory = null;
    videoCapturerStopped = false;
    isError = false;
    mediaStream = null;
    videoCapturer = null;
    renderVideo = true;
    localVideoTrack = null;
    remoteVideoTrack = null;
    enableAudio = true;
    localAudioTrack = null;
    this.viewRenderer = viewRenderer;
    this._webSocketChannel = webSocketChannel;

    Log.d(TAG,
            "Create peer connection factory. Use video: true");
    isError = false;

    PeerConnectionFactory.InitializationOptions factory_init_options = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .setInjectableLogger(((s, severity, s1) -> {Log.d("internal", s1);}), Logging.Severity.LS_INFO)
            .createInitializationOptions();

    PeerConnectionFactory.initialize(factory_init_options);

    this.context = context;
    factory = PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(renderEGLContext))
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(renderEGLContext, true, true))
            .createPeerConnectionFactory();

    Log.d(TAG, "Peer connection factory created.");
  }

  public void createPeerConnection(final EglBase.Context renderEGLContext,
                                   final VideoSink localRender,
                                   final VideoCapturer videoCapturer, final BigInteger handleId) {
    if (peerConnectionParameters == null) {
      Log.e(TAG, "Creating peer connection without initializing factory.");
      return;
    }
    this.localRender = localRender;
    this.videoCapturer = videoCapturer;

    // Create SDP constraints.
    sdpMediaConstraints = new MediaConstraints();
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));


    if (factory == null || isError) {
      Log.e(TAG, "Peerconnection factory is not created");
      return;
    }

    PeerConnection peerConnection = createPeerConnection(handleId, JanusConnection.ConnectionType.LOCAL);

    mediaStream = factory.createLocalMediaStream("ARDAMS");
    mediaStream.addTrack(createVideoTrack(videoCapturer, renderEGLContext));

    mediaStream.addTrack(createAudioTrack(peerConnectionParameters.noAudioProcessing));
    peerConnection.addStream(mediaStream);

  }

  private PeerConnection createPeerConnection(BigInteger handleId, JanusConnection.ConnectionType type) {
    Log.d(TAG, "Create peer connection.");
    PeerConnection.IceServer iceServer = PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer();

    List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    iceServers.add(iceServer);
    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
    rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
    rtcConfig.enableDtlsSrtp = true;

    PeerConnectionObserver pcObserver = new PeerConnectionObserver(viewRenderer, _webSocketChannel);

    PeerConnection peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
    if (peerConnection == null)
      throw new NullPointerException("peer connection is null");

    SDPObserver sdpObserver = new SDPObserver(_webSocketChannel, peerConnection, handleId, type);

    JanusConnection janusConnection = new JanusConnection();
    janusConnection.handleId = handleId;
    janusConnection.sdpObserver = sdpObserver;
    janusConnection.peerConnection = peerConnection;
    janusConnection.type = type;

    peerConnectionMap.put(handleId, janusConnection);

    pcObserver.setConnection(janusConnection);
    Log.d(TAG, "Peer connection created.");
    return peerConnection;
  }

  private void closeInternal() {
    Log.d(TAG, "Closing peer connection.");

    if (peerConnectionMap != null) {
      for (Map.Entry<BigInteger, JanusConnection> entry: peerConnectionMap.entrySet()) {
        if (entry.getValue().peerConnection != null) {
          entry.getValue().peerConnection.dispose();
        }
      }
    }
    Log.d(TAG, "Closing audio source.");
    if (audioSource != null) {
      audioSource.dispose();
      audioSource = null;
    }
    Log.d(TAG, "Stopping capture.");
    if (videoCapturer != null) {
      try {
        videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      videoCapturerStopped = true;
      videoCapturer.dispose();
      videoCapturer = null;
    }
    Log.d(TAG, "Closing video source.");
    if (videoSource != null) {
      videoSource.dispose();
      videoSource = null;
    }
    Log.d(TAG, "Closing peer connection factory.");
    if (factory != null) {
      factory.dispose();
      factory = null;
    }
    Log.d(TAG, "Closing peer connection done.");
    PeerConnectionFactory.stopInternalTracingCapture();
    PeerConnectionFactory.shutdownInternalTracer();
  }

  public void createOffer(final BigInteger handleId) {
      JanusConnection connection = peerConnectionMap.get(handleId);
      PeerConnection peerConnection = connection.peerConnection;
      if (peerConnection != null && !isError) {
        Log.d(TAG, "PC Create OFFER");
        peerConnection.createOffer(connection.sdpObserver, sdpMediaConstraints);
      }
  }

  public void setRemoteDescription(final BigInteger handleId, final SessionDescription sdp) {
    PeerConnection peerConnection = peerConnectionMap.get(handleId).peerConnection;
    if (peerConnection == null || isError) {
      return;
    }
    SDPObserver sdpObserver = peerConnectionMap.get(handleId).sdpObserver;

    peerConnection.setRemoteDescription(sdpObserver, sdp);
  }

  public void subscriberHandleRemoteJsep(final BigInteger handleId, final SessionDescription sdp) {
      PeerConnection peerConnection = createPeerConnection(handleId, JanusConnection.ConnectionType.REMOTE);
      SDPObserver sdpObserver = peerConnectionMap.get(handleId).sdpObserver;
      if (peerConnection == null || isError) {
        return;
      }
      JanusConnection connection = peerConnectionMap.get(handleId);
      peerConnection.setRemoteDescription(sdpObserver, sdp);
      Log.d(TAG, "PC create ANSWER");
      peerConnection.createAnswer(connection.sdpObserver, sdpMediaConstraints);
  }

  public void startVideoSource() {
      if (videoCapturer != null && videoCapturerStopped) {
        Log.d(TAG, "Restart video source.");
        videoCapturer.startCapture(peerConnectionParameters.videoWidth,
                peerConnectionParameters.videoHeight,
                peerConnectionParameters.videoFps);
        videoCapturerStopped = false;
      }
  }

  private AudioTrack createAudioTrack(boolean disable_audio_processing) {
    // Create audio constraints.
    MediaConstraints audioConstraints = new MediaConstraints();
    // added for audio performance measurements
    if (disable_audio_processing) {
      Log.d(TAG, "Disabling audio processing");
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
    }
    audioSource = factory.createAudioSource(audioConstraints);
    localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    localAudioTrack.setEnabled(enableAudio);
    return localAudioTrack;
  }

  private VideoTrack createVideoTrack(VideoCapturer capturer, EglBase.Context renderEGLContext) {
    videoSource = factory.createVideoSource(false);
    SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", renderEGLContext);

    capturer.initialize(surfaceTextureHelper, context,  videoSource.getCapturerObserver());
    capturer.startCapture(peerConnectionParameters.videoWidth, peerConnectionParameters.videoHeight,
            peerConnectionParameters.videoFps);

    localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
    localVideoTrack.setEnabled(renderVideo);
    localVideoTrack.addSink(localRender);
    return localVideoTrack;
  }
}
