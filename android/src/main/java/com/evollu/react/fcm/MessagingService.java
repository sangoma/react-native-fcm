package com.evollu.react.fcm;

import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

public class MessagingService extends FirebaseMessagingService {

    private static final String TAG = "MessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        Log.d(TAG, "Message received");
        Log.d(TAG,"Notification message: "+ remoteMessage.getData().toString());

        Intent i = new Intent("com.evollu.react.fcm.ReceiveNotification");
        i.putExtra("data", remoteMessage);

        handleBadge(remoteMessage);
        buildLocalNotification(remoteMessage);

        final Intent message = i;

        // We need to run this on the main thread, as the React code assumes that is true.
        // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
        // "Can't create handler inside thread that has not called Looper.prepare()"
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                // Construct and load our normal React JS code bundle
                ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();

                // If it's constructed, send a notification
                if (context != null) {
                    //APP IS IN LOADED
                    context.sendOrderedBroadcast(message, null);

                } else {
                    // Otherwise wait for construction, then send the notification
                    // LOAD REACT INSTANCE

                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            //App was killed by Android or User, needs to startActivity
                            launchApplication(context,"INCALL");
                            context.sendOrderedBroadcast(message, null);

                        }
                    });
                    if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                        // Construct it in the background
                        mReactInstanceManager.createReactContextInBackground();
                    }
                }
            }
        });

    }


    private void launchApplication(Context context, String codeID) {
        String packageName = context.getApplicationContext().getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.putExtra("codeID", codeID);
        context.startActivity(launchIntent);
        Log.i(TAG, "Start activity with extra: Launching: (" + packageName + ")  : " + codeID );
    }


    public void handleBadge(RemoteMessage remoteMessage) {
        BadgeHelper badgeHelper = new BadgeHelper(this);
        if (remoteMessage.getData() == null) {
            return;
        }

        Map data = remoteMessage.getData();
        if (data.get("badge") == null) {
            return;
        }

        try {
            int badgeCount = Integer.parseInt((String)data.get("badge"));
            badgeHelper.setBadgeCount(badgeCount);
        } catch (Exception e) {
            Log.e(TAG, "Badge count needs to be an integer", e);
        }
    }

    public void buildLocalNotification(RemoteMessage remoteMessage) {
        if(remoteMessage.getData() == null){
            return;
        }
        Map<String, String> data = remoteMessage.getData();
        String customNotification = data.get("custom_notification");
        if(customNotification != null){
            try {
                Bundle bundle = BundleJSONConverter.convertToBundle(new JSONObject(customNotification));
                FIRLocalMessagingHelper helper = new FIRLocalMessagingHelper(this.getApplication());
                helper.sendNotification(bundle);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }
}
