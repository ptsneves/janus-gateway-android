package in.minewave.janusvideoroom.Janus;

import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;

interface TransactionCallbackSuccess {
    void success(JSONObject jo);
}

interface TransactionCallbackError {
    void error(JSONObject jo);
}


public class JanusTransactions {
    private ConcurrentHashMap<String, JanusTransaction> transactions = new ConcurrentHashMap<>();
    public static boolean isTransaction(JSONObject jsonObject) {
        String response_type = jsonObject.optString("janus");
        return response_type.equals("success") ||
                response_type.equals("error") ||
                response_type.equals("ack");
    }

    public void processTransaction(JSONObject jsonObject) {
        String response_type = jsonObject.optString("janus");
        if (response_type.equals("success")) {
            String transaction = jsonObject.optString("transaction");
            JanusTransaction jt = transactions.get(transaction);
            if (jt.success != null) {
                jt.success.success(jsonObject);
            }
            transactions.remove(transaction);
        } else if (response_type.equals("error")) {
            String transaction = jsonObject.optString("transaction");
            JanusTransaction jt = transactions.get(transaction);
            if (jt.error != null) {
                jt.error.error(jsonObject);
            }
            transactions.remove(transaction);
        } else if (response_type.equals("ack")) {
            Log.d("JanusTransaction", "Just an ack");
        }
    }

    public void addTransaction(JanusTransaction transaction) {
        transactions.put(transaction.tid, transaction);
    }

    public class JanusTransaction {
        public String tid;
        public TransactionCallbackSuccess success;
        public TransactionCallbackError error;
    }
}
