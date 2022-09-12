package cl.json.social;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Parcelable;
import android.text.TextUtils;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import cl.json.RNShareModule;
import cl.json.ShareFile;
import cl.json.ShareFiles;

/**
 * Created by disenodosbbcl on 23-07-16.
 */
public abstract class ShareIntent {

    protected final ReactApplicationContext reactContext;
    protected Intent intent;
    protected String chooserTitle = "Share";
    protected ShareFile fileShare;
    protected ReadableMap options;
    protected ShareFile stickerAsset;
    protected ShareFile backgroundAsset;

    public ShareIntent(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
        this.setIntent(new Intent(android.content.Intent.ACTION_SEND));
        this.getIntent().setType("text/plain");
    }

    public Intent excludeChooserIntent(Intent prototype, ReadableMap options) {
        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        List<HashMap<String, String>> intentMetaInfo = new ArrayList<HashMap<String, String>>();
        Intent chooserIntent;

        Intent dummy = new Intent(prototype.getAction());
        dummy.setType(prototype.getType());
        List<ResolveInfo> resInfo = this.reactContext.getPackageManager().queryIntentActivities(dummy, 0);

        if (!resInfo.isEmpty()) {
            for (ResolveInfo resolveInfo : resInfo) {
                if (resolveInfo.activityInfo == null || options.getArray("excludedActivityTypes").toString()
                        .contains(resolveInfo.activityInfo.packageName))
                    continue;

                HashMap<String, String> info = new HashMap<String, String>();
                info.put("packageName", resolveInfo.activityInfo.packageName);
                info.put("className", resolveInfo.activityInfo.name);
                info.put("simpleName",
                        String.valueOf(resolveInfo.activityInfo.loadLabel(this.reactContext.getPackageManager())));
                intentMetaInfo.add(info);
            }

            if (!intentMetaInfo.isEmpty()) {
                // sorting for nice readability
                Collections.sort(intentMetaInfo, new Comparator<HashMap<String, String>>() {
                    @Override
                    public int compare(HashMap<String, String> map, HashMap<String, String> map2) {
                        return map.get("simpleName").compareTo(map2.get("simpleName"));
                    }
                });

                // create the custom intent list
                for (HashMap<String, String> metaInfo : intentMetaInfo) {
                    Intent targetedShareIntent = (Intent) prototype.clone();
                    targetedShareIntent.setPackage(metaInfo.get("packageName"));
                    targetedShareIntent.setClassName(metaInfo.get("packageName"), metaInfo.get("className"));
                    targetedShareIntents.add(targetedShareIntent);
                }

                chooserIntent = Intent.createChooser(targetedShareIntents.remove(targetedShareIntents.size() - 1),
                        "share");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray(new Parcelable[] {}));
                return chooserIntent;
            }
        }

