package com.easilydo.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.easilydo.smartreply.Sentiment;

import java.util.Random;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private TextView contentTv;
    private TextView replyPosTv;
    private TextView replyNeuTv;
    private TextView replyNegTv;
    private TextView timeTv;
    private Button normalButton;
    private Button badButton;
    private String[] examples;
    private String[] examplesSender;
    private String[] examplesRecipients;
    private String[] badExamples;
    private String[] badExamplesSender;
    private String[] badExamplesRecipients;
    private Random random = new Random();

    private Messenger smartReplyService = null;
    private boolean isSmartReplyServiceBound;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SmartReplyService.MSG_REPLY:
                    update(msg.getData());
                    break;
                case SmartReplyService.MSG_TEST:
                    Bundle data = msg.getData();
                    double totalTime = data.getLong(SmartReplyService.MESSAGE_DATA_PROCESS_TIME) / 1000.0;
                    double avgTime = data.getLong(SmartReplyService.MESSAGE_DATA_PROCESS_AVG_TIME) / 1000.0;
                    String toast = String.format("Total: %f s, Avg: %f s", totalTime, avgTime);
                    Log.i(TAG, toast);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger messenger = new Messenger(new IncomingHandler());

    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            smartReplyService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, SmartReplyService.MSG_REGISTER_CLIENT);
                msg.replyTo = messenger;
                smartReplyService.send(msg);
            } catch (RemoteException e) {}

            //test();
            demo(random.nextInt(examples.length), false);
        }

        public void onServiceDisconnected(ComponentName className) {
            smartReplyService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contentTv = (TextView) findViewById(R.id.content);
        replyPosTv = (TextView) findViewById(R.id.reply_pos);
        replyNeuTv = (TextView) findViewById(R.id.reply_neu);
        replyNegTv = (TextView) findViewById(R.id.reply_neg);
        timeTv = (TextView) findViewById(R.id.time);

        examples = getResources().getStringArray(R.array.examples);
        badExamples = getResources().getStringArray(R.array.bad_examples);
        examplesSender = getResources().getStringArray(R.array.examples_sender);
        badExamplesSender = getResources().getStringArray(R.array.bad_examples_sender);
        examplesRecipients = getResources().getStringArray(R.array.examples_recipients);
        badExamplesRecipients = getResources().getStringArray(R.array.bad_examples_recipients);
        normalButton = (Button) findViewById(R.id.reload);
        badButton = (Button) findViewById(R.id.reload_bad);

        normalButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                demo(random.nextInt(examples.length), false);
            }
        });

        badButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                demo(random.nextInt(badExamples.length), true);
            }
        });

        Intent serviceIntent = new Intent(this, SmartReplyService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        isSmartReplyServiceBound = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    private void test() {
        final String content = examples[3] + examples[3] + examples[3];
        //final String content = badExamples[0];
        Message msg = Message.obtain(null, SmartReplyService.MSG_TEST);
        Bundle data = new Bundle();
        data.putString(SmartReplyService.MESSAGE_DATA_CONTENT, content);
        msg.setData(data);
        try {
            smartReplyService.send(msg);
        } catch (RemoteException e) {
            Toast.makeText(this, "Remote Service Carshed", Toast.LENGTH_SHORT).show();
        }
    }

    private void demo(int exampleId, boolean useBadCase) {
        String content;
        String sender;
        String recipients;
        if (useBadCase) {
            content = badExamples[exampleId];
            sender = badExamplesSender[exampleId];
            recipients = badExamplesRecipients[exampleId];
        } else {
            content = examples[exampleId];
            sender = examplesSender[exampleId];
            recipients = examplesRecipients[exampleId];
        }
        normalButton.setEnabled(false);
        badButton.setEnabled(false);
        timeTv.setVisibility(View.INVISIBLE);
        contentTv.setText(content);
        replyPosTv.setText(Sentiment.POS.name() + ": Processing...");
        replyNeuTv.setText(Sentiment.NEU.name() + ": Processing...");
        replyNegTv.setText(Sentiment.NEG.name() + ": Processing...");

        Message msg = Message.obtain(null, SmartReplyService.MSG_REPLY);
        Bundle data = new Bundle();
        data.putString(SmartReplyService.MESSAGE_DATA_CONTENT, content);
        data.putString(SmartReplyService.MESSAGE_DATA_SENDER, sender);
        data.putString(SmartReplyService.MESSAGE_DATA_RECIPIENTS, recipients);
        msg.setData(data);
        try {
            smartReplyService.send(msg);
        } catch (RemoteException e) {
            Toast.makeText(this, "Remote Service Carshed", Toast.LENGTH_SHORT).show();
        }
    }

    private void update(Bundle resultData) {
        final String content = resultData.getString(SmartReplyService.MESSAGE_DATA_CONTENT);
        final String replyPos = resultData.getString(SmartReplyService.MESSAGE_DATA_REPLY_POS);
        final String replyNeu = resultData.getString(SmartReplyService.MESSAGE_DATA_REPLY_NEU);
        final String replyNeg = resultData.getString(SmartReplyService.MESSAGE_DATA_REPLY_NEG);
        final long processTime = resultData.getLong(SmartReplyService.MESSAGE_DATA_PROCESS_TIME);
        contentTv.setText(content);
        replyPosTv.setText(Sentiment.POS.name() + ": " + replyPos);
        replyNeuTv.setText(Sentiment.NEU.name() + ": " + replyNeu);
        replyNegTv.setText(Sentiment.NEG.name() + ": " + replyNeg);
        timeTv.setText("Process time: " + String.valueOf(processTime / 10.0e3) + 's');
        timeTv.setVisibility(View.VISIBLE);
        normalButton.setEnabled(true);
        badButton.setEnabled(true);
    }

    void unbindService() {
        if (isSmartReplyServiceBound) {
            if (smartReplyService != null) {
                try {
                    Message msg = Message.obtain(null,
                            SmartReplyService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = messenger;
                    smartReplyService.send(msg);
                } catch (RemoteException e) {}
            }
            unbindService(connection);
            isSmartReplyServiceBound = false;
        }
    }
}
