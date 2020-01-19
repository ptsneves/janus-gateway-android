package in.minewave.janusvideoroom;

import android.content.Context;
import android.util.Log;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
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
  private static final int HD_VIDEO_WIDTH = 1280;
  private static final int HD_VIDEO_HEIGHT = 720;

  private static final PeerConnectionClient instance = new PeerConnectionClient();

  private final ScheduledExecutorService executor;

  private Context context;
  private PeerConnectionFactory factory;
  private ConcurrentHashMap<BigInteger, JanusConnection> peerConnectionMap;

  private AudioSource audioSource;
  private VideoSource videoSource;
  private boolean videoCapturerStopped;
  private boolean isError;
  private VideoSink localRender;
  private int videoWidth;
  private int videoHeight;
  private int videoFps;
  private MediaConstraints sdpMediaConstraints;
  private PeerConnectionParameters peerConnectionParameters;
  private PeerConnectionEvents events;
  private MediaStream mediaStream;
  private VideoCapturer videoCapturer;
  // enableVideo is set to true if video should be rendered and sent.
  private boolean renderVideo;
  private VideoTrack localVideoTrack;
  private VideoTrack remoteVideoTrack;
  // enableAudio is set to true if audio should be sent.
  private boolean enableAudio;
  private AudioTrack localAudioTrack;


  public static class PeerConnectionParameters {
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
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
      this.videoCodec = videoCodec;
      this.audioStartBitrate = audioStartBitrate;
      this.audioCodec = audioCodec;
      this.noAudioProcessing = noAudioProcessing;
    }
  }

  /**
   * Peer connection events.
   */
  public interface PeerConnectionEvents {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp, final BigInteger handleId);


    void onRemoteDescription(final SessionDescription sdp, final BigInteger handleId);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate, final BigInteger handleId);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected();

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected();

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final RTCStatsReport reports);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description);

    void onRemoteRender(JanusConnection connection);
  }

  private PeerConnectionClient() {
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    executor = Executors.newSingleThreadScheduledExecutor();
    peerConnectionMap = new ConcurrentHashMap<>();
  }

  public static PeerConnectionClient getInstance() {
    return instance;
  }

  public void createPeerConnectionFactory(final Context context,
      final EglBase.Context renderEGLContext,
      final PeerConnectionParameters peerConnectionParameters, final PeerConnectionEvents events) {
    this.peerConnectionParameters = peerConnectionParameters;
    this.events = events;
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

    try {
      createMediaConstraintsInternal();
      createPeerConnectionInternal(renderEGLContext, handleId);
    } catch (Exception e) {
      reportError("Failed to create peer connection: " + e.getMessage());
      throw e;
    }

  }


  private void createMediaConstraintsInternal() {
    // Create peer connection constraints.

    // Create video constraints if video call is enabled.
    videoWidth = peerConnectionParameters.videoWidth;
    videoHeight = peerConnectionParameters.videoHeight;
    videoFps = peerConnectionParameters.videoFps;

    // If video resolution is not specified, default to HD.
    if (videoWidth == 0 || videoHeight == 0) {
      videoWidth = HD_VIDEO_WIDTH;
      videoHeight = HD_VIDEO_HEIGHT;
    }

    // If fps is not specified, default to 30.
    if (videoFps == 0) {
      videoFps = 30;
    }
    Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);


    // Create SDP constraints.
    sdpMediaConstraints = new MediaConstraints();
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
  }

  private PeerConnection createPeerConnection(BigInteger handleId, boolean type) {
    Log.d(TAG, "Create peer connection.");
    PeerConnection.IceServer iceServer = PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer();

    List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    iceServers.add(iceServer);
    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
    rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
    rtcConfig.enableDtlsSrtp = true;

    PCObserver pcObserver = new PCObserver();
    SDPObserver sdpObserver = new SDPObserver();

    PeerConnection peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
    if (peerConnection == null)
      throw new NullPointerException("peer connection is null");

    JanusConnection janusConnection = new JanusConnection();
    janusConnection.handleId = handleId;
    janusConnection.sdpObserver = sdpObserver;
    janusConnection.peerConnection = peerConnection;
    janusConnection.type = type;

    peerConnectionMap.put(handleId, janusConnection);
    pcObserver.setConnection(janusConnection);
    sdpObserver.setConnection(janusConnection);
    Log.d(TAG, "Peer connection created.");
    return peerConnection;
  }


  private void createPeerConnectionInternal(EglBase.Context renderEGLContext, BigInteger handleId) {
    if (factory == null || isError) {
      Log.e(TAG, "Peerconnection factory is not created");
      return;
    }

    PeerConnection peerConnection = createPeerConnection(handleId, true);

    mediaStream = factory.createLocalMediaStream("ARDAMS");
    mediaStream.addTrack(createVideoTrack(videoCapturer, renderEGLContext));

    mediaStream.addTrack(createAudioTrack(peerConnectionParameters.noAudioProcessing));
    peerConnection.addStream(mediaStream);
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
    events.onPeerConnectionClosed();
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
    SDPObserver sdpObserver = peerConnectionMap.get(handleId).sdpObserver;
    if (peerConnection == null || isError) {
      return;
    }
    peerConnection.setRemoteDescription(sdpObserver, sdp);
  }

  public void subscriberHandleRemoteJsep(final BigInteger handleId, final SessionDescription sdp) {
      PeerConnection peerConnection = createPeerConnection(handleId, false);
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
        videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
        videoCapturerStopped = false;
      }
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, "Peerconnection error: " + errorMessage);
    if (!isError) {
      events.onPeerConnectionError(errorMessage);
      isError = true;
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
    capturer.startCapture(videoWidth, videoHeight, videoFps);

    localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
    localVideoTrack.setEnabled(renderVideo);
    localVideoTrack.addSink(localRender);
    return localVideoTrack;
  }

  // Implementation detail: observe ICE & stream changes and react accordingly.
  private class PCObserver implements PeerConnection.Observer {
    private JanusConnection connection;
    private PeerConnection peerConnection;
    public void setConnection(JanusConnection connection) {
      this.connection = connection;
      this.peerConnection = connection.peerConnection;
    }
    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        events.onIceCandidate(candidate, connection.handleId);
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        events.onIceCandidatesRemoved(candidates);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        Log.d(TAG, "SignalingState: " + newState);
    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
        Log.d(TAG, "IceConnectionState: " + newState);
        if (newState == IceConnectionState.CONNECTED) {
          events.onIceConnected();
        } else if (newState == IceConnectionState.DISCONNECTED) {
          events.onIceDisconnected();
        } else if (newState == IceConnectionState.FAILED) {
          reportError("ICE connection failed.");
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
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          Log.d(TAG, "=========== onAddStream ==========");
          if (stream.videoTracks.size() == 1) {
            remoteVideoTrack = stream.videoTracks.get(0);
            remoteVideoTrack.setEnabled(true);
            connection.videoTrack = remoteVideoTrack;
            events.onRemoteRender(connection);
          }
        }
      });
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
      remoteVideoTrack = null;
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

  class SDPObserver implements SdpObserver {
    private PeerConnection peerConnection;
    private SDPObserver sdpObserver;
    private BigInteger handleId;
    private SessionDescription localSdp;
    private boolean type;
    public void setConnection(JanusConnection connection) {
      this.peerConnection = connection.peerConnection;
      this.sdpObserver = connection.sdpObserver;
      this.handleId = connection.handleId;
      this.type = connection.type;
    }
    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
      Log.e(TAG, "SDP on create success");
      final SessionDescription sdp = new SessionDescription(origSdp.type, origSdp.description);
      localSdp = sdp;
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection != null && !isError) {
            Log.d(TAG, "Set local SDP from " + sdp.type);
            peerConnection.setLocalDescription(sdpObserver, sdp);
          }
        }
      });
    }

    @Override
    public void onSetSuccess() {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          if (type) {
            if (peerConnection.getRemoteDescription() == null) {
              Log.d(TAG, "Local SDP set succesfully");
              events.onLocalDescription(localSdp, handleId);
            } else {
              Log.d(TAG, "Remote SDP set succesfully");
            }
          } else {
            if (peerConnection.getLocalDescription() != null) {
              Log.d(TAG, "answer Local SDP set succesfully");
              events.onRemoteDescription(localSdp, handleId);
            } else {
              Log.d(TAG, "answer Remote SDP set succesfully");
            }
          }
        }
      });
    }

    @Override
    public void onCreateFailure(final String error) {
      reportError("createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
      reportError("setSDP error: " + error);
    }
  }
}
