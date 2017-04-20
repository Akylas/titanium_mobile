/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
//import org.appcelerator.kroll.common.AsyncResult;
//import org.appcelerator.kroll.common.Log;
//import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
//import org.appcelerator.titanium.proxy.ParentingProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import ti.modules.titanium.ui.widget.TiUIScrollableView;
import android.app.Activity;
import android.os.Message;
//import android.view.ViewGroup;

@Kroll.proxy(creatableInModule=UIModule.class, propertyAccessors={
	TiC.PROPERTY_PAGE_OFFSET,
	TiC.PROPERTY_PAGE_WIDTH,
	TiC.PROPERTY_CACHE_SIZE,
	TiC.PROPERTY_SHOW_PAGING_CONTROL,
	TiC.PROPERTY_OVER_SCROLL_MODE,
	TiC.PROPERTY_SCROLLING_ENABLED,
	TiC.PROPERTY_CURRENT_PAGE,
	TiC.PROPERTY_TRANSITION
})
public class ScrollableViewProxy extends TiViewProxy
{
	private static final String TAG = "TiScrollableView";

	private static final int MSG_FIRST_ID = TiViewProxy.MSG_LAST_ID + 1;
	public static final int MSG_HIDE_PAGER = MSG_FIRST_ID + 101;
	public static final int MSG_MOVE_PREV = MSG_FIRST_ID + 102;
	public static final int MSG_MOVE_NEXT = MSG_FIRST_ID + 103;
	public static final int MSG_SCROLL_TO = MSG_FIRST_ID + 104;
//	public static final int MSG_SET_VIEWS = MSG_FIRST_ID + 105;
//	public static final int MSG_ADD_VIEW = MSG_FIRST_ID + 106;
	public static final int MSG_SET_CURRENT = MSG_FIRST_ID + 107;
//	public static final int MSG_REMOVE_VIEW = MSG_FIRST_ID + 108;
	//public static final int MSG_SET_ENABLED = MSG_FIRST_ID + 109;
//	public static final int MSG_INSERT_VIEWS_AT = MSG_FIRST_ID + 110;
	public static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	private static final int DEFAULT_PAGING_CONTROL_TIMEOUT = 3000;

	protected AtomicBoolean inScroll;
	
    private final ArrayList<TiViewProxy> mViews;
    private final Object viewsLock;
    private boolean preload = false;


	public ScrollableViewProxy()
	{
		super();
        mViews = new ArrayList<TiViewProxy>();
        viewsLock = new Object();
		inScroll = new AtomicBoolean(false);
		defaultValues.put(TiC.PROPERTY_SHOW_PAGING_CONTROL, false);
        defaultValues.put(TiC.PROPERTY_OVER_SCROLL_MODE, 0);
        defaultValues.put(TiC.PROPERTY_CURRENT_PAGE, 0);
	}
	
	@Override
    public void handleCreationDict(HashMap options, KrollProxy rootProxy) {
        super.handleCreationDict(options, rootProxy);
        if (rootProxy != null) {
            this.rootProxyForTemplates = new WeakReference<KrollProxy>(rootProxy);
        }
        //Adding sections to preload sections, so we can handle appendSections/insertSection
        //accordingly if user call these before TiListView is instantiated.
        if (options.containsKey(TiC.PROPERTY_VIEWS)) {
            preload = true;
            setViews(options.get(TiC.PROPERTY_VIEWS));
        }
    }
	
	
    private WeakReference<KrollProxy> rootProxyForTemplates = null;
    
    public KrollProxy getRootProxyForTemplates() {
        if (rootProxyForTemplates != null) {
            return rootProxyForTemplates.get();
        }
        return this;
    }
//	@Override
//	protected void initFromTemplate(HashMap template_, KrollProxy rootProxy,
//            boolean updateKrollProperties, boolean recursive) {
//	    super.initFromTemplate(template_, rootProxy, updateKrollProperties, recursive);
//	}

	@Override
	public TiUIView createView(Activity activity)
	{
		TiUIView view = new TiUIScrollableView(this, (TiBaseActivity) activity);
        LayoutParams params = view.getLayoutParams();
        params.sizeOrFillWidthEnabled = true;
        params.sizeOrFillHeightEnabled = true;
        params.autoFillsHeight = true;
        params.autoFillsWidth = true;
        return view; 
	}

