/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.abslistview;

import java.util.ArrayList;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.listview.TiListView;
import android.app.Activity;

@Kroll.proxy(propertyAccessors = {
	TiC.PROPERTY_HEADER_TITLE,
	TiC.PROPERTY_FOOTER_TITLE,
//    TiC.PROPERTY_SECTIONS,
	TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE,
	TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR,
	TiC.PROPERTY_SEPARATOR_COLOR,
	TiC.PROPERTY_SEARCH_TEXT,
    TiC.PROPERTY_SEARCH_VIEW,
    TiC.PROPERTY_HEADER_VIEW,
    TiC.PROPERTY_FOOTER_VIEW,
    TiC.PROPERTY_SEARCH_VIEW_EXTERNAL,
	TiC.PROPERTY_CASE_INSENSITIVE_SEARCH,
	TiC.PROPERTY_HEADER_DIVIDERS_ENABLED,
	TiC.PROPERTY_REFRESH_CONTROL,
	TiC.PROPERTY_FOOTER_DIVIDERS_ENABLED,
	TiC.PROPERTY_SCROLL_HIDES_KEYBOARD
}, propertyDontEnumAccessors = {
    TiC.PROPERTY_TEMPLATES
})
public abstract class AbsListViewProxy extends TiViewProxy {

	private static final String TAG = "ListViewProxy";
	
//	private static final int MSG_FIRST_ID = TiViewProxy.MSG_LAST_ID + 1;

//	private static final int MSG_SECTION_COUNT = MSG_FIRST_ID + 399;
//	private static final int MSG_SCROLL_TO_ITEM = MSG_FIRST_ID + 400;
//	private static final int MSG_APPEND_SECTION = MSG_FIRST_ID + 401;
//	private static final int MSG_INSERT_SECTION_AT = MSG_FIRST_ID + 402;
//	private static final int MSG_DELETE_SECTION_AT = MSG_FIRST_ID + 403;
//	private static final int MSG_REPLACE_SECTION_AT = MSG_FIRST_ID + 404;
//	private static final int MSG_SCROLL_TO_TOP = MSG_FIRST_ID + 405;
//	private static final int MSG_SCROLL_TO_BOTTOM = MSG_FIRST_ID + 406;
//	private static final int MSG_GET_SECTIONS = MSG_FIRST_ID + 407;
//    private static final int MSG_CLOSE_PULL_VIEW = MSG_FIRST_ID + 408;
//    private static final int MSG_SHOW_PULL_VIEW = MSG_FIRST_ID + 409;
//	private static final int MSG_SET_SECTIONS = MSG_FIRST_ID + 408;

//    protected static final int MSG_LAST_ID = MSG_SHOW_PULL_VIEW;


	//indicate if user attempts to add/modify/delete sections before TiListView is created 
	private boolean preload = false;
	private ArrayList<AbsListSectionProxy> preloadSections;
	private HashMap preloadMarker;
	
	public AbsListViewProxy() {
		super();
	}
	

	
    @Override
    public void setActivity(Activity activity) {
        super.setActivity(activity);
        TiUIView listView = peekView();

        if (listView != null) {
            AbsListSectionProxy[] sections = ((TiCollectionViewInterface) listView).getSections();
            for (AbsListSectionProxy section : sections) {
                if (section != null) {
                    section.setActivity(activity);
                }
            }
        }
    }
    
//    @Override
//    public void releaseViews(boolean activityFinishing) {
//        TiUIView listView = peekView();
//        super.releaseViews(activityFinishing);
//    }

