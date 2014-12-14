/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBaseActivity.DialogWrapper;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.support.v4.view.ViewCompat;
import android.widget.ListView;
import android.widget.Button;
import android.view.KeyEvent;
import android.view.View;
import android.text.Html;

public class TiUIDialog extends TiUIView
{
	private static final String TAG = "TiUIDialog";
	private static final int BUTTON_MASK = 0x10000000;

	protected Builder builder;
	private DialogWrapper dialogWrapper;
    private boolean hideOnClick = true;
    private boolean tapToDismiss = true;
    private TiViewProxy customView;

	protected class ClickHandler implements View.OnClickListener
	{
		private int result;
		public ClickHandler(int id) {
			this.result = id;
		}
		public void onClick(View v) {
			handleEvent(result);
			Log.d(TAG, "onClick hideOnClick: "  + hideOnClick, Log.DEBUG_MODE);
			if (hideOnClick == true) hide(null);
		}
	}

	public TiUIDialog(TiViewProxy proxy)
	{
		super(proxy);
		Log.d(TAG, "Creating a dialog", Log.DEBUG_MODE);
		createBuilder();
	}

	private Activity getCurrentActivity()
	{
		Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
		if (currentActivity == null) {
			currentActivity = proxy.getActivity();
		}
		return currentActivity;
	}
	
	private Builder getBuilder()
	{
		if (builder == null) {
			createBuilder();
		}
		return builder;
	}
	
	private void clearDialog() {
        AlertDialog dialog = dialogWrapper.getDialog();
	    if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
	}
	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        AlertDialog dialog = dialogWrapper.getDialog();
	    if (key.startsWith(TiC.PROPERTY_ACCESSIBILITY_PREFIX)) {
            if (dialog != null) {
                ListView listView = dialog.getListView();
                if (listView != null) {
                    if (key.equals(TiC.PROPERTY_ACCESSIBILITY_HIDDEN)) {
                        int importance = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
                        if (newValue != null && TiConvert.toBoolean(newValue)) {
                            importance = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
                        }
                        ViewCompat.setImportantForAccessibility(listView, importance);
                    } else {
                        listView.setContentDescription(composeContentDescription());
                    }
                }
            }
            return;
        }
        switch (key) {
        case TiC.PROPERTY_TITLE:
            if (dialog != null) {
                dialog.setTitle(Html.fromHtml((String) newValue));
            } else {
                getBuilder().setTitle(Html.fromHtml(TiConvert.toString(newValue)));
            }
            break;
        case TiC.PROPERTY_MESSAGE:
            if (dialog != null) {
                dialog.setMessage(Html.fromHtml((String) newValue));
            } else {
                getBuilder().setMessage(Html.fromHtml(TiConvert.toString(newValue)));
            }
            break;
        case TiC.PROPERTY_BUTTON_NAMES:
            clearDialog();
            processButtons(TiConvert.toStringArray(newValue));
            break;
        case TiC.PROPERTY_OK:
            clearDialog();
            processButtons(new String[]{TiConvert.toString(newValue)});
            break;
        case TiC.PROPERTY_CUSTOM_VIEW:
            clearDialog();
            processView(newValue);
            break;
        case TiC.PROPERTY_PERSISTENT:
            dialogWrapper.setPersistent(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_OPTIONS:
        {
            clearDialog();
            getBuilder().setView(null);
            String[] optionText = TiConvert.toStringArray(newValue);
            int selectedIndex = TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_SELECTED_INDEX), -1); 
            if(selectedIndex >= optionText.length){
                Log.d(TAG, "Ooops invalid selected index specified: " + selectedIndex, Log.DEBUG_MODE);
                selectedIndex = -1;
            }
            processOptions(optionText, selectedIndex);
            break;
        }
        case TiC.PROPERTY_SELECTED_INDEX:
        {
            clearDialog();
            getBuilder().setView(null);
            String[] optionText = TiConvert.toStringArray(proxy.getProperty(TiC.PROPERTY_OPTIONS));
            int selectedIndex = TiConvert.toInt(newValue, -1); 
            if(selectedIndex >= optionText.length){
                Log.d(TAG, "Ooops invalid selected index specified: " + selectedIndex, Log.DEBUG_MODE);
                selectedIndex = -1;
            }
            processOptions(optionText, selectedIndex);
            break;
        }
        case TiC.PROPERTY_HIDE_ON_CLICK:
            hideOnClick = TiConvert.toBoolean(newValue);
            if (dialog != null) {
                dialog.setCancelable(hideOnClick);
            }
            break;
        case TiC.PROPERTY_TAP_OUT_DISMISS:
            tapToDismiss = TiConvert.toBoolean(newValue);
            if (dialog != null) {
                dialog.setCanceledOnTouchOutside(tapToDismiss);
            }
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
	
