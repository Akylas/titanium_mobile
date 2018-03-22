/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiApplication.ActivityTransitionListener;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiMimeTypeHelper;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.filesystem.FileProxy;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.Html;

@Kroll.proxy(creatableInModule = UIModule.class, propertyAccessors = {
        "bccRecipients", "ccRecipients", "html", "messageBody", "subject",
        "toRecipients" })
public class EmailDialogProxy extends ViewProxy
        implements ActivityTransitionListener {

	private static final String TAG = "EmailDialogProxy";

	@Kroll.constant
	public static final int CANCELLED = 0;
	@Kroll.constant
	public static final int SAVED = 1;
	@Kroll.constant
	public static final int SENT = 2;
	@Kroll.constant
	public static final int FAILED = 3;

	private ArrayList<Object> attachments;
	private String privateDataDirectoryPath = null;

    public EmailDialogProxy() {
		super();

        TiBaseFile privateDataDirectory = TiFileFactory
                .createTitaniumFile("appdata-private:///", false);
        privateDataDirectoryPath = privateDataDirectory.getNativeFile()
                .getAbsolutePath();
	}

	@Kroll.method
    public boolean isSupported() {
		boolean supported = false;
		Activity activity = TiApplication.getAppRootOrCurrentActivity();
		if (activity != null) {
			PackageManager pm = activity.getPackageManager();
			if (pm != null) {
				Intent intent = buildIntent();
                List<ResolveInfo> activities = pm.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
				if (activities != null && activities.size() > 0) {
					supported = true;
                    Log.d(TAG, "Number of activities that support ACTION_SEND: "
                            + activities.size(), Log.DEBUG_MODE);
				}
			}
		}

		return supported;
	}

	@Kroll.method
    public void addAttachment(Object attachment,
            @Kroll.argument(optional = true) HashMap options) {
        if (attachment instanceof FileProxy || attachment instanceof TiBlob || attachment instanceof HashMap) {
			if (attachments == null) {
				attachments = new ArrayList<Object>();
			}
			attachments.add(attachment);
		} else {
			// silently ignore?
			Log.d(TAG,
				  "addAttachment for type " + attachment.getClass().getName()
					  + " ignored. Only files and blobs may be attached.",
				  Log.DEBUG_MODE);
		}
	}

    private String baseMimeType(boolean isHtml) {
		String result = isHtml ? "text/html" : "text/plain";
        // After 1.6 (api 4, "Donut"), message/rfc822 will work and still
        // recognize
        // html encoded text. The advantage to putting it to message/rfc822 is
        // that
        // it will most likely "force" the e-mail dialog as opposed to the
        // intent leading
        // to a chooser that might include other applications that can handle
        // text/plain
        // or text/html. Since this is all for our "EmailDialogProxy", we want
        // to
		// force the e-mail dialog as much as possible.
		if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.DONUT) {
			result = "message/rfc822";
		}
		return result;
	}

    private Intent buildIntent() {
		ArrayList<Uri> uris = getAttachmentUris();
        Intent sendIntent = new Intent((uris != null && uris.size() > 1)
                ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
		boolean isHtml = false;
		if (hasProperty("html")) {
			isHtml = TiConvert.toBoolean(getProperty("html"));
		}
		String intentType = baseMimeType(isHtml);
		sendIntent.setType(intentType);
		putAddressExtra(sendIntent, Intent.EXTRA_EMAIL, "toRecipients");
		putAddressExtra(sendIntent, Intent.EXTRA_CC, "ccRecipients");
		putAddressExtra(sendIntent, Intent.EXTRA_BCC, "bccRecipients");
		putStringExtra(sendIntent, Intent.EXTRA_SUBJECT, "subject");
		putStringExtra(sendIntent, Intent.EXTRA_TEXT, "messageBody", isHtml);
		prepareAttachments(sendIntent, uris);

        Log.d(TAG, "Choosing for mime type " + sendIntent.getType(),
                Log.DEBUG_MODE);

		return sendIntent;
	}

	@Kroll.method
    public void open() {
		if (TiApplication.isActivityTransition.get()) {
			TiApplication.addActivityTransitionListener(this);

		} else {
			doOpen();
		}
	}

    public void doOpen() {
		Intent sendIntent = buildIntent();
		Intent choosingIntent = Intent.createChooser(sendIntent, "Send");

		Activity activity = TiApplication.getAppCurrentActivity();
		if (activity != null) {
			TiActivitySupport activitySupport = (TiActivitySupport) activity;
			final int code = activitySupport.getUniqueResultCode();

            activitySupport.launchActivityForResult(choosingIntent, code,
                    new TiActivityResultHandler() {

				@Override
                        public void onResult(Activity activity, int requestCode,
                                int resultCode, Intent data) {
                            // ACTION_SEND does not set a result code (so the
                            // default of 0
                            // is always returned. We'll neither confirm nor
                            // deny -- assume SENT
                            // see
                            // http://code.google.com/p/android/issues/detail?id=5512
					KrollDict result = new KrollDict();
                            result.put("result", SENT); // TODO fix this when
                                                        // figure out above
                            result.putCodeAndMessage(TiC.ERROR_CODE_NO_ERROR,
                                    null);
                            fireEvent("complete", result, false);
				}

				@Override
                        public void onError(Activity activity, int requestCode,
                                Exception e) {
					KrollDict result = new KrollDict();
					result.put("result", FAILED);
                            result.putCodeAndMessage(TiC.ERROR_CODE_UNKNOWN,
                                    e.getMessage());
                            fireEvent("complete", result, false);
				}
			});

		} else {
            Log.e(TAG,
                    "Could not open email dialog, current activity is null.");
		}

	}

    private TiFile getTemFile(String filename) throws IOException {
        File tempFolder = new File(TiFileHelper.getInstance().getDataDirectory(false), "temp");
        if (!tempFolder.exists()) {
		tempFolder.mkdirs();
        }
        File tempfilej = new File(tempFolder, filename);


        return  new TiFile(tempfilej, tempfilej.getPath(), false);
    }

    private File dataToTemp(Object data, String fileName) {
        
        try {
            TiFile tempfile = getTemFile(fileName);
		if (tempfile.exists()) {
			tempfile.deleteFile();
		}
            tempfile.write(data, false);
//            tempfile.close();
            
            
		return tempfile.getNativeFile();
        } catch (IOException e) {
            Log.e(TAG,
                    "Unable to attach file " + fileName + ": " + e.getMessage(),
                    e);
	}

	private File getAttachableFileFrom(FileProxy fileProxy)
	{
		Exception exception = null;
		File file = null;

		// First, attempt to copy the file to a public temp directory.
		// We only need to do this with sandboxed files.
		if (isPrivateData(fileProxy)) {
			try {
				file = blobToTemp(fileProxy.read(), fileProxy.getName());
			} catch (Exception ex) {
				exception = ex;
			}
		}


    private File privateFileToTemp(FileProxy file) {
        File tempfile = null;
			try {
            tempfile = dataToTemp(file.read(), file.getName());
        } catch (IOException e) {
            Log.e(TAG, "Unable to attach file " + file.getName() + ": "
                    + e.getMessage(), e);
			}
		}

    private File blobToFile(TiBlob blob) {
		// Some blobs refer to files (TYPE_FILE), such as a selection from
		// the photo gallery . If that's the case with this
		// blob, then just attach the file and be done with it.
		if (blob.getType() == TiBlob.TYPE_FILE) {
			return ((TiBaseFile) blob.getData()).getNativeFile();
		}

		// For non-file blobs, make a temp file and attach.
		File tempFile = null;
		try {
			String fileName = "attachment";
        String extension = TiMimeTypeHelper
                .getFileExtensionFromMimeType(blob.getMimeType(), "");
			if (extension.length() > 0) {
				fileName += "." + extension;
			}
        return dataToTemp(blob, fileName);
		}
		return tempFile;
	}

    private Uri ObjectToUri(Object data, String fileName) {
        if (data instanceof FileProxy) {
            FileProxy fileProxy = (FileProxy) data;
            if (fileProxy.isFile()) {
                if (isPrivateData(fileProxy)) {
                    File file = privateFileToTemp(fileProxy);
                    if (file != null) {
                        return Uri.fromFile(file);
                    } else {
                        return null;
                    }
                } else {
                    File nativeFile = fileProxy.getBaseFile().getNativeFile();
                    return Uri.fromFile(nativeFile);
                }
            }
        } else if (data instanceof TiBlob) {
            TiBlob blob = (TiBlob) data;
            // Some blobs refer to files (TYPE_FILE), such as a selection from
            // the photo gallery . If that's the case with this
            // blob, then just attach the file and be done with it.
            if (blob.getType() == TiBlob.TYPE_FILE) {
                return Uri.fromFile(
                        ((TiBaseFile) blob.getData()).getNativeFile());
            }
            if (fileName == null) {
                fileName = "attachment";
                String extension = TiMimeTypeHelper
                        .getFileExtensionFromMimeType(blob.getMimeType(), "");
                if (extension.length() > 0) {
                    fileName += "." + extension;
                }
            }
            File file = dataToTemp(blob, fileName);
            if (file != null) {
                return Uri.fromFile(file);
            }
        } else if (data instanceof String) {
            if (fileName == null) {
                fileName = "attachment.txt";
            }
            File file = dataToTemp((String) data, fileName);
            if (file != null) {
                return Uri.fromFile(file);
            }
        }
        return null;
    }

    private Uri getAttachmentUri(Object attachment) {
		if (attachment instanceof FileProxy) {
			FileProxy fileProxy = (FileProxy) attachment;
			if (fileProxy.isFile()) {
				File file = getAttachableFileFrom(fileProxy);
				if (file != null) {
					return TiFileProvider.createUriFrom(file);
				}
			}
		} else if (attachment instanceof TiBlob) {
			File file = blobToFile((TiBlob) attachment);
			if (file != null) {
				return TiFileProvider.createUriFrom(file);
			}
        } else if (attachment instanceof HashMap) {
            return ObjectToUri(((HashMap) attachment).get("data"), TiConvert.toString((HashMap) attachment, "filename"));
		}
		return null;
	}

    private ArrayList<Uri> getAttachmentUris() {
		if (attachments == null) {
			return null;
		}
		ArrayList<Uri> uris = new ArrayList<Uri>();
		for (Object attachment : attachments) {
			Uri uri = getAttachmentUri(attachment);
			if (uri != null) {
				uris.add(uri);
			}
		}
		return uris;
	}

    private void prepareAttachments(Intent sendIntent, ArrayList<Uri> uris) {
        if (uris == null || uris.size() == 0) {
			return;
		}

		// Attach files via the following intent extras. (This is always required.)
		if (uris.size() == 1) {
			sendIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            // For api level 4, set the intent mimetype to single attachment's
            // mimetype.
            if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.DONUT) {
                sendIntent.setType(
                        TiMimeTypeHelper.getMimeType(uris.get(0).toString()));
		}

		// We must also copy the given file URIs to the intent's clip data as well.
		// Note: This is needed so that we can grant read-only access permissions below.
		ClipData clipData = null;
		for (Uri nextUri : uris) {
			if (nextUri != null) {
				if (clipData == null) {
					clipData = ClipData.newRawUri("", nextUri);
				} else {
					clipData.addItem(new ClipData.Item(nextUri));
				}
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        // Multiple attachments.
        sendIntent.putExtra(Intent.EXTRA_STREAM, uris);
			}
		}
		if (clipData != null) {
			sendIntent.setClipData(clipData);
		}

    private void putStringExtra(Intent intent, String extraType,
            String ourKey) {
		putStringExtra(intent, extraType, ourKey, false);
	}

    private void putStringExtra(Intent intent, String extraType, String ourkey,
            boolean encodeHtml) {
		if (this.hasProperty(ourkey)) {
			String text = TiConvert.toString(this.getProperty(ourkey));
			if (encodeHtml) {
				intent.putExtra(extraType, Html.fromHtml(text));
			} else {
				intent.putExtra(extraType, text);
			}
		}
	}

    private void putAddressExtra(Intent intent, String extraType,
            String ourkey) {
		Object testprop = this.getProperty(ourkey);
		if (testprop instanceof Object[]) {
			Object[] oaddrs = (Object[]) testprop;
			int len = oaddrs.length;
			String[] addrs = new String[len];
			for (int i = 0; i < len; i++) {
				addrs[i] = TiConvert.toString(oaddrs[i]);
			}
			intent.putExtra(extraType, addrs);
		}
	}

	@Override
    public TiUIView createView(Activity activity) {
		return null;
	}

    private boolean isPrivateData(FileProxy file) {
		if (file.isFile()) {
			if (file.getNativePath().contains("android_asset")) {
				return true;
			}
			if (file.getNativePath().contains(privateDataDirectoryPath)) {
				return true;
			}
		}
		return false;
	}

    public void onActivityTransition(boolean state) {
		if (!state) {
			doOpen();
			TiApplication.removeActivityTransitionListener(this);
		}
	}

	@Override
    public String getApiName() {
		return "Ti.UI.EmailDialog";
	}
}