	@Override
	public void handleCreationDict(HashMap options, KrollProxy rootProxy) {
	    defaultValues.put(TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE, UIModule.LIST_ITEM_TEMPLATE_DEFAULT);
        defaultValues.put(TiC.PROPERTY_CASE_INSENSITIVE_SEARCH, true);
        defaultValues.put(TiC.PROPERTY_ROW_HEIGHT, 50);
//        defaultValues.put(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR, "#474747");
		super.handleCreationDict(options, rootProxy);
		//Adding sections to preload sections, so we can handle appendSections/insertSection
		//accordingly if user call these before TiListView is instantiated.
		if (options.containsKey(TiC.PROPERTY_SECTIONS)) {
			Object obj = options.get(TiC.PROPERTY_SECTIONS);
			if (obj instanceof Object[]) {
				addPreloadSections((Object[]) obj, -1, true);
			}
		}
	}
	
	public void clearPreloadSections() {
	    //dont clear the preloaded because if we are 
		if (preloadSections != null) {
			preloadSections.clear();
		}
		preload = false;
	}
	
	public ArrayList<AbsListSectionProxy> getPreloadSections() {
		return preloadSections;
	}
	
	public boolean getPreload() {
		return preload;
	}
	
	public void setPreload(boolean pload)
	{
		preload = pload;
	}
	
	public HashMap getPreloadMarker()
	{
		return preloadMarker;
	}

	private void addPreloadSections(Object secs, int index, boolean arrayOnly) {
		if (secs instanceof Object[]) {
			Object[] sections = (Object[]) secs;
			for (int i = 0; i < sections.length; i++) {
				Object section = sections[i];
				addPreloadSection(section, -1);
			}
		} else if (!arrayOnly) {
			addPreloadSection(secs, -1);
		}
	}
	
	public Class sectionClass() {
	    return AbsListSectionProxy.class;
	}
	
	private void addPreloadSection(Object section, int index) {
	    if(section instanceof HashMap) {
            section =  KrollProxy.createProxy(sectionClass(), null, new Object[]{section}, null);
            ((KrollProxy) section).updateKrollObjectProperties();
        }
		if (section instanceof AbsListSectionProxy) {
		    if (preloadSections == null) {
		        preloadSections = new ArrayList<AbsListSectionProxy>();
	            preload = true;
		    }
			if (index == -1) {
				preloadSections.add((AbsListSectionProxy) section);
			} else {
				preloadSections.add(index, (AbsListSectionProxy) section);
			}
		}
	}
	
