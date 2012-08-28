package org.hpccsystems.eclide.ui.viewer.platform;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import org.hpccsystems.internal.data.Graph;
import org.hpccsystems.internal.data.Workunit;
import org.hpccsystems.internal.ui.tree.ItemView;

class GraphFolderView extends FolderItemView implements Observer  {
	Workunit workunit;

	GraphFolderView(TreeItemOwner treeViewer, PlatformBaseView parent, Workunit wu) {
		super(treeViewer, parent, wu.getPlatform());
		workunit = wu;
		workunit.addObserver(this);
	}

	@Override
	public String getText() {
		return "Graphs";
	}

	@Override
	public URL getWebPageURL() throws MalformedURLException {
		return platform.getURL("esp/files", "WUGraph.htm", "Wuid=" + workunit.getWuid());
	}

	@Override
	public void refreshChildren() {
		ArrayList<Object> retVal = new ArrayList<Object>();
		for(Graph g : workunit.getGraphs()) {
			retVal.add(new GraphView(treeViewer, this, platform, g));
		}
		children.set(retVal.toArray(new ItemView[0]));
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		if (arg1 instanceof Workunit.Notification) {
			switch ((Workunit.Notification)arg1){
			case GRAPHS:
				refresh();
			}
		}
	}
}
