/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.abslistview;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.AnimatableReusableProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.KrollProxyReusableListener;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;

import android.view.View;

@Kroll.proxy(propertyAccessors = {
        TiC.PROPERTY_HEADER_TITLE,
        TiC.PROPERTY_FOOTER_TITLE,
        TiC.PROPERTY_HEADER_VIEW,
        TiC.PROPERTY_FOOTER_VIEW,
        "showHeaderWhenHidden",
        "hideWhenEmpty"
    })
public class AbsListSectionProxy extends AnimatableReusableProxy {

	private static final String TAG = "ListSectionProxy";
//	private ArrayList<AbsListItemData> listItemData;
	protected int mItemCount;
	protected int mCurrentItemCount = 0;
    protected TiCollectionViewAdapter adapter;
	protected Object[] itemProperties;
	protected ArrayList<Integer> filterIndices;
	protected boolean preload;
	protected SortedSet<Integer> mHiddenItems;
	public boolean showHeaderWhenHidden = false;
	public boolean hideWhenEmpty = false;
	public boolean hidden = false;
	
	protected int sectionIndex;

	protected WeakReference<TiCollectionViewInterface> listView;


//	public class AbsListItemData {
//		private KrollDict properties;
//		private String searchableText;
//		private String template = null;
//		private boolean visible = true;
//
//		public AbsListItemData(KrollDict properties) {
//			setProperties(properties);
//		}
//		
//		private void updateSearchableAndVisible() {
//		    if (properties.containsKey(TiC.PROPERTY_PROPERTIES)) {
//                Object props = properties.get(TiC.PROPERTY_PROPERTIES);
//                if (props instanceof HashMap) {
//                    HashMap<String, Object> propsHash = (HashMap<String, Object>) props;
//                    
//                    if (propsHash.containsKey(TiC.PROPERTY_VISIBLE)) {
//                        visible = TiConvert.toBoolean(propsHash,
//                                TiC.PROPERTY_VISIBLE, true);
//                    }
//                }
//            }
//		    if (properties.containsKey(TiC.PROPERTY_SEARCHABLE_TEXT)) {
//                searchableText = TiConvert.toString(properties,
//                        TiC.PROPERTY_SEARCHABLE_TEXT);
//            }
//		}
//
//		public KrollDict getProperties() {
//			return properties;
//		}
//
//		public String getSearchableText() {
//			return searchableText;
//		}
//		
//
//		public boolean isVisible() {
//			return visible;
//		}
//
//
//		public String getTemplate() {
//			return template;
//		}
//
//        public void setProperties(KrollDict d) {
//            this.properties = d;
//            if (properties.containsKey(TiC.PROPERTY_TEMPLATE)) {
//                this.template = properties.getString(TiC.PROPERTY_TEMPLATE);
//            }
//            // set searchableText
//            updateSearchableAndVisible();
//        }
//        
//        public void setProperty(String binding, String key, Object value) {
//            if (properties.containsKey(binding)) {
//                ((HashMap)properties.get(binding)).put(key, value);
//            }
//        }
//	}
    
	public AbsListSectionProxy() {
//        listItemData = new ArrayList<AbsListItemData>();
        filterIndices = new ArrayList<Integer>();
        mHiddenItems = new TreeSet<>();
        mItemCount = 0;
	}

	public void setAdapter(TiCollectionViewAdapter a) {
		adapter = a;
	}
	
	public boolean hasHeader() {
        return !hideHeaderOrFooter() && getHoldedProxy(TiC.PROPERTY_HEADER_VIEW) != null;
	}
	public boolean hasFooter() {
        return !hideHeaderOrFooter() && getHoldedProxy(TiC.PROPERTY_FOOTER_VIEW) != null;
    }
	