	@Override
	public void processProperties(KrollDict d)
	{
        if (d.get(TiC.PROPERTY_BUTTON_NAMES) != null && d.containsKey(TiC.PROPERTY_OK) ) {
            d.remove(TiC.PROPERTY_OK);
        }
        
        if (d.get(TiC.PROPERTY_CUSTOM_VIEW) != null && d.containsKey(TiC.PROPERTY_OPTIONS) ) {
            d.remove(TiC.PROPERTY_OPTIONS);
        }
		super.processProperties(d);
	}

	private void processOptions(String[] optionText,int selectedIndex)
	{
		getBuilder().setSingleChoiceItems(optionText, selectedIndex , new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				handleEvent(which);
				if (hideOnClick == true) hide(null);
			}
		});
	}

	private void setButtonsListeners(AlertDialog dialog){
		Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (button!= null )button.setOnClickListener(new ClickHandler(0 | BUTTON_MASK));
		button = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
		if (button!= null )button.setOnClickListener(new ClickHandler(2 | BUTTON_MASK));
		button = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
		if (button!= null )button.setOnClickListener(new ClickHandler(1 | BUTTON_MASK));
	}

	private void processButtons(String[] buttonText)
	{
		getBuilder().setPositiveButton(null, null);
		getBuilder().setNegativeButton(null, null);
		getBuilder().setNeutralButton(null, null);
		getBuilder().setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog)
			{
				dialog = null;
				if (customView != null)
				{
				    customView.releaseViews(true);
				    customView = null;
				}
			}
		});
		for (int id = 0; id < buttonText.length; id++) {
			String text = buttonText[id];
//			ClickHandler clicker = new ClickHandler(id | BUTTON_MASK);
			switch (id) {
			case 0:
				getBuilder().setPositiveButton(text, null);
				break;
			case 1:
				getBuilder().setNeutralButton(text, null);
				break;
			case 2:
				getBuilder().setNegativeButton(text, null);
				break;
			default:
				Log.e(TAG, "Only 3 buttons are supported");
			}
		}
	}

	private void processView(Object customView)
	{
	    if (this.customView != null) {
            this.customView.releaseViews(false);
            this.customView.setParent(null);
            this.customView = null;
        }
	    if (customView instanceof HashMap) {
	        this.customView = (TiViewProxy)proxy.createProxyFromTemplate((HashMap) customView,
                   proxy, true);
            if (this.customView != null) {
                this.customView.updateKrollObjectProperties();
                this.proxy.updateKrollObjectProperties();
            }
        }
        else if (customView instanceof TiViewProxy) {
            this.customView = (TiViewProxy)customView;
        }
	    if (this.customView != null) {
	        this.customView.setActivity(dialogWrapper.getActivity());
            getBuilder().setView(this.customView.getOrCreateView().getOuterView());
	    }
	}

	public void show(KrollDict options)
	{
		AlertDialog dialog = dialogWrapper.getDialog();
		if (dialog == null) {
			if (dialogWrapper.getActivity() == null) {
				TiBaseActivity dialogActivity = (TiBaseActivity) getCurrentActivity();
				dialogWrapper.setActivity(new WeakReference<TiBaseActivity>(dialogActivity));
			}
//			processProperties(proxy.getProperties());
			getBuilder().setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dlg) {
					int cancelIndex = (proxy.hasProperty(TiC.PROPERTY_CANCEL)) ? TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_CANCEL)) : -1;
					Log.d(TAG, "onCancelListener called. Sending index: " + cancelIndex + ", hideOnClick: " + hideOnClick, Log.DEBUG_MODE);
					handleEvent(cancelIndex);
					if (hideOnClick == true) hide(null);
				}
			});
			dialog = getBuilder().create();
			dialog.setOnShowListener(new DialogInterface.OnShowListener(){
		        @Override
		        public void onShow(DialogInterface dialog) {
		        	TiApplication.getInstance().cancelPauseEvent();
		        }
			});
            dialog.setCancelable(hideOnClick);
            dialog.setCanceledOnTouchOutside(tapToDismiss);
			
			dialog.setOnKeyListener(new Dialog.OnKeyListener() {

                @Override
                public boolean onKey(DialogInterface dialog, int keyCode,
                        KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {                        
                        if (proxy.hasListeners(TiC.EVENT_ANDROID_BACK)) {
                            proxy.fireEvent(TiC.EVENT_ANDROID_BACK);
                        }
                        else if (hideOnClick){
                            int cancelIndex = TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_CANCEL), -1);
                            handleEvent(cancelIndex);
                            hide(null);
                        }
                    }
                    return true;
                }
            });


			// Initially apply accessibility properties here, the first time
			// the dialog actually becomes available. After this, propertyChanged
			// can also be used.
			ListView listView = dialog.getListView();
			if (listView != null) {
				listView.setContentDescription(composeContentDescription());
				int importance = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
				if (proxy != null) {
					Object propertyValue = proxy.getProperty(TiC.PROPERTY_ACCESSIBILITY_HIDDEN);
					if (propertyValue != null && TiConvert.toBoolean(propertyValue)) {
						importance = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
					}
				}
				ViewCompat.setImportantForAccessibility(listView, importance);
			}

			dialogWrapper.setDialog(dialog);
			builder = null;
		}

		try {
			Activity dialogActivity = dialogWrapper.getActivity();
			if (dialogActivity != null && !dialogActivity.isFinishing()) {
				if (dialogActivity instanceof TiBaseActivity) {
					//add dialog to its activity so we can clean it up later to prevent memory leak.
					((TiBaseActivity) dialogActivity).addDialog(dialogWrapper);
					dialog.show();
					setButtonsListeners(dialog);
					fireEvent(TiC.EVENT_OPEN, null, false);
				}
			} else {
				dialog = null;
				Log.w(TAG, "Dialog activity is destroyed, unable to show dialog with message: " + TiConvert.toString(proxy.getProperty(TiC.PROPERTY_MESSAGE)));
			}
		} catch (Throwable t) {
			Log.w(TAG, "Context must have gone away: " + t.getMessage(), t);
		}
	}

	public void hide(KrollDict options)
	{
		fireEvent(TiC.EVENT_CLOSE, null, false);
		AlertDialog dialog = dialogWrapper.getDialog();
		if (dialog != null) {
			dialog.dismiss();
			dialogWrapper.getActivity().removeDialog(dialog);
		}

		if (customView != null) {
		    customView.releaseViews(true);
		    customView = null;
		}
	}

	private void createBuilder()
	{
		Activity currentActivity = getCurrentActivity();
		if (currentActivity != null) {
			this.builder = new AlertDialog.Builder(currentActivity);
			this.builder.setCancelable(true);

			//Native dialogs are persistent by default.
			TiBaseActivity dialogActivity = (TiBaseActivity)currentActivity;
			dialogWrapper = dialogActivity.new DialogWrapper(null, true, new WeakReference<TiBaseActivity>(dialogActivity));
		} else {
			Log.e (TAG, "Unable to find an activity for dialog.");
		}
	}

	public void handleEvent(int id)
	{
		int cancelIndex = TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_CANCEL), -1);
		KrollDict data = new KrollDict();
		if (id != -1 && (id & BUTTON_MASK) != 0) {
			data.put(TiC.PROPERTY_BUTTON, true);
			id &= ~BUTTON_MASK;
		} else {
			data.put(TiC.PROPERTY_BUTTON, false);
			// If an option was selected and the user accepted it, update the proxy.
			if (id != -1 && proxy.hasProperty(TiC.PROPERTY_OPTIONS)) {
				proxy.setProperty(TiC.PROPERTY_SELECTED_INDEX, id);
			}
		}
		data.put(TiC.EVENT_PROPERTY_INDEX, id);
		data.put(TiC.PROPERTY_CANCEL, id == cancelIndex);
		fireEvent(TiC.EVENT_CLICK, data, false);
	}
}
