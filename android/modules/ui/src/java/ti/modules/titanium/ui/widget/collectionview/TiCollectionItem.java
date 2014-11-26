/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import java.util.ArrayList;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.TiUIButton;
import ti.modules.titanium.ui.widget.TiUISlider;
import ti.modules.titanium.ui.widget.TiUISwitch;
import ti.modules.titanium.ui.widget.TiUIText;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class TiCollectionItem extends TiUIView implements TiTouchDelegate {
    private static final String TAG = "TiListItem";
	TiUIView mClickDelegate;
	View listItemLayout;
	private boolean shouldFireClick = true;
    private boolean canShowLeftMenu = false;
    private boolean canShowLeftMenuDefined = false;
    private boolean canShowRightMenu = false;
    private boolean canShowRightMenuDefined = false;
    private List<TiViewProxy> leftButtons = null;
    private List<TiViewProxy> rightButtons = null;
	public TiCollectionItem(TiViewProxy proxy) {
		super(proxy);
	}

	public TiCollectionItem(TiViewProxy proxy, View v, View item_layout) {
		super(proxy);
//		layoutParams = p;
        layoutParams.sizeOrFillWidthEnabled = true;
        layoutParams.autoFillsWidth = true;
		listItemLayout = item_layout;
		setNativeView(v);
		registerForTouch(v);
		v.setFocusable(false);
	}
	
	private List<TiViewProxy> proxiesArrayFromValue(Object value) {
	    List<TiViewProxy> result = null;
	    final CollectionItemProxy itemProxy = (CollectionItemProxy) proxy;
	    if (value instanceof Object[]) {
	        result = new ArrayList<TiViewProxy>();
	        Object[] array  = (Object[]) value;
            for (int i = 0; i < array.length; i++) {
                TiViewProxy viewProxy  = (TiViewProxy)proxy.createProxyFromObject(array[i], proxy, false);
                if (viewProxy != null) {
                    viewProxy.setParent(proxy);
                    viewProxy.setEventOverrideDelegate(itemProxy);
                    result.add(viewProxy);
                }
            }
	    }
	    else {
	        TiViewProxy viewProxy  = (TiViewProxy)proxy.createProxyFromObject(value, proxy, false);
            if (viewProxy != null) {
                viewProxy.setParent(proxy);
                viewProxy.setEventOverrideDelegate(itemProxy);
                result = new ArrayList<TiViewProxy>();
                result.add(viewProxy);
            }
	    }
	    return result;
	}
	
	public void processProperties(KrollDict d) {
		CollectionItemProxy itemProxy = (CollectionItemProxy)getProxy();

		if (d.containsKey(TiC.PROPERTY_ACCESSORY_TYPE)) {
			int accessory = TiConvert.toInt(d.get(TiC.PROPERTY_ACCESSORY_TYPE), -1);
			handleAccessory(accessory);
		}
		if (d.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			d.put(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, d.get(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			d.put(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, d.get(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE));
		}
		
		if (d.containsKey(TiC.PROPERTY_CAN_SWIPE_LEFT)) {
            canShowLeftMenu = d.optBoolean(TiC.PROPERTY_CAN_SWIPE_LEFT, true);
            canShowLeftMenuDefined = true;
        }
		if (d.containsKey(TiC.PROPERTY_CAN_SWIPE_RIGHT)) {
            canShowRightMenu = d.optBoolean(TiC.PROPERTY_CAN_SWIPE_RIGHT, true);
            canShowRightMenuDefined = true;
        }
		
		if (d.containsKey(TiC.PROPERTY_LEFT_SWIPE_BUTTONS)) {
		    if (leftButtons != null) {
		        for (TiViewProxy viewProxy : leftButtons) {
		            proxy.removeHoldedProxy(TiConvert.toString(
		                    viewProxy.getProperty(TiC.PROPERTY_BIND_ID), null));
		            proxy.removeProxy(viewProxy);
		        }
		    }
            leftButtons = proxiesArrayFromValue(d.get(TiC.PROPERTY_LEFT_SWIPE_BUTTONS));
        }
		if (d.containsKey(TiC.PROPERTY_RIGHT_SWIPE_BUTTONS)) {
            if (rightButtons != null) {
                for (TiViewProxy viewProxy : leftButtons) {
                    proxy.removeHoldedProxy(TiConvert.toString(
                            viewProxy.getProperty(TiC.PROPERTY_BIND_ID), null));
                    proxy.removeProxy(viewProxy);
                }
            }
            rightButtons = proxiesArrayFromValue(d.get(TiC.PROPERTY_RIGHT_SWIPE_BUTTONS));
        }
		super.processProperties(d);
	}

	private void handleAccessory(int accessory) {
		
		ImageView accessoryImage = (ImageView) listItemLayout.findViewById(TiCollectionView.accessory);

		switch(accessory) {

			case UIModule.LIST_ACCESSORY_TYPE_CHECKMARK:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiCollectionView.isCheck);
				break;
			case UIModule.LIST_ACCESSORY_TYPE_DETAIL:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiCollectionView.hasChild);
				break;

			case UIModule.LIST_ACCESSORY_TYPE_DISCLOSURE:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiCollectionView.disclosure);
				break;
	
			default:
                accessoryImage.setVisibility(View.GONE);
				accessoryImage.setImageDrawable(null);
				break;
		}
	}
	
	@Override
	protected void setOnClickListener(View view)
	{
		view.setOnClickListener(new OnClickListener()
		{
			public void onClick(View view)
			{
				
				if (shouldFireClick) {
					KrollDict data = dictFromEvent(lastUpEvent);
					handleFireItemClick(new KrollDict(data));
					fireEvent(TiC.EVENT_CLICK, data);
				}
                shouldFireClick = true;
			}
		});
	}
	
	protected void handleFireItemClick (KrollDict data) {
//		TiViewProxy listViewProxy = ((ListItemProxy)proxy).getListProxy();
//		if (listViewProxy != null && listViewProxy.hasListeners(TiC.EVENT_ITEM_CLICK)) {
			// TiUIView listView = listViewProxy.peekView();
			// if (listView != null) {
			// 	KrollDict d = listView.getAdditionalEventData();
			// 	if (d == null) {
			// 		listView.setAdditionalEventData(new KrollDict((HashMap) additionalEventData));
			// 	} else {
			// 		d.clear();
			// 		d.putAll(additionalEventData);
			// 	}
				if (mClickDelegate == null) {
				    //fire on the ListItemProxy so that the event data gets overriden
				    // and thus filled
					proxy.fireEvent(TiC.EVENT_ITEM_CLICK, data, true, true);
				}
			// }
//		}
	}
	
	public void release() {
		if (listItemLayout != null) {
			listItemLayout = null;
		}
		removeUnsetPressCallback();
		super.release();
	}
	@Override
	protected void handleTouchEvent(MotionEvent event) {
		mClickDelegate = null;
		super.handleTouchEvent(event);
	}
	private boolean prepressed = false;
	private final class UnsetPressedState implements Runnable {
        public void run() {
            if (nativeView != null) {
                nativeView.setPressed(false);
            }
        }
    }
    private UnsetPressedState mUnsetPressedState;
    /**
     * Remove the prepress detection timer.
     */
    private void removeUnsetPressCallback() {
        if (nativeView != null && nativeView.isPressed() && mUnsetPressedState != null) {
            nativeView.setPressed(false);
            nativeView.removeCallbacks(mUnsetPressedState);
        }
    }
    
    public boolean pointInView(final View view, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((view.getRight() - view.getLeft()) + slop) &&
                localY < ((view.getBottom() - view.getTop()) + slop);
    }
    
    @SuppressLint("NewApi")
    public boolean isInScrollingContainer(View view) {
        ViewParent p = view.getParent();
        while (p != null && p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }
    
    @Override
    public void onTouchEvent(MotionEvent event, TiUIView fromView) {
        if (fromView == this)
            return;
        shouldFireClick = false;
        if (fromView instanceof TiUIButton || fromView instanceof TiUISwitch
                || fromView instanceof TiUISlider
                || fromView instanceof TiUIText)
            return;
        mClickDelegate = fromView;

        if (nativeView != null && !fromView.getPreventListViewSelection()) {
            final boolean pressed = nativeView.isPressed();

            if (nativeView.isEnabled() == false) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressed) {
                    nativeView.setPressed(false);
                }
                return;
            }

            if (nativeView.isClickable() || nativeView.isLongClickable()) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (pressed || prepressed) {

                        if (prepressed) {
                            // The button is being released before we actually
                            // showed it as pressed. Make it show the pressed
                            // state now (before scheduling the click) to ensure
                            // the user sees it.
                            nativeView.setPressed(true);
                        }
                        if (mUnsetPressedState == null) {
                            mUnsetPressedState = new UnsetPressedState();
                        }
                        if (prepressed) {
                            nativeView
                                    .postDelayed(mUnsetPressedState,
                                            ViewConfiguration
                                                    .getPressedStateDuration());
                        } else if (!nativeView.post(mUnsetPressedState)) {
                            // If the post failed, unpress right now
                            mUnsetPressedState.run();
                        }
                    }
                    break;

                case MotionEvent.ACTION_DOWN:

                    // Walk up the hierarchy to determine if we're inside a
                    // scrolling container.
                    boolean isInScrollingContainer = isInScrollingContainer(nativeView);

                    // For views inside a scrolling container, delay the pressed
                    // feedback for
                    // a short period in case this is a scroll.
                    if (isInScrollingContainer) {
                        prepressed = true;

                    } else {
                        // Not inside a scrolling container, so show the
                        // feedback right away
                        nativeView.setPressed(true);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    nativeView.setPressed(false);
                    break;

                case MotionEvent.ACTION_MOVE:
                    final int x = (int) event.getX();
                    final int y = (int) event.getY();

                    // Be lenient about moving outside of buttons
                    if (!pointInView(nativeView, x, y,
                            ViewConfiguration.get(getContext())
                                    .getScaledTouchSlop())) {
                        if (pressed) {
                            nativeView.setPressed(false);
                        }
                    }
                    else if (!pressed) {
                        prepressed = false;
                        nativeView.setPressed(true);
                    }
                    break;
                }
            }
        }
    }

    public boolean canShowLeftMenu() {
        return (canShowLeftMenuDefined && canShowLeftMenu) || leftButtons != null;
    }
    
    public boolean canShowRightMenu() {
        return (canShowRightMenuDefined && canShowRightMenu) || rightButtons != null;
    }
    
    private View[] viewsForProxyArray(List<TiViewProxy> proxies) {
        if (proxies != null) {
            View[] buttons = new View[proxies.size()];
            int i = 0;
            final Context context = getContext();
            for (TiViewProxy viewProxy : proxies) {
                View view = viewProxy.getOrCreateView().getOuterView();
                if (view.getParent() instanceof TiCompositeLayout) {
                    buttons[i] = (View) view.getParent();
                }
                else {
                    TiCompositeLayout layout = new TiCompositeLayout(context);
                    layout.addView(view);
                    buttons[i] = layout;
                }
                
                i ++;
            }
            return buttons;
        }
        return null;
    }
    
    public View[] getLeftButtons() {
        return viewsForProxyArray(leftButtons);
    }
    
    public View[] getRightButtons() {
        return viewsForProxyArray(rightButtons);
    }

    public boolean canShowMenus() {
        return leftButtons != null || rightButtons != null;
    }
}