	private static final ArrayList<String> KEY_SEQUENCE;
    static{
      ArrayList<String> tmp = new ArrayList<String>();
      tmp.add(TiC.PROPERTY_HEADER_TITLE);
      tmp.add(TiC.PROPERTY_FOOTER_TITLE);
      tmp.add(TiC.PROPERTY_HEADER_VIEW);
      tmp.add(TiC.PROPERTY_FOOTER_VIEW);
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
        case TiC.PROPERTY_HEADER_VIEW:
        case TiC.PROPERTY_FOOTER_VIEW:
            addProxyToHold(newValue, key);
            break;
        case TiC.PROPERTY_HEADER_TITLE:
            addProxyToHold(TiAbsListView.headerViewDict(TiConvert.toString(newValue)), TiC.PROPERTY_HEADER_VIEW);
            break;
       case TiC.PROPERTY_FOOTER_TITLE:
            addProxyToHold(TiAbsListView.footerViewDict(TiConvert.toString(newValue)), TiC.PROPERTY_FOOTER_VIEW);
            break;
       case TiC.PROPERTY_ITEMS:
           handleSetItems(newValue);
           break;
       case "hideWhenEmpty":
           hideWhenEmpty = TiConvert.toBoolean(newValue, hideWhenEmpty);
           if (changedProperty) {
               notifyDataChange();
           }
           break;
       case "showHeaderWhenHidden":
           showHeaderWhenHidden = TiConvert.toBoolean(newValue, showHeaderWhenHidden);
           if (changedProperty) {
               notifyDataChange();
           }
           break;
       case TiC.PROPERTY_VISIBLE:
            setVisible(TiConvert.toBoolean(newValue, true));
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

	public void notifyDataChange() {
		if (adapter == null) return;
//        updateCurrentItemCount();
		getMainHandler().post(new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
			}
		});
	}


	
	@Kroll.method
    public KrollProxy getBinding(final int itemIndex, final String bindId) {
        if (listView != null) {
            return listView.get().getChildByBindId(this.sectionIndex, itemIndex, bindId);
        }
        return null;
    }

	@Kroll.method
	public Object getItemAt(final int index) {
//		return getInUiThread(new Command<KrollDict>() {
//		    @Override
//            public KrollDict execute() {
		        return handleGetItemAt(index);
//            }
//        });
	}

	private Object handleGetItemAt(int index) {
		if (itemProperties != null && index >= 0
				&& index < itemProperties.length) {
			return itemProperties[index];
		}
		return null;
	}
	/**
	 * 
     * The external count provided by getCount is of an imaginary array of the hidden items.
     * We need to map indices of this array to indices in the real array of items. For example
     * if we have an array
     * [ "a", "b", "c", "d"]
     * and the second item, "b" is hidden, we return the count of this array as 3, to an imaginary
     * array of
     * [ "a", "c", "d"]
     * When the items at position 1 and 2 are requested however we need to map them to our real array
     * ie. 1 --> 2 and 2 --> 3 to give us "c" and "d"
     */
	private int getItemIndexFromPosition(final int position) {
		int realposition = position;
		 
        for(Integer i : mHiddenItems) {
            if(i <= realposition) {
                realposition++;
            }
            else {
                //mHiddenItems is Ordered so anything higher won't affect us
                break;
            }
        }
        return realposition;
	}
	
	private int getListViewRealPosition(int itemIndex) {
	    int realposition = itemIndex;
	    
        for(Integer i : mHiddenItems) {
            if(i <= itemIndex) {
                realposition--;
            }
            else {
                //mHiddenItems is Ordered so anything higher won't affect us
                break;
            }
        }
 
        return realposition;
	}


