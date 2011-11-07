/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.hpccsystems.eclide.importWizards;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.rpc.ServiceException;

import org.apache.axis.components.threadpool.ThreadPool;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;
import org.eclipse.ui.dialogs.WizardResourceImportPage;
import org.hpccsystems.internal.Eclipse;
import org.hpccsystems.ws.wsworkunits.WsWorkunitsLocator;
import org.hpccsystems.ws.wsworkunits.WsWorkunitsServiceSoap;

import com.seisint.webservices.WsAttributes.ArrayOfEspException;
import com.seisint.webservices.WsAttributes.ECLAttribute;
import com.seisint.webservices.WsAttributes.ECLModule;
import com.seisint.webservices.WsAttributes.GetAttribute;
import com.seisint.webservices.WsAttributes.GetAttributeResponse;
import com.seisint.webservices.WsAttributes.GetAttributes;
import com.seisint.webservices.WsAttributes.GetAttributesResponse;
import com.seisint.webservices.WsAttributes.GetModules;
import com.seisint.webservices.WsAttributes.GetModulesResponse;
import com.seisint.webservices.WsAttributes.WsAttributesLocator;
import com.seisint.webservices.WsAttributes.WsAttributesServiceSoap;

public class ImportWizardPage extends WizardResourceImportPage {
	
	public class PasswordFieldEditor extends StringFieldEditor { 

		public PasswordFieldEditor(String name, String label, Composite parent) { 
			super(name, label, parent); 
		} 

		protected void doFillIntoGrid(Composite parent, int numColumns) 
		{ 
			// Creates the text control 
			super.doFillIntoGrid(parent, numColumns); 

			// Now we can set the echo character 
			getTextControl().setEchoChar('*'); 
		} 
	} 

	
	protected StringFieldEditor fIPText;
	protected StringFieldEditor fUserText;
	protected PasswordFieldEditor fPasswordText;

	public ImportWizardPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
		setTitle(pageName); //NON-NLS-1
		setDescription("Import an entire Repository from a remote legacy server into the workspace."); //NON-NLS-1
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#createAdvancedControls(org.eclipse.swt.widgets.Composite)
	 */	
	protected void createAdvancedControls(Composite parent) {
	}

	@Override
	protected void createSourceGroup(Composite parent) {
		Composite fileSelectionArea = new Composite(parent, SWT.NONE);
		GridData fileSelectionData = new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL);
		fileSelectionArea.setLayoutData(fileSelectionData);

		GridLayout fileSelectionLayout = new GridLayout();
		fileSelectionLayout.numColumns = 3;
		fileSelectionLayout.makeColumnsEqualWidth = false;
		fileSelectionLayout.marginWidth = 0;
		fileSelectionLayout.marginHeight = 0;
		fileSelectionArea.setLayout(fileSelectionLayout);
		
		fIPText = new StringFieldEditor("IPSelect", "Server IP:  ", fileSelectionArea);
		fIPText.setStringValue("10.173.84.202");
		fUserText = new StringFieldEditor("User", "User:  ", fileSelectionArea);
		fUserText.setStringValue("gosmith");
		fPasswordText = new PasswordFieldEditor("Password", "Password:  ", fileSelectionArea);
		fPasswordText.setStringValue("password");
	}

	@Override
	protected ITreeContentProvider getFileProvider() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ITreeContentProvider getFolderProvider() {
		// TODO Auto-generated method stub
		return null;
	}
	
	boolean doImport() {
		final IFolder targetFolder = Eclipse.getWorkspaceRoot().getFolder(getContainerFullPath());
		
		WsAttributesLocator locator = new WsAttributesLocator();
		try {
			final WsAttributesServiceSoap service = locator.getWsAttributesServiceSoap(new URL("http", fIPText.getStringValue(), 8145, "/WsAttributes"));
			org.apache.axis.client.Stub stub = (org.apache.axis.client.Stub)service;
			stub.setUsername(fUserText.getStringValue());
			stub.setPassword(fPasswordText.getStringValue());
			GetModules request = new GetModules();
			final GetModulesResponse response = service.getModules(request);
			if (response.getOutModules() != null) {

				Job job = new Job("Importing Attributes") {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						monitor.beginTask("Importing", response.getOutModules().length);
						for (final ECLModule module : response.getOutModules()) {
							if (module.getName().equalsIgnoreCase("Trash"))
								continue;
							monitor.subTask(module.getName());

							GetAttributes request2 = new GetAttributes();
							request2.setModuleName(module.getName());
							try {
								GetAttributesResponse response2 = service.getAttributes(request2);
								if (response2.getOutAttributes() != null) {
									int MAX_THREAD = 5;
									ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREAD);
									for (final ECLAttribute attribute : response2.getOutAttributes()) {
										String modPath = attribute.getModuleName();
										modPath.replaceAll(".", "/");
										String attrPath = attribute.getName() + ".ecl";
										IPath targetPath = targetFolder.getLocation();
										IPath fullPath = targetPath.append(modPath + "/" + attrPath).makeAbsolute();
										final File targetFile = new File(fullPath.toOSString());
										
										try {
											targetFile.getParentFile().mkdirs(); 
											if (targetFile.createNewFile()) {
												threadPool.execute(new Runnable() {
													public void run() {
														GetAttribute request3 = new GetAttribute();
														request3.setModuleName(module.getName());
														request3.setAttributeName(attribute.getName());
														request3.setGetText(true);
														try {
															GetAttributeResponse response3 = service.getAttribute(request3);
															if (response3.getOutAttribute() != null) {
																ECLAttribute attribute2 = response3.getOutAttribute();
																try {
																	FileWriter fstream = new FileWriter(targetFile);
																	BufferedWriter out = new BufferedWriter(fstream);
																	out.write(attribute2.getText());
																	out.flush();
																} catch (IOException e) {
																	// TODO Auto-generated catch block
																	e.printStackTrace();
																}
																System.out.println(attribute2.getModuleName() + "." + attribute2.getName());
															}
														} catch (ArrayOfEspException e) {
															// TODO Auto-generated catch block
															e.printStackTrace();
														} catch (RemoteException e) {
															// TODO Auto-generated catch block
															e.printStackTrace();
														}
													}
												});
											}
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
									threadPool.shutdown();
									try {
										while(!threadPool.awaitTermination(300, TimeUnit.MILLISECONDS)) {
											if (monitor.isCanceled()) {
												threadPool.shutdownNow();											
												return Status.CANCEL_STATUS;
											}
										}
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									if (monitor.isCanceled()) {
										return Status.CANCEL_STATUS;
									}
								}
							} catch (ArrayOfEspException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								//return new Status();
							} catch (RemoteException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							monitor.worked(1);
						}
						return Status.OK_STATUS;
					}
				};
				job.schedule();
				return true;	
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ArrayOfEspException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;	
	}
}