	@Kroll.method @Kroll.getProperty(enumerable=false)
	public int getSectionCount() {
        TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
			return ((TiCollectionViewInterface)listView).getSectionCount();
		} else {
		    if (preloadSections != null) {
	            return preloadSections.size();
            }
            return 0;

		}
	}

	@Kroll.method
	public int getSectionItemsCount(int sectionIndex) {
		AbsListSectionProxy section = getSectionAt(sectionIndex);
		if (section != null) {
			return section.getLength();
		} else {
			return 0;
		}
	}
	
	@Kroll.method
    public AbsListSectionProxy getSectionAt(int sectionIndex) {
        if (preload) {
            if (sectionIndex < 0 || sectionIndex >= preloadSections.size()) {
                Log.e(TAG, "getItem Invalid section index");
                return null;
            }

            return preloadSections.get(sectionIndex);
        }
        TiUIView listView = getOrCreateView();
        if (listView instanceof TiCollectionViewInterface) {
            return ((TiCollectionViewInterface) listView)
                    .getSectionAt(sectionIndex);
        }
        return null;
    }

	@Kroll.method
	public void scrollToItem(final int sectionIndex, final int itemIndex, @Kroll.argument(optional = true) KrollDict options) {
	    final boolean animated = TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, true);
		final TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    ((TiCollectionViewInterface) listView).scrollToItem(sectionIndex, itemIndex, animated);
                }
            }, false);
        }
	}
	
    @Kroll.method
    public void selectItem(int sectionIndex, int itemIndex, @Kroll.argument(optional = true) KrollDict options) {
        //on android no selection so same as scrollToItem
        scrollToItem(sectionIndex, itemIndex, options);
    }
    @Kroll.method
    public void deselectAll() {
        //on android no selection
    }
    
	
	@Kroll.method
	public KrollProxy getChildByBindId(int sectionIndex, int itemIndex, String bindId) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            ((TiCollectionViewInterface) listView).getChildByBindId(sectionIndex, itemIndex, bindId);
		}
		return null;
	}
	
	@Kroll.method
	public Object getItemAt(int sectionIndex, int itemIndex) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            return ((TiCollectionViewInterface) listView).getItem(sectionIndex, itemIndex);
		} else {
			if (sectionIndex < 0 || sectionIndex >= preloadSections.size()) {
				Log.e(TAG, "getItem Invalid section index");
				return null;
			}
			
			return preloadSections.get(sectionIndex).getItemAt(itemIndex);
		}
	}
	
	
    @Kroll.method
    @Kroll.setProperty
	public void setMarker(Object marker) {
		if (marker instanceof HashMap) {
			HashMap m = (HashMap) marker;
			TiUIView listView = peekView();
	        if (listView instanceof TiCollectionViewInterface) {
	            ((TiCollectionViewInterface) listView).setMarker(m);
			} else {
				preloadMarker = m;
			}
		}
	}
	
	@Kroll.method
	public void scrollToTop(final int y, @Kroll.argument(optional = true) KrollDict options)
	{
	    final boolean animated = TiConvert.toBoolean(options, TiC.PROPERTY_ANIMATED, true);
	    final TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    ((TiCollectionViewInterface) listView).scrollToTop(y, animated);
                }
            }, false);
        }
	}

	@Kroll.method
	public void scrollToBottom(final int y, @Kroll.argument(optional = true) KrollDict options)
 {
        final boolean animated = TiConvert.toBoolean(options,
                TiC.PROPERTY_ANIMATED, true);
        final TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    ((TiCollectionViewInterface) listView).scrollToBottom(y,
                            animated);
                }
            }, false);
        }
    }

	
	
	@Kroll.method
	public void appendSection(Object section) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            ((TiCollectionViewInterface) listView).appendSection(section);
        } else {
            addPreloadSections(section, -1, false);
        }
	}

	
	@Kroll.method
	public void deleteSectionAt(int index) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            ((TiCollectionViewInterface) listView).deleteSectionAt(index);
        } else {
            if (index < 0 || index >= preloadSections.size()) {
                Log.e(TAG, "Invalid index to delete section");
                return;
            }
            preloadSections.remove(index);
            if (preloadSections.size() == 0) {
                clearPreloadSections();
            }
        }
	}
	
	@Kroll.method
	public void insertSectionAt(int index, Object section) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            ((TiCollectionViewInterface) listView).insertSectionAt(index, section);
        } else {
            if (index < 0 || index > preloadSections.size()) {
                Log.e(TAG, "Invalid index to insertSection");
                return;
            }
            addPreloadSections(section, index, false);
        }
	}
	
	@Kroll.method
	public void replaceSectionAt(int index, Object section) {
	    TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            ((TiCollectionViewInterface) listView).replaceSectionAt(index, section);
        } else {
            deleteSectionAt(index);
            insertSectionAt(index,  section);
            
        }
	}
	
	@Kroll.method @Kroll.getProperty(enumerable=false)
	public AbsListSectionProxy[] getSections()
	{
	    if (preload) {
	        ArrayList<AbsListSectionProxy> preloadedSections = getPreloadSections();
	        if (preloadedSections != null) {
	            return preloadedSections.toArray(new AbsListSectionProxy[preloadedSections.size()]);
	        }
            return null;
	    }
		TiUIView listView = getOrCreateView();
        if (listView instanceof TiCollectionViewInterface) {
            return ((TiCollectionViewInterface) listView).getSections();
        }
        return null;
	}
	
	@Kroll.setProperty @Kroll.method
	public void setSections(Object sections)
	{
		if (!(sections instanceof Object[])) {
			Log.e(TAG, "Invalid argument type to setSection(), needs to be an array", Log.DEBUG_MODE);
			return;
		}
		Object[] sectionsArray = (Object[]) sections;
		TiUIView listView = peekView();
		//Preload sections if listView is not opened.
		if (listView == null) {
			clearPreloadSections();
			addPreloadSections(sectionsArray, -1, true);
		}
		else {
			((TiCollectionViewInterface)listView).processSectionsAndNotify(sectionsArray);
		}
		setProperty(TiC.PROPERTY_SECTIONS, sections);
	}
	
	@Kroll.method
	public void appendItems(int sectionIndex, Object data, @Kroll.argument(optional = true) Object options) {
		AbsListSectionProxy section = getSectionAt(sectionIndex);
		if (section != null){
			section.appendItems(data, options);
		}
		else {
			Log.e(TAG, "appendItems wrong section index");
		}
	}
	
	@Kroll.method
	public void insertItemsAt(int sectionIndex, int index, Object data, @Kroll.argument(optional = true) Object options) {
		AbsListSectionProxy section = getSectionAt(sectionIndex);
		if (section != null){
			section.insertItemsAt(index, data, options);
		}
		else {
			Log.e(TAG, "insertItemsAt wrong section index");
		}
	}

	@Kroll.method
	public void deleteItemsAt(int sectionIndex, int index, int count, @Kroll.argument(optional = true) Object options) {
		AbsListSectionProxy section = getSectionAt(sectionIndex);
		if (section != null){
			section.deleteItemsAt(index, count, options);
		}
		else {
			Log.e(TAG, "deleteItemsAt wrong section index");
		}
	}

	@Kroll.method
	public void replaceItemsAt(int sectionIndex, int index, int count, Object data, @Kroll.argument(optional = true) Object options) {
		AbsListSectionProxy section = getSectionAt(sectionIndex);
		if (section != null){
			section.replaceItemsAt(index, count, data, options);
		}
		else {
			Log.e(TAG, "replaceItemsAt wrong section index");
		}
	}

	@Kroll.method
	public void updateItemAt(int sectionIndex, int index, Object data, @Kroll.argument(optional = true) Object options) {
        AbsListSectionProxy section = getSectionAt(sectionIndex);
        if (section != null){
            section.updateItemAt(index, data, options);
        }
        else {
            Log.e(TAG, "updateItemAt wrong section index");
        }
	}
	

    @Kroll.method()
    public void showPullView(@Kroll.argument(optional = true) Object obj) {
        Boolean animated = true;
        if (obj != null) {
            animated = TiConvert.toBoolean(obj);
        }
        TiUIView listView = peekView();
        if (listView instanceof TiAbsListView) {
            ((TiAbsListView) listView).showPullView(animated);
        }
    }

    @Kroll.method()
    public void closePullView(@Kroll.argument(optional = true) Object obj) {
        Boolean animated = true;
        if (obj != null) {
            animated = TiConvert.toBoolean(obj);
        }
        TiUIView listView = peekView();
        if (listView instanceof TiAbsListView) {
            ((TiAbsListView) listView).closePullView(animated);
        }
    }
    
    @Kroll.method
    public void setContentOffset(final Object offset,
            @Kroll.argument(optional = true) HashMap options) {
        
        final boolean animated = TiConvert.toBoolean(options,
                TiC.PROPERTY_ANIMATED, true);
        final TiUIView listView = peekView();
        if (listView instanceof TiCollectionViewInterface) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    ((TiAbsListView) listView).setContentOffset(offset, animated);

                }
            }, false);
        }
    }

    @Kroll.setProperty
    public void setContentOffset(Object offset) {
        setContentOffset(offset, null);
    }
    
    @Kroll.getProperty
    @Kroll.method
    public Object getContentOffset() {
        TiUIView listView = peekView();
        if (peekView() != null) {
            return ((TiAbsListView) listView).getContentOffset();
        }
        return getProperty(TiC.PROPERTY_CONTENT_OFFSET);
    }
}