//	private int getHiddenCountUpTo(int location) {
//		int count = 0;
//		for(Integer i : mHiddenItems) {
//            if(i <= location) {
//                count++;
//            }
//            else {
//                //mHiddenItems is Ordered so anything higher won't affect us
//                break;
//            }
//        }
//		return count;
//	}

	@Kroll.method
	@Kroll.setProperty
	public void setItems(final Object data) {
//		runInUiThread(new CommandNoReturn() {
//            @Override
//            public void execute() {
                handleSetItems(data);
                notifyDataChange();
//            }
//        }, true);
	}

	@Kroll.method
	@Kroll.getProperty(enumerable=false)
	public Object[] getItems() {
		if (itemProperties == null) 
			return new Object[0];
//		} else if (TiApplication.isUIThread()) {
			return itemProperties;
//		} else {
//			return (Object[]) TiMessenger
//					.sendBlockingMainMessage(getMainHandler().obtainMessage(
//							MSG_GET_ITEMS));
//		}
	}

	@Kroll.method
	public void appendItems(final Object data) {
//		runInUiThread(new CommandNoReturn() {
//            @Override
//            public void execute() {
                insertItemsAt(mItemCount, data);
//            }
//        }, true);
	}

	public boolean isIndexValid(final int index) {
		return (index >= 0) ? true : false;
	}

	@Kroll.method
	public void insertItemsAt(final int index, final Object data) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                int itemCount = insertItemsData(index, data);
                if (itemCount > 0) {
                    notifyItemRangeInserted(index, itemCount);
                }
            }
        }, true);
	}
	
	protected void notifyItemRangeRemoved(int childPositionStart, int itemCount) {
	    notifyDataChange();
	}
	
	protected void notifyItemRangeChanged(int childPositionStart, int itemCount) {
        notifyDataChange();
    }
	protected void notifyItemRangeInserted(int childPositionStart, int itemCount) {
        notifyDataChange();
    }
    

	@Kroll.method
	public void deleteItemsAt(final int index, final int count) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                int deletedCount = deleteItemsData(index, count);
                notifyItemRangeRemoved(index, deletedCount);
            }
        }, true);
	}

	@Kroll.method
	public void replaceItemsAt(final int index, final int count, final Object data) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            
            @Override
            public void execute() {
                int deletedCount = 0;
                if (count > 0) {
                    deletedCount = deleteItemsData(index, count);
                }
                if (deletedCount > 0) {
                    notifyItemRangeRemoved(index, deletedCount);
                }
                notifyItemRangeInserted(index, insertItemsData(index, data));
            }
        }, true);
	}
	
	
	@Kroll.method
    public void updateItems(final Object data,
            @Kroll.argument(optional = true) final Object options) {
        if (!(data instanceof Object[])) {
            return;
        }
        if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    updateItems(data, options);
                }
            }, true);
            return;
        }
        Object[] updates = (Object[]) data;
        if (itemProperties == null || updates.length != itemProperties.length) {
            return;
        }
        TiCollectionViewInterface listView = getListView();
        int i;
        Object item;
        Object update;
        View content;
        boolean visible;
        for (i = 0; i < itemProperties.length; i++) {
            item = itemProperties[i];
            update = updates[i];
            if (item instanceof HashMap && update instanceof HashMap) {
                KrollDict.merge((HashMap) item, (HashMap) update, false);
            } else  {
                itemProperties[i] = updates[i];
            }
            
            content = listView.getCellAt(this.sectionIndex, i);
            visible = updateVisibleState(item, i);

            if (content != null && visible) {
                TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content
                        .findViewById(TiAbsListView.listContentId);
                if (listItem != null) {
                    if (listItem.getItemIndex() == i) {
                        TiAbsListViewTemplate template = getListView()
                                .getTemplate(TiConvert.toString(item,
                                        TiC.PROPERTY_TEMPLATE), true);
                        populateViews(item, listItem, template,
                                getUserItemInversedIndexFromSectionPosition(i),
                                this.sectionIndex, content, false);
                    }
                }
            }
        }