	//only for tableview magic
	@Override
	public void clearViews()
	{
		super.clearViews();
		synchronized (viewsLock) {
            for (TiViewProxy viewProxy : mViews) {
                viewProxy.clearViews();
            }
        }
	}

	protected TiUIScrollableView getView()
	{
		return (TiUIScrollableView) getOrCreateView();
	}
	
	@Kroll.method
    public Object getView(int page)
    {
        synchronized (viewsLock) {
    	    ArrayList<TiViewProxy> views = mViews;
    	    if (page >= 0 && page < views.size()) {
    	        return views.get(page);
    	    }
            return null;
        }
    }

	public boolean handleMessage(Message msg)
	{
		boolean handled = false;
		TiUIScrollableView view = getView();
        if (view == null) {
            return true;
        }
		switch(msg.what) {
			case MSG_HIDE_PAGER:
			    view.hidePager();
				handled = true;
				break;
			case MSG_MOVE_PREV:
				inScroll.set(true);
				view.movePrevious(msg.arg1 == 1);
				inScroll.set(false);
				handled = true;
				break;
			case MSG_MOVE_NEXT:
				inScroll.set(true);
				view.moveNext(msg.arg1 == 1);
				inScroll.set(false);
				handled = true;
				break;
			case MSG_SCROLL_TO:
				inScroll.set(true);
				view.scrollTo(msg.obj, msg.arg1 == 1);
				inScroll.set(false);
				handled = true;
				break;
			case MSG_SET_CURRENT:
			    view.setCurrentPage(msg.obj);
				handled = true;
				break;
			
			default:
				handled = super.handleMessage(msg);
		}

		return handled;
	}

	@Kroll.getProperty(enumerable=false) @Kroll.method
	public Object getViews()
	{
        synchronized (viewsLock) {
            return mViews.toArray();
        }
	}
	
	public void clearViewsList()
    {
        if (mViews == null || mViews.size() == 0) {
            return;
        }
        synchronized (viewsLock) {
            if (this.view != null) {
                this.getView().clearViewsList();
            }
            for (TiViewProxy viewProxy : mViews) {
                //dont release views will be done by the adapter
//              viewProxy.releaseViews(true);
                viewProxy.setParent(null);
            }
            mViews.clear();
        }
    }
	
	public boolean getPreload() {
        return preload;
    }
    
    public void setPreload(boolean pload)
    {
        preload = pload;
    }

	@Kroll.setProperty @Kroll.method
	public void setViews(Object viewsObject)
	{
	    clearViewsList();
	    synchronized(viewsLock) {
    	    if (viewsObject instanceof Object[]) {
                Object[] views = (Object[])viewsObject;
    //          Activity activity = this.proxy.getActivity();
                KrollProxy rootProxy = getRootProxyForTemplates();
                for (int i = 0; i < views.length; i++) {
                    Object arg = views[i];
                    KrollProxy child = null;
                    if (arg instanceof HashMap) {
                        child = createProxyFromTemplate((HashMap) arg, rootProxy, true);
                        if (child != null) {
                            child.updateKrollObjectProperties();
                        }
                    } else {
                        child = (KrollProxy) arg;
                    }
                    if (child instanceof TiViewProxy) {
    //                  tv.setActivity(activity);
                        ((TiViewProxy) child).setParent(this);
                        mViews.add((TiViewProxy) child);
                    }
                }
            }
	    }
	    if (view != null) {
//            getView().notifyViewsChanged();
        } else {
            preload = true;
        }
	}

	@Kroll.method
	public void addView(Object viewObject)
	{
	    insertViewsAt(mViews.size(), viewObject);
	}

	@Kroll.method
	public void insertViewsAt(int insertIndex, Object object)
    {
        if (object instanceof TiViewProxy) {
            // insert a single view at insertIndex
            TiViewProxy proxy = (TiViewProxy) object;
            if (!mViews.contains(proxy)) {
                proxy.setActivity(getActivity());
                proxy.setParent(this);
                mViews.add(insertIndex, proxy);
                setProperty(TiC.PROPERTY_VIEWS, mViews.toArray());
                if (view != null) {
                    getView().notifyViewsChanged();
                } else {
                    preload = true;
                }
            }
        }
        else if (object instanceof Object[]) {
            // insert many views at insertIndex
            boolean changed = false;
            Object[] views = (Object[])object;
            Activity activity = getActivity();
            for (int i = 0; i < views.length; i++) {
                if (views[i] instanceof TiViewProxy) {
                    TiViewProxy tv = (TiViewProxy)views[i];
                    tv.setActivity(activity);
                    tv.setParent(this);
                    mViews.add(insertIndex, tv);
                    changed = true;
                }
            }
            if (changed) {
                setProperty(TiC.PROPERTY_VIEWS, mViews.toArray());
                if (view != null) {
                    getView().notifyViewsChanged();
                } else {
                    preload = true;
                }
            }
        }
    }

