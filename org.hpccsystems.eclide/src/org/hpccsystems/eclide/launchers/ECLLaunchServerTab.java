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
package org.hpccsystems.eclide.launchers;

import java.awt.Checkbox;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.AuthenticationEvent;
import org.eclipse.swt.browser.AuthenticationListener;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.hpccsystems.eclide.Activator;
import org.hpccsystems.internal.ECLLaunchConfigurationTab;
import org.hpccsystems.internal.data.Data;
import org.hpccsystems.internal.data.Platform;

public class ECLLaunchServerTab extends ECLLaunchConfigurationTab {

	private class WidgetListener extends SelectionAdapter implements ModifyListener {
		public void modifyText(ModifyEvent e) {
			scheduleUpdateJob();
			Object source= e.getSource();
			if (source == fIPText) {
				refreshAddress();
			} if (source == fAddressText) {
			}
		}
		
		public void widgetSelected(SelectionEvent e) {
			Object source= e.getSource();
			if (source == testButton) {
				refreshBrowser();
			} else if (source == enableButton) {
				scheduleUpdateJob();
			}
		}
	}
	
	private WidgetListener fListener = new WidgetListener();
	
    Image image;

	private Button enableButton;
	protected Text fIPText;
	protected Text fClusterText;

	protected Text fUserText;
	protected Text fPasswordText;

	protected Text fAddressText;
	private Button testButton;
	private Browser browser;

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		return super.isValid(launchConfig);
	}

	protected void createServerEditor(Composite parent) {
		enableButton = SWTFactory.createCheckButton(parent, "Server Active (Will be disabled if unreachable)", null, true, 1);
		enableButton.addSelectionListener(fListener);

		Group group = SWTFactory.createGroup(parent, "Server:", 2, 1, GridData.FILL_HORIZONTAL);
		SWTFactory.createLabel(group, "IP Address:  ", 1);
		fIPText = SWTFactory.createSingleText(group, 1);
		fIPText.addModifyListener(fListener);

		SWTFactory.createLabel(group, "Cluster:  ", 1);
		fClusterText = SWTFactory.createSingleText(group, 1);
		fClusterText.addModifyListener(fListener);
	}
	
	protected void createCredentialsEditor(Composite parent) {
		Group group = SWTFactory.createGroup(parent, "Credentials:", 2, 1, GridData.FILL_HORIZONTAL);
		SWTFactory.createLabel(group, "User:  ", 1);
		fUserText = SWTFactory.createSingleText(group, 1);
		fUserText.addModifyListener(fListener);

		SWTFactory.createLabel(group, "Password:  ", 1);
		fPasswordText = SWTFactory.createText(group, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD, 1);
		fPasswordText.addModifyListener(fListener);
	}

	protected void createBrowser(Composite parent) {
		Group group = SWTFactory.createGroup(parent, "ECL Watch:", 3, 1, GridData.FILL_BOTH);
		SWTFactory.createLabel(group, "Address:  ", 1);
		fAddressText = SWTFactory.createSingleText(group, 1);
		fAddressText.addModifyListener(fListener);
		testButton = SWTFactory.createPushButton(group, "Test", null);
		testButton.addSelectionListener(fListener);

		browser = new Browser(group, SWT.BORDER);
		browser.setUrl("about:blank");
    	GridData gd = new GridData(GridData.FILL_BOTH);
    	gd.horizontalSpan = 3;
    	browser.setLayoutData(gd);
    	browser.addAuthenticationListener(new AuthenticationListener() {
			
			@Override
			public void authenticate(AuthenticationEvent event) {
				// TODO Auto-generated method stub
				event.user = fUserText.getText();
				event.password = fPasswordText.getText();
			}
		});
	}

	public final void createControl(Composite parent) {
		Composite projComp = SWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH); 
		((GridLayout)projComp.getLayout()).verticalSpacing = 0;
		
		createVerticalSpacer(projComp, 1);
		createServerEditor(projComp);
		createCredentialsEditor(projComp);		
		createVerticalSpacer(projComp, 1);
		createBrowser(projComp);
		setControl(projComp);
		
	}
	
	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
//		ip.setText("localhost");
//		cluster.setText("hthor");
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			enableButton.setSelection(configuration.getAttribute(Platform.P_ENABLED, true));

			fIPText.setText(configuration.getAttribute(Platform.P_IP, "localhost"));
			fClusterText.setText(configuration.getAttribute(Platform.P_CLUSTER, "hthor"));

			fUserText.setText(configuration.getAttribute(Platform.P_USER, ""));
			fPasswordText.setText(configuration.getAttribute(Platform.P_PASSWORD, ""));
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(Platform.P_ENABLED, enableButton.getSelection());

		configuration.setAttribute(Platform.P_IP, fIPText.getText());
		configuration.setAttribute(Platform.P_CLUSTER, fClusterText.getText());

		configuration.setAttribute(Platform.P_USER, fUserText.getText());
		configuration.setAttribute(Platform.P_PASSWORD, fPasswordText.getText());
	}

	protected void handleProjectButtonSelected() {
//		IJavaProject project = chooseJavaProject();
//		if (project == null) {
//			return;
//		}
//		String projectName = project.getElementName();
//		fProjText.setText(projectName);		
	}

	void refreshAddress() {
		StringBuilder url = new StringBuilder("http://");
		url.append(fIPText.getText());
		url.append(":8010/");
		fAddressText.setText(url.toString());
	}
	
	void refreshBrowser() {
		browser.addProgressListener(new ProgressAdapter() {
			public void completed(ProgressEvent event) {
				browser.removeProgressListener(this);
				System.out.println(fAddressText.getText());
				browser.setUrl(fAddressText.getText());
			}
		});
		browser.setText("<html><body><h3>Loading (" + fAddressText.getText() + ")...</h3></body></html>");
	}

	@Override
	public String getName() {
		return "HPCC Platform";
	}

    public Image getImage() {
        if (image == null) {
        	image = Activator.getImage("icons/releng_gears.gif"); //$NON-NLS-1$
        }
        return image;
    }
}