//        notifyItemRangeChanged(0, updates.length);
    }
	
	private boolean updateVisibleState(final Object item, final int index) {
	    final boolean visible = isItemVisible(item);
        if (visible) {
            mHiddenItems.remove(index);
        } else {
            mHiddenItems.add(index);
        }
        return visible;
	}

	
	@Kroll.method
    public void updateItemAt(final int index, final Object data,
            @Kroll.argument(optional = true) final Object options) {
        if (!isIndexValid(index) || !(data instanceof HashMap)) {
            return;
        }
        
        if (itemProperties == null) {
            return;
        }
        // int nonRealItemIndex = itemIndex;
        if (index < 0 || index > itemProperties.length - 1) {
            return;
        }
        // if (hasHeader()) {
        // nonRealItemIndex += 1;
        // }
        

        final TiCollectionViewInterface listView = getListView();
        final int sectionIndex = this.sectionIndex;
        Object currentItem = itemProperties[index];
        final boolean wasVisible = isItemVisible(currentItem);
        if (data instanceof HashMap && currentItem instanceof HashMap) {
            currentItem = KrollDict.merge((HashMap) currentItem,
                    (HashMap) (data));
        } else {
            currentItem = data;
        }
        if (currentItem == null)
            return;
        itemProperties[index] = currentItem;
        final Object newItem = currentItem;
        
        final boolean visible = updateVisibleState(newItem, index);
        
        if (!wasVisible && visible) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    notifyItemRangeInserted(index, 1);
                }
            }, false);
            return;
        }
        
        // only process items when listview's properties is processed.
        if (listView == null) {
            preload = true;
            return;
        }
        runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                View content = listView.getCellAt(sectionIndex, index);
                if (content != null && visible) {
                    TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content
                            .findViewById(TiAbsListView.listContentId);
                    if (listItem != null) {
                        if (listItem.getItemIndex() == index) {
                            TiAbsListViewTemplate template = getListView()
                                    .getTemplate(TiConvert.toString(newItem,
                                            TiC.PROPERTY_TEMPLATE), true);
                            populateViews(newItem, listItem, template,
                                    getUserItemInversedIndexFromSectionPosition(index),
                                    sectionIndex, content, false);
                        } else {
                            Log.d(TAG, "wrong item index", Log.DEBUG_MODE);
                        }
                        return;
                    }
                } else {
                    notifyItemRangeChanged(index, 1);
                }
            }
        }, false);
//        notifyItemRangeChanged(index, 1);
    }
	
	
	
	public void updateItemAt(final int index, final String binding, final String key, final Object value) {
	    if (index < 0 || index >= mItemCount) {
	        return;
	    }
	    if (itemProperties != null) {
	        Object itemProp = itemProperties[index];
	        if (itemProp instanceof HashMap) {
	            if (!((HashMap) itemProp).containsKey(binding)) {
	                ((HashMap) itemProp).put(binding, new HashMap());
	            }
	            ((HashMap)((HashMap) itemProp).get(binding)).put(key, value);
	        }
	        
        }
//	    AbsListItemData itemD = listItemData.get(index);
//	    itemD.setProperty(binding, key, value);
    }
	
	public void updateItemAt(final int index, final String binding, final HashMap props) {
        if (index < 0 || index >= mItemCount) {
            return;
        }
        if (itemProperties != null) {
            Object itemProp = itemProperties[index];
            if (itemProp instanceof HashMap) {
                if (!((HashMap) itemProp).containsKey(binding)) {
                    ((HashMap) itemProp).put(binding, new HashMap<String, Object>());
                }
                ((HashMap)((HashMap) itemProp).get(binding)).putAll(props);
            }
        }
//      AbsListItemData itemD = listItemData.get(index);
//      itemD.setProperty(binding, key, value);
    }
	
	@Kroll.method
	public void hide() {
        setVisible(false);
	}
	
	@Kroll.method
	public void show() {
		setVisible(true);
	}
	
	@Kroll.method
	@Kroll.setProperty
	public void setVisible(boolean value) {
		if (hidden == !value) return;
        hidden = !value;
		notifyDataChange();
	}
	
	@Kroll.method
	@Kroll.getProperty
	public boolean getVisible() {
		return !hidden;
	}

    @Kroll.method
    @Kroll.getProperty(enumerable=false)
    public int getLength() {
        return getItemCount();
    }

	
	public void processPreloadData() {
		if (itemProperties != null && preload) {
            mItemCount = itemProperties.length;
            processData(itemProperties, 0);
			preload = false;
		}
	}
	
	private boolean isItemVisible(final Object item) {
        boolean visible = false;
        if (item != null) {
            visible = true;
            if (item instanceof HashMap) {
            Object props = ((HashMap) item).get(TiC.PROPERTY_PROPERTIES);
                if (props instanceof HashMap) {
                    HashMap<String, Object> propsHash = (HashMap<String, Object>) props;
                    visible = TiConvert.toBoolean(propsHash,
                                TiC.PROPERTY_VISIBLE, visible);
                }
            }
        }
        return visible;
	}

	private void processData(Object items, int offset) {
//		if (listItemData == null) {
//			return;
//		}
//        boolean visible = true;
        int i;
		if (items instanceof Object[]) {
		    Object[] array = (Object[])items;
	        for (i = 0; i < array.length; i++) {
	            updateVisibleState(array[i], i + offset);          
//	            hiddenItems.add(i + offset, !isItemVisible((HashMap) array[i]));
	        }
		} else if (items instanceof ArrayList) {
		    ArrayList<Object> array = (ArrayList<Object>)items;
		    for (i = 0; i < array.size(); i++) {
                updateVisibleState(array.get(i), i + offset);          
//		        hiddenItems.add(i + offset, !isItemVisible((HashMap) array.get(i)));
	        }
		}
		
		updateCurrentItemCount();
		// Notify adapter that data has changed.
//		if (preload == false) {
//	      runInUiThread(new CommandNoReturn() {
//              @Override
//              public void execute() {
//                adapter.notifyDataSetChanged();
//              }
//          }, false);
//		}
	}

	private void handleSetItems(Object data) {

		if (data instanceof Object[]) {
//		    HashMap[] items = (HashMap[]) data;
			itemProperties = (Object[]) data;
//			listItemData.clear();
			mHiddenItems.clear();
			filterIndices.clear();
			// only process items when listview's properties is processed.
//			if (getListView() == null) {
//				preload = true;
//				return;
//			}
			mItemCount = itemProperties.length;
			processData(itemProperties, 0);

		} else {
			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
		}
	}

