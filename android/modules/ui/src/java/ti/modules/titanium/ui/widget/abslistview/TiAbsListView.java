/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.abslistview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiPoint;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiUINonViewGroupView;
import org.appcelerator.titanium.view.TiUIView;
import org.json.JSONException;

import com.nhaarman.listviewanimations.ListViewAnimationsBaseAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.DynamicListItemView;
import com.nhaarman.listviewanimations.itemmanipulation.DynamicStickyListHeadersAbsListViewInterface;
import com.nhaarman.listviewanimations.itemmanipulation.DynamicWrapperViewList;
import com.nhaarman.listviewanimations.itemmanipulation.swipemenu.MenuAdapter;
import com.nhaarman.listviewanimations.util.Insertable;
import com.nhaarman.listviewanimations.util.Removable;

import se.emilsjolander.stickylistheaders.OnStickyHeaderChangedListener;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListViewAbstract;
import se.emilsjolander.stickylistheaders.WrapperView;
import ti.modules.titanium.ui.RefreshControlProxy;
import ti.modules.titanium.ui.SearchBarProxy;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.ViewProxy;
import android.annotation.SuppressLint;
import ti.modules.titanium.ui.android.SearchViewProxy;
import ti.modules.titanium.ui.widget.CustomListView;
import ti.modules.titanium.ui.widget.TiUITableView;
//import ti.modules.titanium.ui.widget.abslistview.AbsListSectionProxy.AbsListItemData;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar.OnSearchChangeListener;
import ti.modules.titanium.ui.widget.searchview.TiUISearchView;
import yaochangwei.pulltorefreshlistview.widget.RefreshableListView;
import yaochangwei.pulltorefreshlistview.widget.RefreshableListView.OnPullListener;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.view.ViewPager;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.AbsListView.OnScrollListener;

