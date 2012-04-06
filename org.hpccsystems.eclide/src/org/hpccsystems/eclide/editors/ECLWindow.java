/*******************************************************************************
 * Copyright (c) 2011 HPCC Systems.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     HPCC Systems - initial API and implementation
 ******************************************************************************/
package org.hpccsystems.eclide.editors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.hpccsystems.eclide.Activator;
import org.hpccsystems.eclide.builder.meta.ECLDefinition;
import org.hpccsystems.eclide.ui.viewer.ECLContentOutlinePage;
import org.hpccsystems.eclide.ui.viewer.platform.PlatformActions;
import org.hpccsystems.eclide.ui.viewer.platform.TreeItemOwner;
import org.hpccsystems.eclide.ui.viewer.platform.WorkunitTabItem;
import org.hpccsystems.eclide.ui.viewer.platform.WorkunitView;
import org.hpccsystems.eclide.ui.viewer.platform.PlatformActions.IPlatformUI;
import org.hpccsystems.internal.data.CollectionDelta;
import org.hpccsystems.internal.data.Data;
import org.hpccsystems.internal.data.DataSingleton;
import org.hpccsystems.internal.data.DataSingletonCollection;
import org.hpccsystems.internal.data.Workunit;
import org.hpccsystems.internal.ui.tree.ItemView;
import org.hpccsystems.internal.ui.tree.LazyChildLoader;
import org.hpccsystems.internal.ui.tree.WorkunitComparator;

public class ECLWindow extends MultiPageEditorPart implements IResourceChangeListener, Observer, TreeItemOwner {

	ECLContentOutlinePage eclOutlinePage;
	
	private ECLEditor editor;

	Data data;
	CTabFolder workunitFolder;
	LazyChildLoader<ItemView> children;
	
	Map<WorkunitView, WorkunitTabItem> workunitViewTabMap;

	PlatformActions actions;	

	public ECLWindow() {
		super();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		this.data = null;
		this.workunitFolder = null;
		this.children = new LazyChildLoader<ItemView>();
		this.workunitViewTabMap = new HashMap<WorkunitView, WorkunitTabItem>();
		
		actions = new PlatformActions(new IPlatformUI() {
			
			@Override
			public void refresh() {
			}
			
			@Override
			public Vector<ItemView> getSelection() {
				Vector<ItemView> retVal = new Vector<ItemView>(); 
				CTabItem childItem = getTabItem(getActivePage());
				if (childItem != null && childItem instanceof WorkunitTabItem) {
					WorkunitTabItem treeItemTabItem = (WorkunitTabItem)childItem;
					retVal.add(treeItemTabItem.getWorkunitView());
				}
				return retVal;
			}
		});
	}

	WorkunitTabItem createItem(Control control, WorkunitView wuti) {
		int index = getPageCount();
		return createItem(index, control, wuti);
	}

	WorkunitTabItem createItem(int index, Control control, WorkunitView wuView) {
		WorkunitTabItem item = new WorkunitTabItem(workunitFolder, SWT.NONE, index, wuView);
		return item;
	}
	
	void createEditorPage() {
		try {
			editor = new ECLEditor();
			int index = addPage(editor, getEditorInput());
			this.setTitle(editor.getTitle());
			setPageText(index, "ECL");
			setPageImage(index, Activator.getImage("icons/doc.png"));
		} catch (PartInitException e) {
			ErrorDialog.openError(
					getSite().getShell(),
					"Error creating nested text editor",
					null,
					e.getStatus());
		}
	}
	
	WorkunitTabItem createWorkunitPage(WorkunitView wuti, boolean addToEnd) {
		//  TODO need to do better check than label...
    	boolean found = false;
    	for (int i = 1; i < getPageCount(); ++i) {
    		if (getPageText(i).compareTo(wuti.getText()) == 0) {
    			found = true;
    			break;
    		}			    		
    	}
    	
    	if (!found) {
    		WorkunitTabItem retVal =  addToEnd ? createItem(getContainer(), wuti) : createItem(1, getContainer(), wuti);
    		return retVal;
    	}
    	return null;
	}

	void createWorkunitPages() {
		data = Data.get();
		workunitFolder = (CTabFolder)getContainer(); 		
		children.start(new Runnable() {
			@Override
			public void run() {
				refreshChildren();
				refresh();
			}
		});
	}