//	private int handleAppendItems(Object data) {
//		if (data instanceof Object[]) {
//		    Object[] items = (Object[]) data;
//			if (itemProperties == null) {
//				itemProperties = items;
//			} else {
//	            ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
//			    list.addAll(Arrays.asList(items));
//			    itemProperties = list.toArray();
//			}
//			// only process items when listview's properties is processed.
//			if (getListView() == null) {
//				preload = true;
//				return 0;
//			}
//			int offset = mItemCount;
//			mItemCount = itemProperties.length;
//
//			processData(items, offset);
//			return items.length;
//		} else {
//			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
//		}
//		return 0;
//	}

	
	public int deleteItemsData(int index, int count) {
		int removedCount = 0;
		
        ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
		while (count > 0) {
			if (index < itemProperties.length) {
			    list.remove(index);
				removedCount ++;
			}
//			if (index < listItemData.size()) {
//				listItemData.remove(index);
//			}
//			if (index < hiddenItems.size()) {
			mHiddenItems.remove(index);
//			}
			count--;
		}
        itemProperties = list.toArray();
        mItemCount = itemProperties.length;
		updateCurrentItemCount();
		return removedCount;
	}
	
    public int insertItemsData(int index, Object data) {
        if (data instanceof Object[]) {
            Object[] items = (Object[]) data;
            if (items.length == 0) {
                return 0;
            }
            if (itemProperties == null) {
                itemProperties = items;
            } else {
                if (index < 0 || index > itemProperties.length) {
                    Log.e(TAG, "Invalid index to handleInsertItem",
                            Log.DEBUG_MODE);
                    return 0;
                }
                ArrayList<Object> list = new ArrayList(
                        Arrays.asList(itemProperties));
                list.addAll(index, Arrays.asList(items));
                itemProperties = (Object[]) list.toArray();

            }
            mItemCount = itemProperties.length;
            processData(items, index);
            return items.length;
        } else if (data != null) {
            insertItemsData(index, new Object[] { data });
        }
        return 0;
    }


