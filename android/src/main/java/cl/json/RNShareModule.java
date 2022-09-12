package cl.json;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import java.util.HashMap;
import java.util.Map;

import cl.json.social.EmailShare;
import cl.json.social.FacebookStoriesShare;
import cl.json.social.FacebookPagesManagerShare;
import cl.json.social.FacebookShare;
import cl.json.social.GenericShare;
import cl.json.social.GooglePlusShare;
import cl.json.social.InstagramShare;
import cl.json.social.PinterestShare;
import cl.json.social.SMSShare;
import cl.json.social.MessengerShare;
import cl.json.social.LinkedinShare;
import cl.json.social.ShareIntent;
import cl.json.social.SnapChatShare;
import cl.json.social.TargetChosenReceiver;
import cl.json.social.TelegramShare;
import cl.json.social.TwitterShare;
import cl.json.social.WhatsAppShare;
import cl.json.social.WhatsAppBusinessShare;
import cl.json.social.InstagramStoriesShare;

public class RNShareModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public static final int SHARE_REQUEST_CODE = 16845;
    public static final int SHARE_FINISHED_CODE = 16846;
    private final ReactApplicationContext reactContext;
    private String pendingShareAppName = null;

    // removed @Override temporarily just to get it working on different versions of
    // RN
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        // onActivityResult(requestCode, resultCode, data);
        if (requestCode == SHARE_REQUEST_CODE) {
            if (pendingShareAppName != null) {
                TargetChosenReceiver.sendCallback(false, "another_share_pending");
                return;
            }
            if (resultCode != Activity.RESULT_OK) {
                TargetChosenReceiver.sendCallback(false, "share_intent_cancelled");
                return;
            }
            if (data == null || data.getComponent() == null
                    || TextUtils.isEmpty(data.getComponent().flattenToShortString())) {
                TargetChosenReceiver.sendCallback(false, "unknown_application");
                return;
            }
            pendingShareAppName = data.getComponent().flattenToShortString();
            activity.startActivityForResult(data, SHARE_FINISHED_CODE); // Start the selected activity
        }
        if (requestCode == SHARE_FINISHED_CODE) {
            if (pendingShareAppName == null) {
                TargetChosenReceiver.sendCallback(false, "pending_share_lost");
                return;
            }
            TargetChosenReceiver.sendCallback(true, true, pendingShareAppName);
            pendingShareAppName = null;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    private enum SHARES {
        facebook,
        facebookstories,
        generic,
        pagesmanager,
        twitter,
        whatsapp,
        whatsappbusiness,
        instagram,
        instagramstories,
        googleplus,
        email,
        pinterest,
        messenger,
        snapchat,
        sms,
        linkedin,
        telegram;

        public static ShareIntent getShareClass(String social, ReactApplicationContext reactContext) {
            SHARES share = valueOf(social);
            switch (share) {
                case generic:
                    return new GenericShare(reactContext);
                case facebook:
                    return new FacebookShare(reactContext);
                case facebookstories:
                    return new FacebookStoriesShare(reactContext);
                case pagesmanager:
                    return new FacebookPagesManagerShare(reactContext);
                case twitter:
                    return new TwitterShare(reactContext);
                case whatsapp:
                    return new WhatsAppShare(reactContext);
                case whatsappbusiness:
                    return new WhatsAppBusinessShare(reactContext);
                case instagram:
                    return new InstagramShare(reactContext);
                case instagramstories:
                    return new InstagramStoriesShare(reactContext);
                case googleplus:
                    return new GooglePlusShare(reactContext);
                case email:
                    return new EmailShare(reactContext);
                case pinterest:
                    return new PinterestShare(reactContext);
                case sms:
                    return new SMSShare(reactContext);
                case snapchat:
                    return new SnapChatShare(reactContext);
                case messenger:
                    return new MessengerShare(reactContext);
                case linkedin:
                    return new LinkedinShare(reactContext);
                case telegram:
                    return new TelegramShare(reactContext);
                default:
                    return null;
            }
        }
    };

    public RNShareModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNShare";
    }

    @javax.annotation.Nullable
    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        for (SHARES val : SHARES.values()) {
            constants.put(val.toString().toUpperCase(), val.toString());
        }
        return constants;
    }

    @ReactMethod
    public void open(ReadableMap options, @Nullable Callback failureCallback, @Nullable Callback successCallback) {
        if (pendingShareAppName != null && failureCallback != null) {
            failureCallback.invoke("another_share_pending");
            return;
        }
        TargetChosenReceiver.registerCallbacks(successCallback, failureCallback);
        try {
            GenericShare share = new GenericShare(this.reactContext);
            share.open(options);
        } catch (ActivityNotFoundException ex) {
            System.out.println("ERROR " + ex.getMessage());
            ex.printStackTrace(System.out);
            TargetChosenReceiver.sendCallback(false, "not_available");
        } catch (Exception e) {
            System.out.println("ERROR " + e.getMessage());
            e.printStackTrace(System.out);
            TargetChosenReceiver.sendCallback(false, e.getMessage());
        }
    }

    @ReactMethod
    public void shareSingle(ReadableMap options, @Nullable Callback failureCallback,
            @Nullable Callback successCallback) {
        System.out.println("SHARE SINGLE METHOD");
        TargetChosenReceiver.registerCallbacks(successCallback, failureCallback);
        if (ShareIntent.hasValidKey("social", options)) {
            try {
                ShareIntent shareClass = SHARES.getShareClass(options.getString("social"), this.reactContext);
                if (shareClass != null && shareClass instanceof ShareIntent) {
                    shareClass.open(options);
                } else {
                    throw new ActivityNotFoundException("Invalid share activity");
                }
            } catch (ActivityNotFoundException ex) {
                System.out.println("ERROR " + ex.getMessage());
                ex.printStackTrace(System.out);
                TargetChosenReceiver.sendCallback(false, ex.getMessage());
            } catch (Exception e) {
                System.out.println("ERROR " + e.getMessage());
                e.printStackTrace(System.out);
                TargetChosenReceiver.sendCallback(false, e.getMessage());
            }
        } else {
            TargetChosenReceiver.sendCallback(false, "key 'social' missing in options");
        }
    }

    @ReactMethod
    public void isPackageInstalled(String packagename, @Nullable Callback failureCallback,
            @Nullable Callback successCallback) {
        try {
            boolean res = ShareIntent.isPackageInstalled(packagename, this.reactContext);
            successCallback.invoke(res);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            failureCallback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void isBase64File(String url, @Nullable Callback failureCallback, @Nullable Callback successCallback) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if ((scheme != null) && scheme.equals("data")) {
                successCallback.invoke(true);
            } else {
                successCallback.invoke(false);
            }
        } catch (Exception e) {
            System.out.println("ERROR " + e.getMessage());
            e.printStackTrace(System.out);
            failureCallback.invoke(e.getMessage());
        }
    }
}