	@Kroll.method
	public void removeView(Object viewObject)
	{
        TiViewProxy proxy = null;
	    if (viewObject instanceof Number) {          
             proxy = getViewAt(TiConvert.toInt(view));
            
        } else if (viewObject instanceof TiViewProxy) {
            proxy = (TiViewProxy)viewObject;
        }
	    if (proxy != null) {
            proxy.setParent(null);
            synchronized (viewObject) {
                mViews.remove(proxy);
                setProperty(TiC.PROPERTY_VIEWS, mViews.toArray());
            }
            if (view != null) {
                getView().notifyViewsChanged();
            } else {
                preload = true;
            }
        }
	    
	}

	@Kroll.method
	public void scrollToView(Object view, @Kroll.argument(optional = true) Object obj)
	{
		if (inScroll.get()) return;

		Boolean animated = true;
		if (obj != null) {
			animated = TiConvert.toBoolean(obj);
		}

		getMainHandler().obtainMessage(MSG_SCROLL_TO, animated?1:0, 0, view).sendToTarget();
	}

	@Kroll.method
	public void movePrevious(@Kroll.argument(optional = true) Object obj)
	{
		if (inScroll.get()) return;


		Boolean animated = true;
		if (obj != null) {
			animated = TiConvert.toBoolean(obj);
		}

		getMainHandler().removeMessages(MSG_MOVE_PREV);
		getMainHandler().obtainMessage(MSG_MOVE_PREV, animated?1:0, 0, null).sendToTarget();
	}

	@Kroll.method
	public void moveNext(@Kroll.argument(optional = true) Object obj)
	{
		if (inScroll.get()) return;

		Boolean animated = true;
		if (obj != null) {
			animated = TiConvert.toBoolean(obj);
		}

		getMainHandler().removeMessages(MSG_MOVE_NEXT);
		getMainHandler().obtainMessage(MSG_MOVE_NEXT, animated?1:0, 0, null).sendToTarget();
	}

	public void setPagerTimeout()
	{
		getMainHandler().removeMessages(MSG_HIDE_PAGER);

		int timeout = DEFAULT_PAGING_CONTROL_TIMEOUT;
		Object o = getProperty(TiC.PROPERTY_PAGING_CONTROL_TIMEOUT);
		if (o != null) {
			timeout = TiConvert.toInt(o);
		}

		if (timeout > 0) {
			getMainHandler().sendEmptyMessageDelayed(MSG_HIDE_PAGER, timeout);
		}
	}

	public void fireDragEnd(int currentPage) {
	    if (currentPage < 0 ||  currentPage >= getViewCount()) {
            return;
        }
        TiViewProxy currentView = getViewAt(currentPage);
		setProperty(TiC.PROPERTY_CURRENT_PAGE, currentPage);
		if (hasListeners(TiC.EVENT_DRAGEND)) {
			KrollDict options = new KrollDict();
            options.put(TiC.PROPERTY_VIEW, currentView);
			options.put(TiC.PROPERTY_CURRENT_PAGE, currentPage);
			fireEvent(TiC.EVENT_DRAGEND, options, false, false);
		}
	}

    public void fireScrollEnd(int currentPage)
	{
        if (currentPage < 0 ||  currentPage >= getViewCount()) {
            return;
        }
        TiViewProxy currentView = getViewAt(currentPage);
		setProperty(TiC.PROPERTY_CURRENT_PAGE, currentPage);
		if (hasListeners(TiC.EVENT_SCROLLEND)) {
			KrollDict options = new KrollDict();
            options.put(TiC.PROPERTY_VIEW, currentView);
			options.put(TiC.PROPERTY_CURRENT_PAGE, currentPage);
			fireEvent(TiC.EVENT_SCROLLEND, options, false, false);
		}
	}

