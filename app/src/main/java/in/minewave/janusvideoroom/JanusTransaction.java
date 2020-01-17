package in.minewave.janusvideoroom;

import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

interface TransactionCallbackSuccess {
    void success(JSONObject jo);
}

interface TransactionCallbackError {
    void error(JSONObject jo);
}


public class JanusTransaction {
    public static boolean isTransaction(JSONObject jsonObject) {
        String resposnse_type = jsonObject.optString("janus");
        return resposnse_type.equals("success") ||
                resposnse_type.equals("error") ||
                resposnse_type.equals("ack");
    }

    public static void processTransaction(JSONObject jsonObject, ConcurrentHashMap<String, JanusTransaction> transactions) {
        String resposnse_type = jsonObject.optString("janus");
        if (resposnse_type.equals("success")) {
            String transaction = jsonObject.optString("transaction");
            JanusTransaction jt = transactions.get(transaction);
            if (jt.success != null) {
                jt.success.success(jsonObject);
            }
            transactions.remove(transaction);
        } else if (resposnse_type.equals("error")) {
            String transaction = jsonObject.optString("transaction");
            JanusTransaction jt = transactions.get(transaction);
            if (jt.error != null) {
                jt.error.error(jsonObject);
            }
            transactions.remove(transaction);
        } else if (resposnse_type.equals("ack")) {
            Log.d("JanusTransaction", "Just an ack");
        }
    }

    public String tid;
    public TransactionCallbackSuccess success;
    public TransactionCallbackError error;
}