//	private void handleUpdateItemAt(int index, Object data) {
//		handleReplaceItemsAt(index, 1, data);
//		setProperty(TiC.PROPERTY_ITEMS, itemProperties.toArray());
//	}

	/**
	 * This method creates a new cell and fill it with content. getView() calls
	 * this method when a view needs to be created.
	 * 
	 * @param sectionIndex
	 *            Entry's index relative to its section
	 * @return
	 */
	public void generateCellContent(int sectionIndex, final Object item, 
			AbsListItemProxy itemProxy, TiBaseAbsListViewItem itemContent, TiAbsListViewTemplate template,
			int itemPosition, View item_layout) {
		// Create corresponding TiUIView for item proxy
		TiAbsListItem listItem = new TiAbsListItem(itemProxy, itemContent, item_layout);
        itemProxy.setActivity(this.getActivity());
		itemProxy.setView(listItem);
		itemContent.setView(listItem);
		itemProxy.realizeViews();

		if (template != null) {
			populateViews(item, itemContent, template, itemPosition,
					sectionIndex, item_layout, false);
		}		
	}
	
	public int getUserItemIndexFromSectionPosition(final int position) {
	    int result = position;
//	    if (hasHeader()) {
//	        result -= 1;
//        }
	    if (isFilterOn() && result >= 0&& result < filterIndices.size()) {
	        return getItemIndexFromPosition(filterIndices.get(result));
	    }
	    return getItemIndexFromPosition(result);
	}
	public int getUserItemInversedIndexFromSectionPosition(final int position) {
        int result = position;
//      if (hasHeader()) {
//          result -= 1;
//        }
        if (isFilterOn()) {
            return getListViewRealPosition(filterIndices.get(result));
        }
        return getListViewRealPosition(result);
    }

	public void populateViews(final Object item, TiBaseAbsListViewItem cellContent, TiAbsListViewTemplate template, int itemIndex, int sectionIndex,
			View item_layout, boolean reusing) {
		TiAbsListItem listItem = (TiAbsListItem)cellContent.getView();
		// Handling root item, since that is not in the views map.
		if (listItem == null) {
			return;
		}
		listItem.setReusing(reusing);
		int realItemIndex = getUserItemIndexFromSectionPosition(itemIndex);
		cellContent.setCurrentItem(sectionIndex, realItemIndex, this);
		
//		HashMap data = item;
		HashMap data = template.prepareDataDict(item);
		AbsListItemProxy itemProxy = (AbsListItemProxy) cellContent.getView().getProxy();
		itemProxy.setCurrentItem(sectionIndex, realItemIndex, this, item);

		HashMap listItemProperties = null;
//		String itemId = null;

		if (data != null && data.get(TiC.PROPERTY_PROPERTIES) != null) {
			listItemProperties = (HashMap) data.get(TiC.PROPERTY_PROPERTIES);
		}
		if (listItemProperties == null) { 
		    listItemProperties = new HashMap(); 
		}
		ProxyAbsListItem rootItem = itemProxy.getListItem();
		
		if (itemIndex >= 0) { //important in collectionView to ignore headers
	        HashMap<String, Object> listViewProperties = getListView().getToPassProps();
		    for (Map.Entry<String, Object> entry : listViewProperties.entrySet()) {
	            String inProp = entry.getKey();
	            if (!(listItemProperties.containsKey(inProp)) && !rootItem.containsKey(inProp)) {
	                listItemProperties.put(inProp, entry.getValue());
	            }
	        }
		}
		

//		// find out if we need to update itemId
//		if (listItemProperties.containsKey(TiC.PROPERTY_ITEM_ID)) {
//			itemId = TiConvert.toString(listItemProperties
//					.get(TiC.PROPERTY_ITEM_ID));
//		}

		// update extra event data for list item
		itemProxy.setEventOverrideDelegate(itemProxy);

		HashMap<String, ProxyAbsListItem> views = itemProxy.getBindings();
		// Loop through all our views and apply default properties
		for (String binding : views.keySet()) {
			ProxyAbsListItem viewItem = views.get(binding);
			KrollProxy proxy  = viewItem.getProxy();
			if (proxy instanceof TiViewProxy) {
			    ((TiViewProxy) proxy).getOrCreateView();
			}
			KrollProxyListener modelListener = (KrollProxyListener) proxy.getModelListener();
			if (!(modelListener instanceof KrollProxyReusableListener)) {
                continue;
			}
			if (modelListener instanceof TiUIView) {
	            ((TiUIView)modelListener).setTouchDelegate((TiTouchDelegate) listItem);
            }
			// update extra event data for views
			proxy.setEventOverrideDelegate(itemProxy);
            proxy.setSetPropertyListener(itemProxy);
			// if binding is contain in data given to us, process that data,
			// otherwise
			// apply default properties.
			if (reusing) {
			    ((KrollProxyReusableListener) modelListener).setReusing(true);
			}
			if (data instanceof HashMap) {
			    HashMap diffProperties = viewItem
	                    .generateDiffProperties((HashMap) data.get(binding));
	            
	            if (diffProperties != null && !diffProperties.isEmpty()) {
	                if (reusing) {
	                    modelListener.processApplyProperties(diffProperties);
	                } else {
	                    modelListener.processProperties(diffProperties);
	                }
	            }
			}
			
			if (reusing) {
			    ((KrollProxyReusableListener) modelListener).setReusing(false);
			}
		}
		
		for (KrollProxy theProxy : itemProxy.getNonBindedProxies()) {
		    KrollProxyListener modelListener = (KrollProxyListener) theProxy.getModelListener();
		    if (modelListener instanceof KrollProxyReusableListener) {
		        if (reusing) {
	                ((KrollProxyReusableListener) modelListener).setReusing(true);
	            }
		        if (modelListener instanceof TiUIView) {
	                ((TiUIView)modelListener).setTouchDelegate((TiTouchDelegate) listItem);
	            }
		        theProxy.setEventOverrideDelegate(itemProxy);
		        if (reusing) {
	                ((KrollProxyReusableListener) modelListener).setReusing(false);
	            }
            }
		}

	    listItemProperties = itemProxy.getListItem()
                .generateDiffProperties(listItemProperties);

		if (!listItemProperties.isEmpty()) {
		    if (reusing) {
		        listItem.processApplyProperties(listItemProperties);
            } else {
                listItem.processProperties(listItemProperties);
            }
		}
        listItem.setReusing(false);
	}

	public String getTemplateByIndex(int index) {
//        if (hasHeader()) {
//			index -= 1;
//		}
	    Object item = null;
		if (isFilterOn()) {
		    item = getItemDataAt(filterIndices.get(index));
		} else {
		    item = getItemDataAt(index);
		}
		if (item instanceof HashMap) {
            return TiConvert.toString((HashMap) item, TiC.PROPERTY_TEMPLATE);
        }
        return null;
	}

	public int getContentCount() {
		int totalCount = 0;
		if (hidden) return totalCount;
		if (isFilterOn()) {
			totalCount = filterIndices.size();
		} else {
			totalCount = mItemCount;
		}
		return totalCount - getHiddenCount();
	}
	
    protected void updateCurrentItemCount() {
        if (hidden && !showHeaderWhenHidden) {
            mCurrentItemCount = 0;
            return;
        }
        int totalCount = 0;
        if (isFilterOn()) {
            totalCount = filterIndices.size();
        } else {
            totalCount = mItemCount;
        }
        // else if (!hideHeaderOrFooter() && hasHeader()) {
        // totalCount += 1;
        // }
        //
        totalCount -= getHiddenCount();

        if (!hideHeaderOrFooter() && (totalCount > 0 || !hideWhenEmpty)) {
            if (hasHeader() && totalCount == 0) {
                totalCount += 1;
            }
            // footer must be counted in!
            if (hasFooter()) {
                totalCount += 1;
            }
        }
        mCurrentItemCount = totalCount;
    }
	/**
	 * @return number of entries within section
	 */
    public int getItemCount() {
		return mCurrentItemCount;
	}

	protected int getHiddenCount() {
		int count = 0;
		if (hidden || mHiddenItems == null) return count;
//		for (int i = 0; i < hiddenItems.size(); i++)
//			if (hiddenItems.get(i) == true) {
//                count++;
//			}
		return mHiddenItems.size();
	}

	protected boolean hideHeaderOrFooter() {
		return (isFilterOn() && filterIndices.isEmpty());
	}

	public boolean isHeaderView(int pos) {
		return (hasHeader() && pos == 0);
	}

	protected boolean isFooterView(int pos) {
		return (hasFooter() && pos == mCurrentItemCount - 1);
	}

	public void setListView(TiCollectionViewInterface l) {
		listView = new WeakReference<TiCollectionViewInterface>(l);
        updateCurrentItemCount(); //needs to be updated if no item but with a header or footer
	}

	public TiCollectionViewInterface getListView() {
		if (listView != null) {
			return listView.get();
		}
		return null;
	}

	public Object getItemDataAt(int position)
	{
	    if (itemProperties != null && itemProperties.length > 0) {
	        final int index = getItemIndexFromPosition(position);
	        if (index < itemProperties.length) {
	            return itemProperties[index];
	        }
	    }
	    return null;
	}