        return Intent.createChooser(prototype, "Share");
    }

    public void open(ReadableMap options) throws ActivityNotFoundException {
        this.options = options;

        if (ShareIntent.hasValidKey("subject", options)) {
            this.getIntent().putExtra(Intent.EXTRA_SUBJECT, options.getString("subject"));
        }

        if (ShareIntent.hasValidKey("email", options)) {
            this.getIntent().putExtra(Intent.EXTRA_EMAIL, new String[] { options.getString("email") });
        }

        if (ShareIntent.hasValidKey("title", options)) {
            this.chooserTitle = options.getString("title");
        }

        String message = "";
        if (ShareIntent.hasValidKey("message", options)) {
            message = options.getString("message");
        }

        String socialType = "";
        if (ShareIntent.hasValidKey("social", options)) {
            socialType = options.getString("social");
        }

        if (socialType.equals("sms")) {
            String recipient = options.getString("recipient");

            if (!recipient.isEmpty()) {
                this.getIntent().putExtra("address", recipient);
            }
        }

        if (socialType.equals("whatsapp")) {
            if (options.hasKey("whatsAppNumber")) {
                String whatsAppNumber = options.getString("whatsAppNumber");
                String chatAddress = whatsAppNumber + "@s.whatsapp.net";
                this.getIntent().putExtra("jid", chatAddress);
            }
        }

        if (socialType.equals("whatsappbusiness")) {
            if (options.hasKey("whatsAppNumber")) {
                String whatsAppNumber = options.getString("whatsAppNumber");
                String chatAddress = whatsAppNumber + "@s.whatsapp.net";
                this.getIntent().putExtra("jid", chatAddress);
            }
        }

        if (ShareIntent.hasValidKey("urls", options)) {

            ShareFiles fileShare = getFileShares(options);
            if (fileShare.isFile()) {
                ArrayList<Uri> uriFile = fileShare.getURI();
                this.getIntent().setAction(Intent.ACTION_SEND_MULTIPLE);
                this.getIntent().setType(fileShare.getType());
                this.getIntent().putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriFile);
                this.getIntent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (!TextUtils.isEmpty(message)) {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, message);
                }
            } else {
                if (!TextUtils.isEmpty(message)) {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, message + " " + options.getArray("urls").getString(0));
                } else {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, options.getArray("urls").getString(0));
                }
            }
        } else if (ShareIntent.hasValidKey("url", options)) {
            this.fileShare = getFileShare(options);
            if (this.fileShare.isFile()) {
                Uri uriFile = this.fileShare.getURI();
                this.getIntent().setType(this.fileShare.getType());
                this.getIntent().putExtra(Intent.EXTRA_STREAM, uriFile);
                this.getIntent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (!TextUtils.isEmpty(message)) {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, message);
                }
            } else {
                if (!TextUtils.isEmpty(message)) {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, message + " " + options.getString("url"));
                } else {
                    this.getIntent().putExtra(Intent.EXTRA_TEXT, options.getString("url"));
                }
            }
        } else if (!TextUtils.isEmpty(message)) {
            this.getIntent().putExtra(Intent.EXTRA_TEXT, message);
        }
    }

    protected ShareFile getFileShare(ReadableMap options) {
        String filename = null;
        if (ShareIntent.hasValidKey("filename", options)) {
            filename = options.getString("filename");
        }
        if (ShareIntent.hasValidKey("type", options)) {
            return new ShareFile(options.getString("url"), options.getString("type"), filename, this.reactContext);
        } else {
            return new ShareFile(options.getString("url"), filename, this.reactContext);
        }
    }

    protected ShareFiles getFileShares(ReadableMap options) {
        ArrayList<String> filenames = new ArrayList<>();
        if (ShareIntent.hasValidKey("filenames", options)) {
            ReadableArray fileNamesReadableArray = options.getArray("filenames");
            for (int i = 0; i < fileNamesReadableArray.size(); i++) {
                filenames.add(fileNamesReadableArray.getString(i));
            }
        }

        if (ShareIntent.hasValidKey("type", options)) {
            return new ShareFiles(options.getArray("urls"), filenames, options.getString("type"), this.reactContext);
        } else {
            return new ShareFiles(options.getArray("urls"), filenames, this.reactContext);
        }
    }

    protected static String urlEncode(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URLEncoder.encode() failed for " + param);
        }
    }

    protected Intent[] getIntentsToViewFile(Intent intent, Uri uri) {
        PackageManager pm = this.reactContext.getPackageManager();

        List<ResolveInfo> resInfo = pm.queryIntentActivities(intent, 0);
        Intent[] extraIntents = new Intent[resInfo.size()];
        for (int i = 0; i < resInfo.size(); i++) {
            ResolveInfo ri = resInfo.get(i);
            String packageName = ri.activityInfo.packageName;

            Intent newIntent = new Intent();
            newIntent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
            newIntent.setAction(Intent.ACTION_VIEW);
            newIntent.setDataAndType(uri, intent.getType());
            newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            extraIntents[i] = new Intent(newIntent);
        }

        return extraIntents;
    }

    protected void openIntentChooser() throws ActivityNotFoundException {
        Activity activity = this.reactContext.getCurrentActivity();
        if (activity == null) {
            TargetChosenReceiver.sendCallback(false, "main_activity_null");
            return;
        }
        List<Intent> targetedShareIntents = new ArrayList<>();
        List<ResolveInfo> resInfo = reactContext.getPackageManager().queryIntentActivities(this.getIntent(), 0);
        if (!resInfo.isEmpty()) {
            for (ResolveInfo info : resInfo) {
                Intent targetedShare = new Intent(android.content.Intent.ACTION_SEND);
                targetedShare.setPackage(info.activityInfo.packageName.toLowerCase());
                targetedShare.setType("text/plain"); // put here your mime type
                targetedShareIntents.add(targetedShare);
            }
            // Then show the ACTION_PICK_ACTIVITY to let the user select it
            Intent intentPick = new Intent();
            intentPick.setAction(Intent.ACTION_PICK_ACTIVITY);
            // Set the title of the dialog
            intentPick.putExtra(Intent.EXTRA_TITLE, this.chooserTitle);
            intentPick.putExtra(Intent.EXTRA_INTENT, this.getIntent());
            intentPick.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray());
            // Call StartActivityForResult so we can get the app name selected by the user
            intentPick.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            activity.startActivityForResult(intentPick, RNShareModule.SHARE_REQUEST_CODE);
        } else {
            TargetChosenReceiver.sendCallback(false, "no_apps_available");
        }
    }

    public static boolean isPackageInstalled(String packagename, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    protected Intent getIntent() {
        return this.intent;
    }

    protected void setIntent(Intent intent) {
        this.intent = intent;
    }

    public static boolean hasValidKey(String key, ReadableMap options) {
        return options != null && options.hasKey(key) && !options.isNull(key);
    }

    protected abstract String getPackage();

    protected String getComponentClass() {
        return null;
    }

    protected abstract String getDefaultWebLink();

    protected abstract String getPlayStoreLink();

    private ComponentName[] getExcludedComponentArray(ReadableArray excludeActivityTypes) {
        if (excludeActivityTypes == null) {
            return null;
        }
        Intent dummy = new Intent(getIntent().getAction());
        dummy.setType(getIntent().getType());
        List<ComponentName> componentNameList = new ArrayList<>();
        List<ResolveInfo> resInfoList = this.reactContext.getPackageManager().queryIntentActivities(dummy, 0);
        for (int index = 0; index < excludeActivityTypes.size(); index++) {
            String packageName = excludeActivityTypes.getString(index);
            for (ResolveInfo resInfo : resInfoList) {
                if (resInfo.activityInfo.packageName.equals(packageName)) {
                    componentNameList
                            .add(new ComponentName(resInfo.activityInfo.packageName, resInfo.activityInfo.name));
                }
            }
        }
        return componentNameList.toArray(new ComponentName[] {});
    }
}