	public void fireScroll(int currentPage, float currentPageAsFloat)
	{
	    if (currentPage < 0 ||  currentPage >= getViewCount()) {
            return;
        }
	    TiViewProxy currentView = getViewAt(currentPage);
		if (hasListeners(TiC.EVENT_SCROLL)) {
			KrollDict options = new KrollDict();
			options.put(TiC.PROPERTY_VIEW, currentView);
			options.put(TiC.PROPERTY_CURRENT_PAGE, currentPage);
			options.put("currentPageAsFloat", currentPageAsFloat);
			fireEvent(TiC.EVENT_SCROLL, options);
		}
	}
	
	public void fireScrollStart(int currentPage)
	{
	    if (currentPage < 0 ||  currentPage >= getViewCount()) {
            return;
        }
        TiViewProxy currentView = getViewAt(currentPage);
		if (hasListeners(TiC.EVENT_SCROLLSTART)) {
			KrollDict options = new KrollDict();
			options.put(TiC.PROPERTY_VIEW, currentView);
			options.put(TiC.PROPERTY_CURRENT_PAGE, currentPage);
			fireEvent(TiC.EVENT_SCROLLSTART, options, false, false);
		}
	}
	
	public void firePageChange(int currentPage, int oldPage)
	{
        TiViewProxy currentView = getViewAt(currentPage);
        TiViewProxy oldView = getViewAt(oldPage);
		if (hasListeners(TiC.EVENT_CHANGE)) {
			KrollDict options = new KrollDict();
            options.put(TiC.PROPERTY_VIEW, currentView);
            options.put("oldView", oldView);
			options.put(TiC.PROPERTY_CURRENT_PAGE, currentPage);
			fireEvent(TiC.EVENT_CHANGE, options, false, false);
		}
	}
	
//	@Kroll.setProperty @Kroll.method
//	public void setScrollingEnabled(Object enabled)
//	{
//		getMainHandler().obtainMessage(MSG_SET_ENABLED, enabled).sendToTarget();
//	}

//	@Kroll.getProperty @Kroll.method
//	public boolean getScrollingEnabled()
//	{
//		return getView().getEnabled();
//	}

	@Kroll.getProperty @Kroll.method
	public int getCurrentPage()
	{
		return getView().getCurrentPage();
	}

	@Kroll.setProperty @Kroll.method
	public void setCurrentPage(Object page)
	{
		//getView().setCurrentPage(page);
		getMainHandler().obtainMessage(MSG_SET_CURRENT, page).sendToTarget();
	}

	@Override
	public void releaseViews(boolean activityFinishing)
	{
		getMainHandler().removeMessages(MSG_HIDE_PAGER);
		super.releaseViews(activityFinishing);
		synchronized  (viewsLock) {
            if (mViews != null) {
                for (TiViewProxy viewProxy : mViews) {
                    viewProxy.releaseViews(true);
//                    viewProxy.setParent(null);
                }
                mViews.clear();
            }
        }
	}
	
	public TiViewProxy getViewAt(int position) {
	    synchronized (viewsLock) {
	        if (position >= 0 && position < mViews.size()) {
	            return mViews.get(position);
	        }
	        return null;
	    }
	}
	
	public int getPositionOfView(TiViewProxy proxy) {
        synchronized (viewsLock) {
            return mViews.indexOf(proxy);
//            for(int i = 0; i < getCount(); i++) {
//                if(mViews.get(i).equals(proxy)) {
//                    // item still exists in dataset; return position
//                    return i;
//                }
//            }
        }
    }
	
	public int getViewCount() {
        synchronized (viewsLock) {
            return mViews.size();
//            for(int i = 0; i < getCount(); i++) {
//                if(mViews.get(i).equals(proxy)) {
//                    // item still exists in dataset; return position
//                    return i;
//                }
//            }
        }
    }

	@Override
	public void setActivity(Activity activity)
	{
	    super.setActivity(activity);
	    if (view != null) {
	        synchronized (viewsLock) {
    	        if (mViews != null) {
    	            for (TiViewProxy viewProxy : mViews) {
    	                viewProxy.setActivity(activity);
    	            }
    	        }
	        }
	        
	    }
        
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.ScrollableView";
	}
}