@SuppressLint("NewApi")
public abstract class TiAbsListView<C extends StickyListHeadersListViewAbstract & DynamicStickyListHeadersAbsListViewInterface> extends TiUINonViewGroupView 
    implements OnSearchChangeListener, TiCollectionViewInterface {

	protected C listView;
	private TiBaseAdapter adapter;
	private List<AbsListSectionProxy> sections;
	private AtomicInteger itemTypeCount;
	private String defaultTemplateBinding;
	private HashMap<String, TiAbsListViewTemplate> templatesByBinding;
	// public static int isCheck;
	// public static int hasChild;
	// public static int disclosure;
	// public static int accessory = 24124;
	private int[] marker = new int[2];
	private String searchText;
	private boolean caseInsensitive;
	private boolean ignoreExactMatch;
	private static final String TAG = "TiListView";
	private boolean hideKeyboardOnScroll = true;
	private boolean canShowMenus = false;
	
	protected static final int TIFLAG_NEEDS_DATASET               = 0x00000001;
    protected static final int TIFLAG_NEEDS_ADAPTER_CHANGE        = 0x00000002;

	private Set<TiViewProxy> handledProxies;

	private int currentScrollOffset = -1;
	
	
	private static final String defaultTemplateKey = UIModule.LIST_ITEM_TEMPLATE_DEFAULT;
	private static final TiAbsListViewTemplate defaultTemplate = new TiDefaultAbsListViewTemplate(defaultTemplateKey);

	
	/* We cache properties that already applied to the recycled list tiem in ViewItem.java
	 * However, since Android randomly selects a cached view to recycle, our cached properties
	 * will not be in sync with the native view's properties when user changes those values via
	 * User Interaction - i.e click. For this reason, we create a list that contains the properties 
	 * that must be reset every time a view is recycled, to ensure synchronization. Currently, only
	 * "value" is in this list to correctly update the value of Ti.UI.Switch.
	 */
	public static List<String> MUST_SET_PROPERTIES = Arrays.asList(TiC.PROPERTY_VALUE, TiC.PROPERTY_AUTO_LINK, TiC.PROPERTY_TEXT, TiC.PROPERTY_HTML);
	
//	public static final String MIN_SEARCH_HEIGHT = "50dp";
	public static final int HEADER_FOOTER_WRAP_ID = 12345;
	public static final int HEADER_FOOTER_VIEW_TYPE = 0;
	public static final int BUILT_IN_TEMPLATE_ITEM_TYPE = 1;
	public static final int CUSTOM_TEMPLATE_ITEM_TYPE = 2;
	
	
	private void addHandledProxy(final TiViewProxy proxy) {
	    if (handledProxies == null) {
	        handledProxies = new HashSet<TiViewProxy>();
	    }
	       handledProxies.add(proxy);
	}
	
//	private void removeHandledProxy(final TiViewProxy proxy) {
//        if (handledProxies == null) {
//            return;
//        }
//        handledProxies.remove(proxy);
//    }
	
	private AbsListView getInternalListView() {
        return listView.getWrappedList();
	}
	
	protected static String getCellProxyRootType() {
        return "Ti.UI.ListItem";
    }
	
    private static HashMap<String, String> TO_PASS_PROPS;
    private HashMap<String, Object> toPassProps;
	private void updateToPassProps(HashMap<String, Object> props) {
	    if (props == null || props.size() == 0) {
	        return;
	    }
	    if (TO_PASS_PROPS == null) {
	        TO_PASS_PROPS = new HashMap<String, String>();
	        TO_PASS_PROPS.put(TiC.PROPERTY_ACCESSORY_TYPE,
                    TiC.PROPERTY_ACCESSORY_TYPE);
	        TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR,
                    TiC.PROPERTY_BACKGROUND_SELECTED_COLOR);
	        TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE,
                    TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE);
	        TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_GRADIENT,
                    TiC.PROPERTY_BACKGROUND_SELECTED_GRADIENT);
	        TO_PASS_PROPS.put(TiC.PROPERTY_ROW_HEIGHT, TiC.PROPERTY_HEIGHT);
	        TO_PASS_PROPS.put(TiC.PROPERTY_MIN_ROW_HEIGHT, TiC.PROPERTY_MIN_HEIGHT);
	        TO_PASS_PROPS.put(TiC.PROPERTY_MAX_ROW_HEIGHT, TiC.PROPERTY_MAX_HEIGHT);
        }
	    if (toPassProps == null) {
	        toPassProps = new HashMap<>();
	    }
	    for (Map.Entry<String, Object> entry : props.entrySet()) {
            String inProp = entry.getKey();
            Object outProp = entry.getValue();
            if (TO_PASS_PROPS.containsKey(inProp)) {
                toPassProps.put(TO_PASS_PROPS.get(inProp), outProp);
            }
        }
	}
	public HashMap<String, Object> getToPassProps() {
	    return toPassProps;
	}

	public class TiBaseAdapter extends ListViewAnimationsBaseAdapter 
	    implements  StickyListHeadersAdapter,
	                SectionIndexer, 
	                MenuAdapter ,
	                Insertable<Object> , 
	                Removable<Object>, 
	                TiCollectionViewAdapter
	{

		Activity context;
		private boolean mCounted = false;
		private int mCount = 0;
        private boolean canNotifyDataSetChanged = true;
		
		public TiBaseAdapter(Activity activity) {
		    super();
			context = activity;
		}
		
		public boolean hasStableIds() {
	        return true;
	    }

		@Override
		public int getCount() {
		    if (mCounted) {
		        return mCount;
		    }
			mCount = 0;
			synchronized (sections) {
			    for (int i = 0; i < sections.size(); i++) {
	                AbsListSectionProxy section = sections.get(i);
	                mCount += section.getItemCount();
	            }
            }
			mCounted = true;
			return mCount;
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
		
		//One type for header/footer title, one for header/footer view, one for built-in template, and one type per custom template.
		@Override
		public int getViewTypeCount() {
			return itemTypeCount.get();
			
		}
		@Override
		public int getItemViewType(int position) {
			Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
			if (info == null) {
			    return -1;
			}
			AbsListSectionProxy section = info.first;
			int sectionItemIndex = info.second.second;
			if (section.isHeaderView(sectionItemIndex) || section.isFooterView(sectionItemIndex)) {
				return HEADER_FOOTER_VIEW_TYPE;
			}
			return getTemplate(section.getTemplateByIndex(sectionItemIndex), true).getType();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			//Get section info from index
			Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
			if (info == null) {
                return null; // possible because of WrapperView
            }
			AbsListSectionProxy section = info.first;
			if (section.hidden) {
			    return null; // possible because of WrapperView
			}
			int sectionItemIndex = info.second.second;
			int sectionIndex = info.second.first;
			//check marker
			if (sectionIndex > marker[0] || (sectionIndex == marker[0] && sectionItemIndex >= marker[1])) {
				if (proxy.hasListeners(TiC.EVENT_MARKER, false)) {
	                proxy.fireEvent(TiC.EVENT_MARKER, null, false, false);
				}
				resetMarker();
			}

			View content = convertView;
			
			if (section.isFooterView(sectionItemIndex)) {
			    KrollProxy vp = section.getHoldedProxy(TiC.PROPERTY_FOOTER_VIEW);
	            if (vp instanceof TiViewProxy) {
	                return layoutHeaderOrFooterView((TiViewProxy) vp, convertView);
	            }
	            return null;
            }
			
			//Handling templates
			Object item = section.getListItem(sectionItemIndex);
			if (item == null) {
			    return null;
			}
			String templateId = null;
			if (item instanceof HashMap) {
			    templateId = TiConvert.toString((HashMap) item, TiC.PROPERTY_TEMPLATE);
	        }
			TiAbsListViewTemplate template = getTemplate(templateId, true);
			int itemViewType = template.getType();
			
			TiBaseAbsListViewItem itemContent = null;
			if (content != null && (int)content.getTag() == itemViewType) {
				itemContent = (TiBaseAbsListViewItem) content.findViewById(TiBaseAbsListViewItem.listContentId);
				if (itemContent == null) {
				    return content;
				}
				boolean reusing = sectionIndex != itemContent.sectionIndex || 
						itemContent.itemIndex >= section.getItemCount() || 
						item != section.getListItem(itemContent.itemIndex);
				section.populateViews(item, itemContent, template, sectionItemIndex, sectionIndex, content, reusing);
//                setBoundsForBaseItem(itemContent);
			} else {
				content = new TiBaseAbsListViewItemHolder(getContext());
				content.setTag(itemViewType);
				itemContent = (TiBaseAbsListViewItem) content.findViewById(TiBaseAbsListViewItem.listContentId);

				AbsListItemProxy itemProxy = template.generateCellProxy(proxy, getCellProxyRootType());
				itemProxy.setListProxy(proxy);
				addHandledProxy(itemProxy);
				section.generateCellContent(sectionIndex, item, itemProxy, itemContent, template, sectionItemIndex, content);
			}
			if (content instanceof TiBaseAbsListViewItemHolder) {
			    ((TiBaseAbsListViewItemHolder) content).setItem(itemContent, item, listView);

			}
//            setBoundsForBaseItem(content, itemContent, item);
		    canShowMenus |= itemContent.getListItem().canShowMenus();

			return content;

		}
		
		
		public void setCanNotifyDataSetChanged(final boolean canNotifyDataSetChanged) {
		    this.canNotifyDataSetChanged = canNotifyDataSetChanged;
		}
		

//		private void setBoundsForBaseItem(View content)  {
//			TiBaseAbsListViewItemHolder holder;
//			if (content instanceof TiBaseAbsListViewItemHolder)
//			{
//				holder = (TiBaseAbsListViewItemHolder) content;
//			}
//			else if (content instanceof TiBorderWrapperView)
//			{
//				holder = (TiBaseAbsListViewItemHolder) content.getParent();
//			}
//			else return;
//			String minRowHeight = MIN_ROW_HEIGHT;
//			if (proxy != null && proxy.hasProperty(TiC.PROPERTY_MIN_ROW_HEIGHT)) {
//				minRowHeight = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_MIN_ROW_HEIGHT));
//			}
//			item.setMinHeight(TiConvert.toTiDimension(minRowHeight, TiDimension.TYPE_HEIGHT));
//			if (proxy == null) return;
//			if (proxy.hasProperty(TiC.PROPERTY_MAX_ROW_HEIGHT)) {
//				item.setMaxHeight(TiConvert.toTiDimension(proxy.getProperty(TiC.PROPERTY_MAX_ROW_HEIGHT), TiDimension.TYPE_HEIGHT));
//			}
//		}
		
		@Override
		public void notifyDataSetChanged()
		{ 
		    if (!canNotifyDataSetChanged ) {
		        return;
		    }
		    canShowMenus = false;
		    mCounted = false;
//		    mSectionInfoCache.clear();
		    if (listView != null) {
		        // save index and top position
	            int index = listView.getFirstVisiblePosition();
	            View v = listView.getListChildAt(0);
	            int top = (v == null) ? 0 : v.getTop();
	            super.notifyDataSetChanged();
	            // restore
	            //
	            listView.setSelectionFromTop(index, top);
		    }
		    else {
                super.notifyDataSetChanged();
		    }
		}

        @Override
        public long getHeaderId(int position) {
          //Get section info from index
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
            if (info != null) {
                AbsListSectionProxy section = info.first;
                return section.getIndex();
            }
            return -1;
        }

        @Override
        public View getHeaderView(int position, View convertView, ViewGroup parent) {
            //Get section info from index
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
            if (info != null) {
                AbsListSectionProxy section = info.first;
                
                KrollProxy vp = section.getHoldedProxy(TiC.PROPERTY_HEADER_VIEW);
                
                if (vp instanceof TiViewProxy) {
                    return layoutHeaderOrFooterView((TiViewProxy) vp, convertView);
                }
            }
            
            if (convertView != null) {
                return convertView;
            }
            //StickyListHeaderView always wants a header
            return new FrameLayout(getContext());
        }

        @Override
        public Object[] getSections() {
            synchronized (sections) {
                return sections.toArray();
            }
        }

        @Override
        public int getPositionForSection(int sectionIndex) {
            return getSectionFirstPosition(sectionIndex);
        }

        @Override
        public int getSectionForPosition(int position) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
            if (info != null) {
                AbsListSectionProxy section = info.first;
                return section.getIndex();
            } 
            return -1;
        }
        
        @Override
        public Object remove(int position) {
//            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(position);
//            Object result = null;
//            if (info != null) {
//                result = info.first.deleteItemsData(info.second.second, 1);
//            }
            notifyDataSetChanged();
//            return result;
            return null;
        }

        @Override
        public void add(int position, Object data) {
//            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(Math.max(0, position - 1));
//            if (info != null) {
//                info.first.insertItemsData(info.second.second + 1, data);
//            }
            notifyDataSetChanged();
        }

        @Override
        public boolean canShowLeftMenu(int position, final DynamicListItemView view) {
            if (!canShowMenus) return false;
            TiBaseAbsListViewItem viewItem = (TiBaseAbsListViewItem) view.findViewById(TiBaseAbsListViewItem.listContentId);
            if (viewItem != null) {
                TiAbsListItem listItem = viewItem.getListItem();
                return listItem.canShowLeftMenu();
            }
            return false;
        }

        @Override
        public boolean canShowRightMenu(int position, final DynamicListItemView view) {
            if (!canShowMenus) return false;
            TiBaseAbsListViewItem viewItem = (TiBaseAbsListViewItem) view.findViewById(TiBaseAbsListViewItem.listContentId);
            if (viewItem != null) {
                TiAbsListItem listItem = viewItem.getListItem();
                return listItem.canShowRightMenu();
            }
            return false;
        }

        @Override
        public View[] getLeftButtons(int position, final DynamicListItemView view) {
            TiBaseAbsListViewItem viewItem = (TiBaseAbsListViewItem) view.findViewById(TiBaseAbsListViewItem.listContentId);
            if (viewItem != null) {
                TiAbsListItem listItem = viewItem.getListItem();
                return listItem.getLeftButtons();
            }
            return null;
        }

        @Override
        public View[] getRightButtons(int position, final DynamicListItemView view) {
            TiBaseAbsListViewItem viewItem = (TiBaseAbsListViewItem) view.findViewById(TiBaseAbsListViewItem.listContentId);
            if (viewItem != null) {
                TiAbsListItem listItem = viewItem.getListItem();
                return listItem.getRightButtons();
            }
            return null;
        }
	}
	
	private Dictionary<Integer, Integer> listViewItemHeights = new Hashtable<Integer, Integer>();

    public int getScroll() {
        View c = listView.getListChildAt(0); //this is the first visible row
        int scrollY = -c.getTop();
        int first = listView.getFirstVisiblePosition();
        listViewItemHeights.put(first, c.getHeight());
        for (int i = 0; i < first; ++i) {
            if (listViewItemHeights.get(i) != null) // (this is a sanity check)
                scrollY += listViewItemHeights.get(i); //add all heights of the views that are gone
        }
        
        return (int) (scrollY / TiApplication.getAppDensity());
    }
    
    public int getViewHeigth(View v) {
        int viewPosition = listView.getPositionForView(v);
        int scrollY = 0;
        for (int i = 0; i < viewPosition; ++i) {
                scrollY += listView.getListChildAt(i).getHeight();
        }
        return scrollY;
    }
	
    
	private KrollDict dictForScrollEvent(final int yScroll) {
		KrollDict eventArgs = new KrollDict();
		KrollDict size = new KrollDict();
		size.put(TiC.PROPERTY_WIDTH, TiAbsListView.this.getNativeView().getWidth());
		size.put(TiC.PROPERTY_HEIGHT, TiAbsListView.this.getNativeView().getHeight());
		eventArgs.put(TiC.PROPERTY_SIZE, size);
		
        int firstVisibleItem = listView.getFirstVisiblePosition();
        int lastVisiblePosition = listView.getLastVisiblePosition();
		eventArgs.put("firstVisibleItem", firstVisibleItem);
        eventArgs.put("visibleItemCount", lastVisiblePosition - firstVisibleItem);
        KrollDict point = new KrollDict();
        point.put(TiC.PROPERTY_X, 0);
        point.put(TiC.PROPERTY_Y, yScroll);
        eventArgs.put(TiC.PROPERTY_CONTENT_OFFSET, point);
		return eventArgs;
	}
	
	protected KrollDict dictForScrollEvent() {
        return dictForScrollEvent(getScroll());
    }
	
	
    
    protected abstract C createListView(final Activity activity);

	public TiAbsListView(TiViewProxy proxy, Activity activity) {
		super(proxy);
		
		//initializing variables
        sections = Collections.synchronizedList(new ArrayList<AbsListSectionProxy>());
		itemTypeCount = new AtomicInteger(CUSTOM_TEMPLATE_ITEM_TYPE);
		templatesByBinding = new HashMap<String, TiAbsListViewTemplate>();
		defaultTemplateBinding = defaultTemplateKey;
		templatesByBinding.put(defaultTemplateKey, defaultTemplate);
		defaultTemplate.setType(BUILT_IN_TEMPLATE_ITEM_TYPE);
		caseInsensitive = true;
		ignoreExactMatch = false;
		
		//handling marker
		HashMap preloadMarker = ((AbsListViewProxy)proxy).getPreloadMarker();
		if (preloadMarker != null) {
			setMarker(preloadMarker);
		} else {
			resetMarker();
		}
		
		final KrollProxy fProxy = proxy;
		//initializing listView
		listView = createListView(activity);
		listView.setSelector(android.R.color.transparent);
		listView.setAreHeadersSticky(false);
		
//		listView.setDuplicateParentStateEnabled(true);
		AbsListView internalListView = getInternalListView();
		if (TiC.LOLLIPOP_OR_GREATER) {
            listView.setNestedScrollingEnabled(true);
            internalListView.setNestedScrollingEnabled(true);
        }
        if (internalListView instanceof ListView) {
            ((ListView) internalListView).setHeaderDividersEnabled(false);
            ((ListView) internalListView).setFooterDividersEnabled(false);
        }
        
        if (listView instanceof CustomListView) {
            ((CustomListView)listView).setOnPullListener( new OnPullListener() {
                private boolean canUpdate = false;
                @Override
                public void onPull(boolean canUpdate) {
                    if (canUpdate != this.canUpdate) {
                        this.canUpdate = canUpdate;
                        if(fProxy.hasListeners(TiC.EVENT_PULL_CHANGED, false)) {
                            KrollDict event = dictForScrollEvent();
                            event.put("active", canUpdate);
                            fProxy.fireEvent(TiC.EVENT_PULL_CHANGED, event, false, false);
                        }
                    }
                    if(fProxy.hasListeners(TiC.EVENT_PULL, false)) {
                        KrollDict event = dictForScrollEvent();
                        event.put("active", canUpdate);
                        fProxy.fireEvent(TiC.EVENT_PULL, event, false, false);
                    }
                }
        
                @Override
                public void onPullEnd(boolean canUpdate) {
                    if(fProxy.hasListeners(TiC.EVENT_PULL_END, false)) {
                        KrollDict event = dictForScrollEvent();
                        event.put("active", canUpdate);
                        fProxy.fireEvent(TiC.EVENT_PULL_END, event, false, false);
                    }
                }
            });
        }

		adapter = new TiBaseAdapter(activity);
		listView.setOnScrollListener(new OnScrollListener()
		{
			private boolean scrollTouch = false;
			private int lastValidfirstItem = 0;
			private Timer endTimer = null;
			
			public void cancelEndCall() {
			    if (endTimer != null) {
                    endTimer.cancel();
                    endTimer = null;
                }
			}
			public void delayEndCall() {
			    cancelEndCall();
			    endTimer = new Timer();

			    TimerTask action = new TimerTask() {
			        public void run() {
			            scrollTouch = false;
                        if(fProxy.hasListeners(TiC.EVENT_SCROLLEND, false)) {
                            fProxy.fireEvent(TiC.EVENT_SCROLLEND, dictForScrollEvent(), false, false);
                        }
			        }

			    };

			    this.endTimer.schedule(action, 200); 
			}
			
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState)
			{
                
				view.requestDisallowInterceptTouchEvent(scrollState != ViewPager.SCROLL_STATE_IDLE);
				if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
				    if (scrollTouch) {
	                    delayEndCall();
				    }
				}
				else if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
				    cancelEndCall();
                }
				else if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    cancelEndCall();
				    if (hideKeyboardOnScroll && hasFocus()) {
	                    blur();
	                }
					if (scrollTouch == false) {
					    scrollTouch = true;
						if(fProxy.hasListeners(TiC.EVENT_SCROLLSTART, false)) {
                            fProxy.fireEvent(TiC.EVENT_SCROLLSTART, dictForScrollEvent(), false, false);
                        }
					}
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
			{
//				Log.d(TAG, "onScroll : " + scrollValid, Log.DEBUG_MODE);
//				boolean fireScroll = scrollValid;
//				if (!fireScroll && visibleItemCount > 0) {
//					//Items in a list can be selected with a track ball in which case
//					//we must check to see if the first visibleItem has changed.
//					fireScroll = (lastValidfirstItem != firstVisibleItem);
//				}
				if(fProxy.hasListeners(TiC.EVENT_SCROLL, false)) {
				    int newScrollOffset = getScroll();
//	                Log.d(TAG, "newScrollOffset : " + newScrollOffset, Log.DEBUG_MODE);
                    lastValidfirstItem = firstVisibleItem;
				    if (newScrollOffset != currentScrollOffset) {
				        currentScrollOffset = newScrollOffset;
	                    fProxy.fireEvent(TiC.EVENT_SCROLL, dictForScrollEvent(currentScrollOffset), false, false);
				    }
				}
			}
		});
		
		listView.setOnStickyHeaderChangedListener(new OnStickyHeaderChangedListener() {
            
            @Override
            public void onStickyHeaderChanged(StickyListHeadersListViewAbstract l, View header,
                    int itemPosition, long headerId) {
                //for us headerId is the section index
                int sectionIndex = (int) headerId;
                if (fProxy.hasListeners(TiC.EVENT_HEADER_CHANGE, false)) {
                    KrollDict data = new KrollDict();
                    AbsListSectionProxy section = null;
                    synchronized (sections) {
                        if (sectionIndex >= 0 && sectionIndex < sections.size()) {
                            section = sections.get(sectionIndex);
                        }
                        else {
                            return;
                        }
                    }
                    data.put(TiC.PROPERTY_HEADER_VIEW, section.getHoldedProxy(TiC.PROPERTY_HEADER_VIEW));
                    data.put(TiC.PROPERTY_SECTION, section);
                    data.put(TiC.PROPERTY_SECTION_INDEX, sectionIndex);
                    fProxy.fireEvent(TiC.EVENT_HEADER_CHANGE, data, false, false);
                }
            }
        });

		internalListView.setCacheColorHint(Color.TRANSPARENT);
		listView.setEnabled(true);
		getLayoutParams().autoFillsHeight = true;
		getLayoutParams().autoFillsWidth = true;