	@Override
	protected void createPages() {
		createEditorPage();
		createWorkunitPages();
		createContextMenu();
	}
	/**
	 * The <code>MultiPageEditorPart</code> implementation of this 
	 * <code>IWorkbenchPart</code> method disposes all nested editors.
	 * Subclasses may extend.
	 */
	@Override
	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}
	/**
	 * Saves the multi-page editor's document.
	 */
	@Override
	public void doSave(IProgressMonitor monitor) {
		getEditor(0).doSave(monitor);
	}
	/**
	 * Saves the multi-page editor's document as another file.
	 * Also updates the text for page 0's tab, and updates this multi-page editor's input
	 * to correspond to the nested editor's.
	 */
	@Override
	public void doSaveAs() {
		IEditorPart editor = getEditor(0);
		editor.doSaveAs();
		setPageText(0, editor.getTitle());
		setInput(editor.getEditorInput());
	}
	/* (non-Javadoc)
	 * Method declared on IEditorPart
	 */
	public void gotoMarker(IMarker marker) {
		setActivePage(0);
		IDE.gotoMarker(getEditor(0), marker);
	}
	/**
	 * The <code>MultiPageEditorExample</code> implementation of this method
	 * checks that the input is an instance of <code>IFileEditorInput</code>.
	 */
	@Override
	public void init(IEditorSite site, IEditorInput editorInput)
			throws PartInitException {
		if (!(editorInput instanceof IFileEditorInput))
			throw new PartInitException("Invalid Input: Must be IFileEditorInput");
		super.init(site, editorInput);
	}

	@Override
	public boolean isSaveAsAllowed() {
		return true;
	}
	
	CTabItem getTabItem(int index){
		CTabItem childItem = ((CTabFolder)getContainer()).getItem(index);
		return childItem;
	}

	@Override
	protected void pageChange(int newPageIndex) {
		super.pageChange(newPageIndex);
	}

	@Override
	public void resourceChanged(final IResourceChangeEvent event){
		if(event.getType() == IResourceChangeEvent.PRE_CLOSE){
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run(){
					IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
					for (int i = 0; i<pages.length; i++){
						if(((FileEditorInput)editor.getEditorInput()).getFile().getProject().equals(event.getResource())){
							IEditorPart editorPart = pages[i].findEditor(editor.getEditorInput());
							pages[i].closeEditor(editorPart,true);
						}
					}
				}
			});
		}
	}
	
	@Override
	public Object getAdapter(Class required) {
		if (IContentOutlinePage.class.equals(required)) {
			if (eclOutlinePage == null) {
				eclOutlinePage = new ECLContentOutlinePage();
				eclOutlinePage.setInput(getEditorInput());
				eclOutlinePage.addSelectionChangedListener(new ISelectionChangedListener() {

					@Override
					public void selectionChanged(SelectionChangedEvent event) {
						ISelection selection = event.getSelection();
						if (selection != null && selection instanceof TreeSelection) {
							Object o = ((TreeSelection)selection).getFirstElement();
							if (o != null && o instanceof ECLDefinition) {
								ECLDefinition def = (ECLDefinition)o;
								editor.setHighlightRange(def.getOffset(), def.getLength(), true);
							}
						}
					}
				});
				
			}
			return eclOutlinePage;
		}
		return super.getAdapter(required);
	}

	public void refreshChildren() {
		Workunit.All.deleteObserver(this);
			
		CollectionDelta delta = new CollectionDelta("primeChildren", getCurrentWorkunits());
		delta.calcChanges(data.getWorkunits(null, "", "", ""));
		mergeChanges(delta);

		Workunit.All.addObserver(this);
	}
	
	public void reloadChildren() {
		children.clear();
		refreshChildren();
		refresh();
	}

	Collection<Workunit> getCurrentWorkunits() {
		Collection<Workunit> retVal = new ArrayList<Workunit>();
		for (Object item : children.get().clone()) {
			if (item instanceof WorkunitView) {
				WorkunitView ti = (WorkunitView)item;
				retVal.add(ti.getWorkunit());
			}
		}
		return retVal;
	}

	boolean mergeChanges(CollectionDelta delta) {
		boolean changed = false;
		for (Object item : children.get().clone()) {
			if (item instanceof WorkunitView) {
				WorkunitView ti = (WorkunitView)item;

				//  Remove deleted workunits  --- 
				if (delta.removeContains(ti.getWorkunit())) {
					ti.getWorkunit().refreshState();
					changed = true;
				}
			}
		}
		
		//  Add new workunits  ---
		IFileEditorInput input = (IFileEditorInput) getEditorInput(); 
		IFile file = input.getFile();
		String filePath = file.getFullPath().toPortableString();
		for (DataSingleton ds : delta.getAdded()) {
			if (ds instanceof Workunit) {
				Workunit wu = (Workunit)ds;
				
				if (filePath.compareTo(wu.getApplicationValue("path")) == 0) {
					children.add(new WorkunitView(this, null, wu));
					changed = true;
				}
			}
		}
		
		if (changed)
			children.sort(new WorkunitComparator());

		return changed;
	}
	
	@Override
	public void update(Observable o, Object arg) {
		if (o instanceof DataSingletonCollection) {
			if (arg instanceof CollectionDelta) {
				if (mergeChanges((CollectionDelta)arg)) {
					refresh();
				}
			}
		}
	}

	@Override
	public void update(Object element, String[] properties) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void refresh(Object element) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void refresh() {
		Display.getDefault().asyncExec(new Runnable() {
			
			@Override
			public void run() {
				int pos = 1;
				for (Object item : children.get().clone()) {
					if (item instanceof WorkunitView) {
						WorkunitView wuView = (WorkunitView)item;
						if (!workunitViewTabMap.containsKey(wuView)) {
							workunitViewTabMap.put(wuView, new WorkunitTabItem(workunitFolder, SWT.NONE, pos, wuView));
						}
						++pos;
					}
				}
			}
		});
	}
	
	protected void createContextMenu() {
		// Create menu manager.
		MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager mgr) {
				fillContextMenu(mgr);
			}
		});
		
		// Create menu.
		Menu menu = menuMgr.createContextMenu(getContainer());
		getContainer().setMenu(menu);
		
		// Register menu for extension.
		//getSite().registerContextMenu(menuMgr, getContainer());
	}	

	private void fillContextMenu(IMenuManager mgr) {
		mgr.add(actions.abortItemAction);
		mgr.add(actions.resubmitItemAction);
		mgr.add(actions.restartItemAction);
		mgr.add(actions.publishItemAction);
		mgr.add(new Separator());
		mgr.add(actions.cloneItemAction);
		mgr.add(actions.deleteItemAction);
		actions.setState();
	}
}
