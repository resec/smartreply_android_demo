package com.easilydo.app;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.easilydo.smartreply.Sentiment;
import com.easilydo.smartreply.SmartReplyer;
import com.easilydo.smartreply.signature.SignatureProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SmartReplyService extends Service {

    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_REPLY = 3;
    static final int MSG_TEST = 4;

    public static final String MESSAGE_DATA_CONTENT = SmartReplyService.class.getSimpleName() + "MessageDataContent";
    public static final String MESSAGE_DATA_SENDER = SmartReplyService.class.getSimpleName() + "MessageDataSender";
    public static final String MESSAGE_DATA_RECIPIENTS = SmartReplyService.class.getSimpleName() + "MessageDataRecipients";
    public static final String MESSAGE_DATA_REPLY_POS = SmartReplyService.class.getSimpleName() + "MessageDataReplyPos";
    public static final String MESSAGE_DATA_REPLY_NEU = SmartReplyService.class.getSimpleName() + "MessageDataReplyNeu";
    public static final String MESSAGE_DATA_REPLY_NEG = SmartReplyService.class.getSimpleName() + "MessageDataReplyNeg";
    public static final String MESSAGE_DATA_PROCESS_TIME = SmartReplyService.class.getSimpleName() + "MessageDataProcessTime";
    public static final String MESSAGE_DATA_PROCESS_AVG_TIME = SmartReplyService.class.getSimpleName() + "MessageDataAverageProcessTime";

    private static final String TAG = SmartReplyService.class.getSimpleName();

    private static SignatureProcessor signatureProcessor = new SignatureProcessor();
    private static SmartReplyer smartReplyer;

    private List<Messenger> clients = new ArrayList<>();

    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    clients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    clients.remove(msg.replyTo);
                    break;
                case MSG_REPLY: {
                    Bundle data = msg.getData();
                    final String content = data.getString(MESSAGE_DATA_CONTENT);
                    if (content == null) {
                        break;
                    }
                    final String sender = data.getString(MESSAGE_DATA_SENDER);
                    final String[] recipients = data.getString(MESSAGE_DATA_RECIPIENTS).split("\\s*,\\s*");
                    final String cleaned = signatureProcessor.removeSignature(content, sender, recipients);
                    final long start = System.currentTimeMillis();
                    final Map<Sentiment, String> replyMap = smartReplyer.reply(cleaned);
                    final long end = System.currentTimeMillis();
                    final Bundle bundle = new Bundle();
                    bundle.putString(MESSAGE_DATA_CONTENT, content);
                    bundle.putString(MESSAGE_DATA_REPLY_POS, replyMap.get(Sentiment.POS));
                    bundle.putString(MESSAGE_DATA_REPLY_NEU, replyMap.get(Sentiment.NEU));
                    bundle.putString(MESSAGE_DATA_REPLY_NEG, replyMap.get(Sentiment.NEG));
                    bundle.putLong(MESSAGE_DATA_PROCESS_TIME, end - start);
                    Message replyMessage = Message.obtain(null, MSG_REPLY);
                    replyMessage.setData(bundle);
                    for (int i = 0; i < clients.size(); i++) {
                        try {
                            clients.get(i).send(replyMessage);
                        } catch (RemoteException e) {
                            clients.remove(i);
                        }
                    }
                }
                    break;
                case MSG_TEST: {
                    Bundle data = msg.getData();
                    final String content = data.getString(MESSAGE_DATA_CONTENT);
                    if (content == null) {
                        break;
                    }
                    final long start = System.currentTimeMillis();
                    Map<Sentiment, String> replyMap;
                    final int iter = 10;
                    for (int i = 0; i < iter; i++) {
                        replyMap = smartReplyer.reply(content);
                    }
                    final long end = System.currentTimeMillis();
                    final Bundle bundle = new Bundle();
                    bundle.putLong(MESSAGE_DATA_PROCESS_TIME, end - start);
                    bundle.putLong(MESSAGE_DATA_PROCESS_AVG_TIME, (end - start) / iter);
                    Message testMessage = Message.obtain(null, MSG_TEST);
                    testMessage.setData(bundle);
                    for (int i = 0; i < clients.size(); i++) {
                        try {
                            clients.get(i).send(testMessage);
                        } catch (RemoteException e) {
                            clients.remove(i);
                        }
                    }
                }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger messenger = new Messenger(new IncomingHandler());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        smartReplyer = new SmartReplyer(getAssets());
    }
}