//		listView.setFocusable(false);
		listView.setFocusable(true);
		listView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

// 		try {
// //			headerFooterId = TiRHelper.getApplicationResource("layout.titanium_ui_list_header_or_footer");
// //			titleId = TiRHelper.getApplicationResource("id.titanium_ui_list_header_or_footer_title");
// 			isCheck = TiRHelper.getApplicationResource("drawable.btn_check_buttonless_on_64");
// 			hasChild = TiRHelper.getApplicationResource("drawable.btn_more_64");
// 			disclosure = TiRHelper.getApplicationResource("drawable.disclosure_64");
// 		} catch (ResourceNotFoundException e) {
// 			Log.e(TAG, "XML resources could not be found!!!", Log.DEBUG_MODE);
// 		}
		setNativeView(listView);
	}
	
	@Override
	protected void handleTouchEvent(MotionEvent event) {
	    super.handleTouchEvent(event);
	    if (event.getAction() == MotionEvent.ACTION_UP) {
	        final int x = (int) event.getX();
            final int y = (int) event.getY();
            int motionPosition = listView.getWrappedList().pointToPosition(x, y);
            if (motionPosition == -1) {
                listView.performClick();
            }
        }
    }
	
    @Override
    protected TiUIView associatedTiViewForView(View childView) {
        if (childView instanceof DynamicWrapperViewList) {
            return this;
        }
        if (childView instanceof WrapperView) {
            View view = childView.findViewById(TiBaseAbsListViewItem.listContentId);
            if (view != null) {
                return ((TiCompositeLayout) view).getView();
            }
        }
        return super.associatedTiViewForView(childView);
    }
    
    @Override
    protected boolean viewShouldPassThrough(final View view, final MotionEvent event) {
        if (view == listView) {
            return touchPassThrough(getInternalListView(), event);
        }
        return super.viewShouldPassThrough(view, event);
    }
	
    @Override
	public String getSearchText() {
		return searchText;
	}


	private void resetMarker() 
	{
		marker[0] = Integer.MAX_VALUE;
		marker[1] = Integer.MAX_VALUE;
	}

	public void setHeaderTitle(String title) {
	    if (title != null) {
            ViewProxy vp = (ViewProxy) this.proxy.addProxyToHold(headerViewDict(title), "headerView");
            getOrCreateHeaderWrapperView().add(vp, 1);
	    } else {
	        this.proxy.removeHoldedProxy("headerView");
	    }
	}
	
	public void setFooterTitle(String title) {
	    if (title != null) {
            ViewProxy vp = (ViewProxy) this.proxy.addProxyToHold(footerViewDict(title), "footerView");
            getOrCreateFooterWrapperView().add(vp, 1);
        } else {
            this.proxy.removeHoldedProxy("footerView");
        }
	}