//	public KrollDict getListItemData(int position) {
//		if (headerTitle != null || headerView != null) {
//			position -= 1;
//		}
//
//		if (isFilterOn()) {
//			return getItemDataAt(filterIndices.get(position))
//					.getProperties();
//		} else if (position >= 0 && position < getItemCount()) {
//			return getItemDataAt(position).getProperties();
//		}
//		return null;
//	}

	public Object getListItem(int position) {
//        if (hasHeader()) {
//			position -= 1;
//		}
	    if (hasFooter() && position == getItemCount()) {
            return null;
        }
		if (isFilterOn()) {
			return getItemDataAt(filterIndices.get(position));
		} else if (position >= 0 && position < getItemCount()) {
			return getItemDataAt(position);
		}
		return null;
	}

	public boolean isFilterOn() {
	    if (listView == null) {
	        return false;
	    }
	    String searchText = getListView().getSearchText();
	    return (searchText != null && searchText.length() > 0);
	}

	public void applyFilter(String searchText, final boolean caseInsensitive, final boolean ignoreExactMatch) {
		// Clear previous result
		filterIndices.clear();
		hidden = TiConvert.toBoolean(TiC.PROPERTY_VISIBLE, false);
		if (isFilterOn() && itemProperties != null) {
		    
		    if (caseInsensitive) {
                searchText = searchText.toLowerCase();
            }
	        // Add new results
	        for (int i = 0; i < itemProperties.length; ++i) {
	            Object data = itemProperties[i];
	            boolean visible = isItemVisible(data);
	            if (!visible) {
	                continue;
	            }
	            String searchableText = null;
	            if (data instanceof HashMap) {
	                searchableText = TiConvert.toString((HashMap) data, TiC.PROPERTY_SEARCHABLE_TEXT);
	                if (searchableText == null && ((HashMap) data).get(TiC.PROPERTY_PROPERTIES) != null) {
	                    searchableText = TiConvert.toString(((HashMap) data).get(TiC.PROPERTY_PROPERTIES), TiC.PROPERTY_TITLE);
	                }
	            } else {
	                searchableText = TiConvert.toString(data);
	            }
	             
	            if (searchableText == null) {
	                continue;
	            }
	            // Handle case sensitivity
	            if (caseInsensitive) {
	                searchableText = searchableText.toLowerCase();
	            }
	            // String comparison
	            if (searchableText.contains(searchText) && (!ignoreExactMatch || !searchableText.equals(searchText))) {
	                filterIndices.add(getListViewRealPosition(i));
	            }
	        }
	        hidden = hidden || filterIndices.size() == 0;
		}
        updateCurrentItemCount();
	}

	public void release() {
//		if (listItemData != null) {
//			listItemData.clear();
////			listItemData = null;
//		}
		
		if (mHiddenItems != null) {
		    mHiddenItems.clear();
//			hiddenItems = null;
		}
		
		if (filterIndices != null) {
		    filterIndices.clear();
//          hiddenItems = null;
        }

		if (itemProperties != null) {
//			itemProperties.clear();
			itemProperties = null;
		}
		mCurrentItemCount = 0;
		mItemCount = 0;
		super.release();
	}

    public void setIndex(int index) {
        this.sectionIndex = index;
        
    }
    
    public int getIndex() {
        return this.sectionIndex;
    }

}
