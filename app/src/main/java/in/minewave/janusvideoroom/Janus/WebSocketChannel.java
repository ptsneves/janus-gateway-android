package in.minewave.janusvideoroom.Janus;

import android.app.Activity;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.InvalidObjectException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;

public class WebSocketChannel extends WebSocketClient {
    private static final String TAG = "WebSocketChannel";
    private JanusTransactions janusTransactions = new JanusTransactions();
    private ConcurrentHashMap<BigInteger, JanusHandle> handles = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> feeds = new ConcurrentHashMap<>();
    private Handler mHandler;
    private BigInteger mSessionId;
    private JanusRTCInterface delegate;
    private Activity _activity;

    public static WebSocketChannel createWebSockeChannel(Activity activity, String url) throws URISyntaxException, InterruptedException, InvalidObjectException {
        Draft_6455 janus_draft = new Draft_6455(Collections.<IExtension>emptyList(),
                Collections.<IProtocol>singletonList(new Protocol("janus-protocol")));
        return new WebSocketChannel(activity, url, janus_draft);
    }

    private WebSocketChannel(Activity activity, String url, Draft_6455 janus_draft) throws URISyntaxException, InterruptedException, InvalidObjectException  {
        super(new URI(url), janus_draft);
        mHandler = new Handler();
        _activity = activity;
        if (!connectBlocking(10, TimeUnit.SECONDS))
            throw new InvalidObjectException("Could not connect to janus");
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        String transaction = randomString(12);
        JanusTransactions.JanusTransaction jt = janusTransactions.new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            mSessionId = new BigInteger(jo.optJSONObject("data").optString("id"));
            mHandler.post(fireKeepAlive);
            publisherCreateHandle();
        };
        jt.error = jo -> {};
        janusTransactions.addTransaction(jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "create");
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(msg.toString());
    }

    @Override
    public void onMessage(String message) {
        _activity.runOnUiThread(() -> {
            Log.e(TAG, "onMessage" + message);
            try {
                JSONObject jo = new JSONObject(message);
                String janus = jo.optString("janus");
                if (JanusTransactions.isTransaction(jo)) {
                    janusTransactions.processTransaction(jo);
                } else {
                    JanusHandle handle = handles.get(new BigInteger(jo.optString("sender")));
                    if (handle == null) {
                        Log.e(TAG, "missing handle");
                    } else if (janus.equals("event")) {
                        JSONObject plugin = jo.optJSONObject("plugindata").optJSONObject("data");
                        if (plugin.optString("videoroom").equals("joined")) {
                            handle.onJoined.onJoined(handle);
                        }

                        JSONArray publishers = plugin.optJSONArray("publishers");
                        if (publishers != null && publishers.length() > 0) {
                            for (int i = 0, size = publishers.length(); i <= size - 1; i++) {
                                JSONObject publisher = publishers.optJSONObject(i);
                                BigInteger feed = new BigInteger(publisher.optString("id"));
                                String display = publisher.optString("display");
                                subscriberCreateHandle(feed, display);
                            }
                        }

                        String leaving = plugin.optString("leaving");
                        if (!TextUtils.isEmpty(leaving)) {
                            JanusHandle jhandle = feeds.get(new BigInteger(leaving));
                            jhandle.onLeaving.onJoined(jhandle);
                        }

                        JSONObject jsep = jo.optJSONObject("jsep");
                        if (jsep != null) {
                            handle.onRemoteJsep.onRemoteJsep(handle, jsep);
                        }

                    } else if (janus.equals("detached")) {
                        handle.onLeaving.onJoined(handle);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    );
    }

    private void publisherCreateHandle() {
        String transaction = randomString(12);
        JanusTransactions.JanusTransaction jt = janusTransactions.new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            JanusHandle janusHandle = new JanusHandle();
            janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
            janusHandle.onJoined = jh -> delegate.onPublisherJoined(jh.handleId);
            janusHandle.onRemoteJsep = (jh, jsep) -> delegate.onPublisherRemoteJsep(jh.handleId, jsep);
            handles.put(janusHandle.handleId, janusHandle);
            publisherJoinRoom(janusHandle);
        };
        jt.error = jo -> {};
        janusTransactions.addTransaction(jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(msg.toString());
    }

    private void publisherJoinRoom(JanusHandle handle) {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", 1234);
            body.putOpt("ptype", "publisher");
            body.putOpt("display", "Android webrtc");

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString(12));
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(msg.toString());
    }

    public void publisherCreateOffer(final BigInteger handleId, final SessionDescription sdp) {
        JSONObject publish = new JSONObject();
        JSONObject jsep = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            publish.putOpt("request", "configure");
            publish.putOpt("audio", true);
            publish.putOpt("video", true);

            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);

            message.putOpt("janus", "message");
            message.putOpt("body", publish);
            message.putOpt("jsep", jsep);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(message.toString());
    }

    public void subscriberCreateAnswer(final BigInteger handleId, final SessionDescription sdp) {
        JSONObject body = new JSONObject();
        JSONObject jsep = new JSONObject();
        JSONObject message = new JSONObject();

        try {
            body.putOpt("request", "start");
            body.putOpt("room", 1234);

            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);
            message.putOpt("janus", "message");
            message.putOpt("body", body);
            message.putOpt("jsep", jsep);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
            Log.e(TAG, "-------------"  + message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        send(message.toString());
    }

    public void trickleCandidate(final BigInteger handleId, final IceCandidate iceCandidate) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("candidate", iceCandidate.sdp);
            candidate.putOpt("sdpMid", iceCandidate.sdpMid);
            candidate.putOpt("sdpMLineIndex", iceCandidate.sdpMLineIndex);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(message.toString());
    }

    public void trickleCandidateComplete(final BigInteger handleId) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("completed", true);
            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void subscriberCreateHandle(final BigInteger feed, final String display) {
        String transaction = randomString(12);
        JanusTransactions.JanusTransaction jt = janusTransactions.new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            JanusHandle janusHandle = new JanusHandle();
            janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
            janusHandle.feedId = feed;
            janusHandle.display = display;
            janusHandle.onRemoteJsep = (jh, jsep) -> delegate.subscriberHandleRemoteJsep(jh.handleId, jsep);
            janusHandle.onLeaving = jh -> subscriberOnLeaving(jh);
            handles.put(janusHandle.handleId, janusHandle);
            feeds.put(janusHandle.feedId, janusHandle);
            subscriberJoinRoom(janusHandle);
        };
        jt.error = jo -> {};
        janusTransactions.addTransaction(jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        send(msg.toString());
    }

    private void subscriberJoinRoom(JanusHandle handle) {

        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", 1234);
            body.putOpt("ptype", "listener");
            body.putOpt("feed", handle.feedId);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString(12));
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(msg.toString());
    }

    private void subscriberOnLeaving(final JanusHandle handle) {
        String transaction = randomString(12);
        JanusTransactions.JanusTransaction jt = janusTransactions.new JanusTransaction();
        jt.tid = transaction;
        jt.success = jo -> {
            delegate.onLeaving(handle.handleId);
            handles.remove(handle.handleId);
            feeds.remove(handle.feedId);
        };
        jt.error = jo -> {};

        janusTransactions.addTransaction(jt);

        JSONObject jo = new JSONObject();
        try {
            jo.putOpt("janus", "detach");
            jo.putOpt("transaction", transaction);
            jo.putOpt("session_id", mSessionId);
            jo.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(jo.toString());
    }

    private void keepAlive() {
        String transaction = randomString(12);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "keepalive");
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(msg.toString());
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            mHandler.postDelayed(fireKeepAlive, 30000);
        }
    };

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.e(TAG, "Connection closed by " + ( remote ? "remote peer" : "us" ) + " Code: " + code + " Reason: " + reason );
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "onFailure " + ex.getMessage());
        ex.printStackTrace();
    }

    public void setDelegate(JanusRTCInterface delegate) {
        this.delegate = delegate;
    }

    private String randomString(Integer length) {
        final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random rnd = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(str.charAt(rnd.nextInt(str.length())));
        }
        return sb.toString();
    }
}