//	private TiUIView layoutHeaderOrFooter(TiViewProxy viewProxy)
//	{
//		//We are always going to create a new view here. So detach outer view here and recreate
//		View outerView = (viewProxy.peekView() == null) ? null : viewProxy.peekView().getOuterView();
//		if (outerView != null) {
//			ViewParent vParent = outerView.getParent();
//			if ( vParent instanceof ViewGroup ) {
//				((ViewGroup)vParent).removeView(outerView);
//			}
//		}
//		TiUIView tiView = viewProxy.forceCreateView();
//		View nativeView = tiView.getOuterView();
//		TiCompositeLayout.LayoutParams params = tiView.getLayoutParams();
//
//		int width = AbsListView.LayoutParams.WRAP_CONTENT;
//		int height = AbsListView.LayoutParams.WRAP_CONTENT;
//		if (params.sizeOrFillHeightEnabled) {
//			if (params.autoFillsHeight) {
//				height = AbsListView.LayoutParams.MATCH_PARENT;
//			}
//		} else if (params.optionHeight != null) {
//			height = params.optionHeight.getAsPixels(listView);
//		}
//		if (params.sizeOrFillWidthEnabled) {
//			if (params.autoFillsWidth) {
//				width = AbsListView.LayoutParams.MATCH_PARENT;
//			}
//		} else if (params.optionWidth != null) {
//			width = params.optionWidth.getAsPixels(listView);
//		}
//		AbsListView.LayoutParams p = new AbsListView.LayoutParams(width, height);
//		nativeView.setLayoutParams(p);
//		return tiView;
//	}

	@Override
	public void registerForTouch()
	{
		registerForTouch(listView);
	}
	
	public void setMarker(HashMap markerItem) 
	{
		marker[0] = TiConvert.toInt(markerItem, TiC.PROPERTY_SECTION_INDEX, -1);
		marker[1] = TiConvert.toInt(markerItem, TiC.PROPERTY_ITEM_INDEX, -1);
		
	}
	
	protected void notifyDataSetChanged() {
	    if (!TiApplication.isUIThread()) {
	        proxy.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
            return;
	    }
	    if (adapter != null) {
	        adapter.notifyDataSetChanged();
	    }
	}
	
	protected static final ArrayList<String> KEY_SEQUENCE;
    static{
      ArrayList<String> tmp = new ArrayList<String>();
      tmp.add(TiC.PROPERTY_SEARCH_TEXT); //make sure searchText is set before sections
      KEY_SEQUENCE = tmp;
    }
    @Override
    protected ArrayList<String> keySequence() {
        return KEY_SEQUENCE;
    }
	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_TEMPLATES:
            processTemplates((HashMap)newValue);
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            mProcessUpdateFlags |= TIFLAG_NEEDS_ADAPTER_CHANGE;
//           if (changedProperty) {
//                notifyDataSetChanged();
//            }
            break;
        case TiC.PROPERTY_SEARCH_TEXT:
            filterBy(TiConvert.toString(newValue));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_SEARCH_IGNORE_EXACT_MATCH:
            this.ignoreExactMatch = TiConvert.toBoolean(newValue, true);
            filterBy(TiConvert.toString(this.searchText));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_SEARCH_VIEW:
            setSearchView(newValue, true);
            break;
        case TiC.PROPERTY_SEARCH_VIEW_EXTERNAL:
            setSearchView(newValue, false);
            break;
        case TiC.PROPERTY_SCROLL_HIDES_KEYBOARD:
            this.hideKeyboardOnScroll = TiConvert.toBoolean(newValue, true);
            break;
        case TiC.PROPERTY_STICKY_HEADERS:
            listView.setAreHeadersSticky(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_CASE_INSENSITIVE_SEARCH:
            this.caseInsensitive = TiConvert.toBoolean(newValue, true);
            filterBy(TiConvert.toString(this.searchText));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_SEPARATOR_COLOR:
        {
            AbsListView internalListView = getInternalListView();
            if (internalListView instanceof ListView) {
                int dividerHeight = listView.getDividerHeight();
                ((ListView) internalListView).setDivider(new ColorDrawable(TiConvert.toColor(newValue)));
                ((ListView) internalListView).setDividerHeight(dividerHeight);
            }
            break;
        }
        case TiC.PROPERTY_FOOTER_DIVIDERS_ENABLED:
        {
            AbsListView internalListView = getInternalListView();
            if (internalListView instanceof ListView) {
                ((ListView) internalListView).setFooterDividersEnabled(TiConvert.toBoolean(newValue, false));
            }
            break;
        }
        case TiC.PROPERTY_HEADER_DIVIDERS_ENABLED:
        {
            AbsListView internalListView = getInternalListView();
            if (internalListView instanceof ListView) {
                ((ListView) internalListView).setHeaderDividersEnabled(TiConvert.toBoolean(newValue, false));
            }
            break;
        }
        case TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR:
            listView.setVerticalScrollBarEnabled(TiConvert.toBoolean(newValue, true));
            break;
        case TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE:
            defaultTemplateBinding = TiConvert.toString(newValue);
            if (changedProperty) {
               notifyDataSetChanged();
            }
            break;
        case TiC.PROPERTY_SECTIONS:
            if (changedProperty) {
                mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
               processSectionsAndNotify((Object[])newValue);
            } else {
                //if user didn't append/modify/delete sections before this is called, we process sections
                //as usual. Otherwise, we process the preloadSections, which should also contain the section(s)
                //from this dictionary as well as other sections that user append/insert/deleted prior to this.
                AbsListViewProxy listProxy = (AbsListViewProxy) proxy;
                if (!listProxy.getPreload()) {
                    processSections((Object[])newValue);
                }
            }
            break;
        case TiC.PROPERTY_SEPARATOR_STYLE:
            Drawable drawable = listView.getDivider();
            listView.setDivider(drawable);
            listView.setDividerHeight(TiConvert.toInt(newValue, TiUITableView.SEPARATOR_SINGLE_LINE));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
           break;
        case TiC.PROPERTY_OVER_SCROLL_MODE:
//            if (Build.VERSION.SDK_INT >= 9) {
                listView.setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
//            }
            break;
        case TiC.PROPERTY_HEADER_VIEW:
            setHeaderOrFooterView(newValue, true);
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
           break;
        case TiC.PROPERTY_HEADER_TITLE:
            setHeaderTitle(TiConvert.toString(newValue));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_FOOTER_VIEW:
            setHeaderOrFooterView(newValue, false);
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_FOOTER_TITLE:
//            if (footerView == null || footerView.getId() != HEADER_FOOTER_WRAP_ID) {
//                if (footerView == null) {
//                    footerView = inflater.inflate(headerFooterId, null);
//                }
                setFooterTitle(TiConvert.toString(newValue));
//            }
                mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_PULL_VIEW:
            ((RefreshableListView) listView).setHeaderPullView(setPullView(newValue));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
//		case TiC.PROPERTY_REFRESH_CONTROL:
//			if (newValue instanceof RefreshControlProxy) {
//				((RefreshControlProxy)newValue).assignTo(this.wrapper);
//			} else {
//				RefreshControlProxy.unassignFrom(this.wrapper);
//			}
//            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
	@Override
    protected void aboutToProcessProperties(HashMap d) {
	    
        super.aboutToProcessProperties(d);
        updateToPassProps(d);
        if (adapter != null) {
            adapter.setCanNotifyDataSetChanged(false);
        }
        if (listView.getAdapter() == null) {
            mProcessUpdateFlags |= TIFLAG_NEEDS_ADAPTER_CHANGE;
        }
    }
    
	@Override
    protected void didProcessProperties() {
	    AbsListViewProxy listProxy = (AbsListViewProxy) proxy;
        
        if (listProxy.getPreload()) {
            ArrayList preload = listProxy.getPreloadSections();
            if (preload != null) {
                processSections(preload.toArray());
            }
            listProxy.setPreload(false);
            listProxy.clearPreloadSections();
        }
        super.didProcessProperties();
	    if (adapter != null) {
            adapter.setCanNotifyDataSetChanged(true);
        }
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_ADAPTER_CHANGE) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_ADAPTER_CHANGE;
            setListViewAdapter(adapter);
        }
	    if ((mProcessUpdateFlags & TIFLAG_NEEDS_DATASET) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
	        notifyDataSetChanged();
        }
    }
	
	protected void setListViewAdapter (TiBaseAdapter adapter) {
        listView.setAdapter(adapter);
	}
	
	private void setHeaderOrFooterView (Object viewObj, boolean isHeader) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj, isHeader?"headerView":"footerView", false, true);
        if (viewProxy instanceof TiViewProxy) {
            if (isHeader) {
                getOrCreateHeaderWrapperView().add(viewProxy, 1);
            } else {
                getOrCreateFooterWrapperView().add(viewProxy, 1);
            }
        }
	}
	
	private void setSearchView (Object viewObj, boolean addInHeader) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj, "search");
        if (isSearchViewValid(viewProxy)) {            
            TiUIView search = ((TiViewProxy) viewProxy).getOrCreateView();
            setSearchListener((TiViewProxy) viewProxy, search);
            if (addInHeader) {
                getOrCreateHeaderWrapperView().add(viewProxy, 0);
            }
        } else {
            Log.e(TAG, "Searchview type is invalid");
        }
    }
    

	private void reFilter(String searchText) {
        synchronized (sections) {
			for (int i = 0; i < sections.size(); ++i) {
				AbsListSectionProxy section = sections.get(i);
				section.applyFilter(searchText, caseInsensitive, ignoreExactMatch);
			}
		}
		notifyDataSetChanged();
	}

	private boolean isSearchViewValid(Object proxy) {
		if (proxy instanceof SearchBarProxy || proxy instanceof SearchViewProxy) {
			return true;
		} else {
			return false;
		}
	}

	private void setSearchListener(TiViewProxy searchView, TiUIView search) 
	{
		if (searchView instanceof SearchBarProxy) {
			((TiUISearchBar)search).setOnSearchChangeListener(this);
		} else if (searchView instanceof SearchViewProxy) {
			((TiUISearchView)search).setOnSearchChangeListener(this);
		}
	}

	public TiAbsListViewTemplate getTemplate(String template, final boolean canReturnDefault)
	{
		if (template == null) {
		    template = defaultTemplateBinding;
		}
		if (templatesByBinding.containsKey(template)) {
			return templatesByBinding.get(template);
		}
		if (canReturnDefault) {
	        return templatesByBinding.get(UIModule.LIST_ITEM_TEMPLATE_DEFAULT);
		}
		return null;
	}

	protected void processTemplates(HashMap<String,Object> templates) {
		templatesByBinding = new HashMap<String, TiAbsListViewTemplate>();
		templatesByBinding.put(defaultTemplateKey, defaultTemplate);
		if(templates != null) {
			for (String key : templates.keySet()) {
				HashMap templateDict = (HashMap)templates.get(key);
				if (templateDict != null) {
					//Here we bind each template with a key so we can use it to look up later
					KrollDict properties = new KrollDict((HashMap)templates.get(key));
					TiAbsListViewTemplate template = new TiAbsListViewTemplate(key, properties);
					template.setType(getItemType());
					templatesByBinding.put(key, template);
				}
				else {
					Log.e(TAG, "null template definition: " + key);
				}
			}
		}
	}
	
	private ViewProxy getOrCreateHeaderWrapperView() {
	    ViewProxy vp = (ViewProxy) this.proxy.getHoldedProxy("headerWrapper");
	    if (vp == null) {
	        KrollDict props = new KrollDict();
            props.put("width", "FILL");
            props.put("height", "SIZE");
            props.put("layout", "vertical");
            props.put("touchPassThrough", true);
            vp = (ViewProxy) this.proxy.addProxyToHold(props, "headerWrapper");
        }
	    TiUIView view = ((ViewProxy)vp).getOrCreateView();
        view.setCustomLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, ListView.LayoutParams.WRAP_CONTENT));
        listView.addHeaderView(view.getOuterView(), null, false);
	    return vp;
	}
	private ViewProxy getOrCreateFooterWrapperView() {
        ViewProxy vp = (ViewProxy) this.proxy.getHoldedProxy("footerWrapper");
        if (vp == null) {
            KrollDict props = new KrollDict();
            props.put("width", "FILL");
            props.put("height", "SIZE");
            props.put("layout", "vertical");
            props.put("touchPassThrough", true);
            vp = (ViewProxy) this.proxy.addProxyToHold(props, "footerWrapper");
        }
        TiUIView view = ((ViewProxy)vp).getOrCreateView();
        view.setCustomLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, ListView.LayoutParams.WRAP_CONTENT));
        listView.addFooterView(view.getOuterView(), null, false);
        return vp;
    }
	
	
	private static KrollDict DEFAULT_HEADER_DICT = null;
	public static KrollDict headerViewDict(final String text) {
	    try {
	        if (DEFAULT_HEADER_DICT == null) {
//	            if (TiC.LOLLIPOP_OR_GREATER) {
	                int colorAccent = TiUIHelper.getColorAccent(TiApplication.getAppCurrentActivity());
                    String color = TiColorHelper.toHexString(colorAccent);
	                DEFAULT_HEADER_DICT = new KrollDict("{type:'Ti.UI.Label',font:{size:14, weight:'bold'},padding:{top:12, bottom:1},color:'" + color + "',width:'FILL',left:15,right:15}");
//	            } else {
//	                DEFAULT_HEADER_DICT = new KrollDict("{type:'Ti.UI.Label',font:{size:14, weight:'bold'},padding:{left:8, right:8,top:7, bottom:7},borderPadding:{left:-2.5, right:-2.5, top:-2.5},borderColor:'#666',borderWidth:2.5,color:'#ccc',width:'FILL',left:15,right:15,autocapitalization:true}");
//	            }
	        }
        } catch (JSONException e) {
        }
	    KrollDict result = new KrollDict(DEFAULT_HEADER_DICT);
	    result.put(TiC.PROPERTY_TEXT, text);
	    return result;
    }
	
	private static KrollDict DEFAULT_FOOTER_DICT = null;
    public static KrollDict footerViewDict(final String text) {
        try {
            if (DEFAULT_FOOTER_DICT == null) {
                DEFAULT_FOOTER_DICT = new KrollDict("{type:'Ti.UI.Label',font:{size:14},padding:{left:8, right:8,top:8, bottom:8},color:'#ccc',width:'FILL',left:15,right:15}");
            }
        } catch (JSONException e) {
        }
        KrollDict result = new KrollDict(DEFAULT_FOOTER_DICT);
        result.put(TiC.PROPERTY_TEXT, text);
        return result;
    }
	
	public static View layoutHeaderOrFooterView (TiViewProxy viewProxy, View convertView) {
		TiUIView tiView = viewProxy.getOrCreateView();
		View outerView = null;
		ViewGroup parentView = null;
		if (tiView != null) {
		    outerView = tiView.getOuterView();
	        parentView = (ViewGroup) outerView.getParent();
		}
		if ((parentView != null && parentView.getId() == HEADER_FOOTER_WRAP_ID) && (convertView == null || convertView == parentView)) {
			return parentView;
		} else {
		    
		    TiCompositeLayout wrapper = null;
		    if (convertView instanceof TiCompositeLayout && convertView.getId() == HEADER_FOOTER_WRAP_ID) {
		        wrapper = (TiCompositeLayout) convertView;
		        wrapper.removeAllViews();
	        } else {
	            wrapper = new TiCompositeLayout(viewProxy.getActivity(), LayoutArrangement.DEFAULT, null);
	          //add a wrapper so layout params such as height, width takes in effect.
	            AbsListView.LayoutParams params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,  AbsListView.LayoutParams.WRAP_CONTENT);
	            wrapper.setLayoutParams(params);
	            wrapper.setInternalTouchPassThrough(true);
	        }
		    if (parentView != null) {
		        parentView.removeView(outerView);
		    }
//	        TiUIHelper.removeViewFromSuperView(viewProxy);
			
			if (outerView != null && tiView != null) {
			    TiCompositeLayout.LayoutParams headerParams = tiView.getLayoutParams();
			      //If height is not dip, explicitly set it to SIZE
		        if (!headerParams.fixedSizeHeight()) {
		            headerParams.sizeOrFillHeightEnabled = true;
		            headerParams.autoFillsHeight = false;
		        }
		        if (headerParams.optionWidth == null && !viewProxy.hasProperty(TiC.PROPERTY_WIDTH)) {
		            headerParams.sizeOrFillWidthEnabled = true;
		            headerParams.autoFillsWidth = true;
		        }
	            wrapper.addView(outerView, tiView.getLayoutParams());
			}
            wrapper.setId(HEADER_FOOTER_WRAP_ID);
            wrapper.setTag(HEADER_FOOTER_WRAP_ID);
			return wrapper;
		}
	}

	protected void processSections(Object[] sections) {
		synchronized (this.sections) {
		    this.sections.clear();
		}
        for (int i = 0; i < sections.length; i++) {
            processSection(sections[i], -1);
        }
	}
	
	public void processSectionsAndNotify(Object[] sections) {
	    (new ProcessSectionsTask()).execute(sections);
//		processSections(sections);
//		if (adapter != null) {
//			adapter.notifyDataSetChanged();
//		}
	}
	
private class ProcessSectionsTask extends AsyncTask<Object[], Void, Void> {
        
        @Override
        protected Void doInBackground(Object[]... params) {
            processSections(params[0]);
            AbsListViewProxy listProxy = (AbsListViewProxy) proxy;
            listProxy.clearPreloadSections();
            listProxy.setPreload(false);
            return null;
        }
        
        @Override
        protected void onPostExecute(Void result) {
            notifyDataSetChanged();
        }

    }
	
	protected void processSection(Object sec, int index) {
		if (sec instanceof AbsListSectionProxy) {
			AbsListSectionProxy section = (AbsListSectionProxy) sec;
            section.setListView(this);
            section.setActivity(proxy.getActivity());
            section.setAdapter(adapter);
			synchronized (sections) {
			    if (this.sections.contains(section)) {
	                return;
	            }
	            if (index == -1 || index >= sections.size()) {
	                section.setIndex(this.sections.size());
	                this.sections.add(section);
	            } else {
	                section.setIndex(index);
	                this.sections.add(index, section);
	            }
            }
			
			//Attempts to set type for existing templates.
//			section.setTemplateType();
			//Process preload data if any
			section.processPreloadData();
			//Apply filter if necessary
			if (searchText != null) {
				section.applyFilter(searchText, caseInsensitive, ignoreExactMatch);
			}
		}
		else if(sec instanceof HashMap) {
			AbsListSectionProxy section = (AbsListSectionProxy) KrollProxy.createProxy(((AbsListViewProxy) proxy).sectionClass(), null, new Object[]{sec}, null);
            section.getKrollObject(); //make sure the krollobject exists
			section.updateKrollObjectProperties();
			processSection(section, index);
		}
	}
	
	public Object getItem(int sectionIndex, int itemIndex) {
		if (sectionIndex < 0 || sectionIndex >= sections.size()) {
			Log.e(TAG, "getItem Invalid section index");
			return null;
		}
		synchronized (sections) {
	        return sections.get(sectionIndex).getItemAt(itemIndex);
        }
	}
	
	public AbsListSectionProxy getSectionAt(int sectionIndex) {
        synchronized (sections) {
    		if (sectionIndex < 0 || sectionIndex >= sections.size()) {
    			Log.e(TAG, "getItem Invalid section index");
    			return null;
    		}
    		
    		return sections.get(sectionIndex);
        }
	}
	
	
	protected Pair<AbsListSectionProxy, Pair<Integer, Integer>> getSectionInfoByEntryIndex(int index) {
		if (index < 0) {
			return null;
		}
//		if (mSectionInfoCache .containsKey(index)) {
//		    return (Pair<AbsListSectionProxy, Pair<Integer, Integer>>) mSectionInfoCache.get(index);
//		}
        synchronized (sections) {
    		for (int i = 0; i < sections.size(); i++) {
    			AbsListSectionProxy section = sections.get(i);
    			int sectionItemCount = section.getItemCount();
    			if (index <= sectionItemCount - 1) {
    			    Pair<AbsListSectionProxy, Pair<Integer, Integer>> result = new Pair<AbsListSectionProxy, Pair<Integer, Integer>>(section, new Pair<Integer, Integer>(i, index));
//    			    mSectionInfoCache.put(index, result);
    				return result;
    			} else {
    				index -= sectionItemCount;
    			}
    		}
        }

		return null;
	}
	
	protected int getSectionFirstPosition(int sectionIndex) {
        int result = 0;
        synchronized (sections) {
            for (int i = 0; i < sectionIndex; i++) {
                result += sections.get(i).getItemCount();
            }
        }

        return result;
    }
    
	
	public int getItemType() {
		return itemTypeCount.getAndIncrement();
	}
	
	public TiAbsListViewTemplate getTemplateByBinding(String binding) {
		return templatesByBinding.get(binding);
	}
	
	public String getDefaultTemplateBinding() {
		return defaultTemplateBinding;
	}
	
	public int getSectionCount() {
        synchronized (sections) {
            return sections.size();
        }
	}
	
	public void appendSection(Object section) {
		if (section instanceof Object[]) {
			Object[] secs = (Object[]) section;
			for (int i = 0; i < secs.length; i++) {
				processSection(secs[i], -1);
			}
		} else {
			processSection(section, -1);
		}
		notifyDataSetChanged();
	}
	
	public void deleteSectionAt(int index) {
        synchronized (sections) {
    		if (index >= 0 && index < sections.size()) {
    			sections.remove(index);
    			notifyDataSetChanged();
    		} else {
    			Log.e(TAG, "Invalid index to delete section");
    		}
        }
	}
	
	public void insertSectionAt(int index, Object section) {
        synchronized (sections) {
    		if (index > sections.size()) {
    			Log.e(TAG, "Invalid index to insert/replace section");
    			return;
    		}
        }
		if (section instanceof Object[]) {
			Object[] secs = (Object[]) section;
			for (int i = 0; i < secs.length; i++) {
				processSection(secs[i], index);
				index++;
			}
		} else {
			processSection(section, index);
		}
		notifyDataSetChanged();
	}
	
	public void replaceSectionAt(int index, Object section) {
		deleteSectionAt(index);
		insertSectionAt(index, section);
	}
	
	public int findItemPosition(int sectionIndex, int sectionItemIndex) {
		int position = 0;
        synchronized (sections) {
    		for (int i = 0; i < sections.size(); i++) {
    			AbsListSectionProxy section = sections.get(i);
    			if (i == sectionIndex) {
    				if (sectionItemIndex >= section.getContentCount()) {
    					Log.e(TAG, "Invalid item index");
    					return -1;
    				}
    				position += sectionItemIndex;
//    				if (section.hasHeader()) {
//    				    position += 1;			
//    				}
    				if (section.hasFooter()) {
    					position += 1;			
    				}
    				break;
    			} else {
    				position += section.getItemCount();
    			}
    		}
        }
		return position;
	}
	
	public int getHeaderViewCount() {
	    return listView.getHeaderViewsCount();
	}

	private int getCount() {
		if (adapter != null) {
			return adapter.getCount();
		}
		return 0;
	}
	
	public static void ensureVisible(StickyListHeadersListViewAbstract listView, int pos)
	{
	    if (listView == null)
	    {
	        return;
	    }

	    if(pos < 0 || pos >= listView.getCount())
	    {
	        return;
	    }

	    int first = listView.getFirstVisiblePosition();
	    int last = listView.getLastVisiblePosition();

	    if (pos < first)
	    {
	        listView.setSelection(pos);
	        return;
	    }

	    if (pos >= last)
	    {
	        listView.setSelection(1 + pos - (last - first));
	        return;
	    }
	}
	
	public void scrollToItem(int sectionIndex, int sectionItemIndex, boolean animated) {
		final int position = findItemPosition(sectionIndex, sectionItemIndex);
		if (position > -1) {
			if (animated)
				listView.smoothScrollToPosition(position);
			else
				ensureVisible(listView, position);
		}
	}

	public void scrollToTop(final int y, boolean animated)
	{
		if (animated) {
			listView.smoothScrollToPositionFromTop(0, y);
		}
		else {
			listView.setSelectionFromTop(0, y); 
		}
	}

	public void scrollToBottom(final int y, boolean animated)
	{
		//strangely if i put getCount()-1 it doesnt go to the full bottom but make sure the -1 is shown …
		if (animated) {
			listView.smoothScrollToPosition(getCount()-1);
		}
		else {
			listView.setSelection(getCount()-1);
		}
	}

	@Override
	public void release() {
        synchronized (sections) {
    		for (int i = 0; i < sections.size(); i++) {
    			sections.get(i).release();
    		}
    		
    		sections.clear();
        }
        templatesByBinding.clear();

		// If a refresh control is currently assigned, then detach it.
//		RefreshControlProxy.unassignFrom(this.wrapper);
		
		if (handledProxies != null) {
		    for (TiViewProxy viewProxy : handledProxies) {
	            viewProxy.releaseViews(true);
	            viewProxy.setParent(null);
	        }
	        handledProxies = null;
		}
		


		if (listView != null) {
			listView.setAdapter(null);
			listView = null;
		}
//		footerView = null;

		super.release();
	}

	@Override
	public void filterBy(String text)
	{
		this.searchText = text;
		reFilter(text);
	}
	
	public AbsListSectionProxy[] getSections()
	{
	    synchronized (sections) {
	        return sections.toArray(new AbsListSectionProxy[sections.size()]);
        }
	}
	
	public KrollProxy getChildByBindId(int sectionIndex, int itemIndex, String bindId) {
	    
	    View content = getCellAt(sectionIndex, itemIndex);
        if (content != null) {
            TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content.findViewById(TiBaseAbsListViewItem.listContentId);
            if (listItem != null) {
                if (listItem.getItemIndex() == itemIndex) {
                    return listItem.getViewProxyFromBinding(bindId);
                }
			}
		}
		return null;
	}
	
	public View getCellAt(int sectionIndex, int itemIndex) {
	    if (listView == null) {
	        return null;
	    }
        int position = findItemPosition(sectionIndex, itemIndex);
        int childCount = listView.getListChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = listView.getListChildAt(i);
            TiBaseAbsListViewItem itemContent = (TiBaseAbsListViewItem) child.findViewById(TiBaseAbsListViewItem.listContentId);
            if (itemContent != null) {
                //first visible item of ours
                int firstposition = findItemPosition(itemContent.getSectionIndex(), itemContent.getItemIndex());
                position -= firstposition;
                break;
            }
            else {
                position++;
            }
        }
        if (position > -1) {
            View content = listView.getListChildAt(position);
            return content;
            
        }
        return null;
    }
	

    
    private View setPullView (Object viewObj) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj, "pull");
        if (viewProxy instanceof ViewProxy) {
            return layoutHeaderOrFooterView((TiViewProxy) viewProxy, null);
        }
        return null;
    }
    
    public void showPullView(final boolean animated) {
        if (!TiApplication.isUIThread()) {
            proxy.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showPullView(animated);
                }
            });
            return;
        }
        ((RefreshableListView) listView).showHeaderPullView(animated);
    }
    
    public void closePullView(final boolean animated) {
        if (!TiApplication.isUIThread()) {
            proxy.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    closePullView(animated);
                }
            });
            return;
        }
        ((RefreshableListView) listView).closeHeaderPullView(animated);
    }

    public Object getContentOffset() {
        if (nativeView == null)
            return proxy.getProperty(TiC.PROPERTY_CONTENT_OFFSET);
        KrollDict point = new KrollDict();
        point.put(TiC.PROPERTY_X, 0);
        point.put(TiC.PROPERTY_Y, getScroll());
        return point;
    }
    
    public void setContentOffset(final Object value, final boolean animated) {
        if (nativeView == null)
            return;
        TiPoint point = TiConvert.toPoint(value);
        if (point != null) {
            Point p = point.compute(nativeView.getWidth(),
                    nativeView.getHeight());
            scrollToTop(p.y, animated);
        }
    }
}
