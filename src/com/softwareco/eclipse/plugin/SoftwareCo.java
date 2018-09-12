/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.ITextEditor;
import org.osgi.framework.BundleContext;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.softwareco.eclipse.plugin.SoftwareCoKeystrokeManager.KeystrokeCountWrapper;

/**
 * The activator class controls the plug-in's life cycle
 */
public class SoftwareCo extends AbstractUIPlugin implements IStartup {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.software.eclipse.plugin";
	public static final String VERSION = Platform.getBundle( PLUGIN_ID ).getVersion().toString();
	
	// The shared instance
	public static ILog logInstance;
	
	public static JsonParser jsonParser = new JsonParser();
	
	public static Gson gson;

	// Listeners (used to listen to file
	// events such as opened, activated, input changed, etc
	private static SoftwareCoFileEditorListener editorListener;
	
	// managers used by the static processing method
	private static SoftwareCoKeystrokeManager keystrokeMgr;
	private static SoftwareCoHttpClientManager clientMgr;
	
	private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();
	
	// private keystroke processor timer and client manager
	private Timer timer;
	private Timer kpmFetchTimer;

	/**
	 * The constructor
	 */
	public SoftwareCo() {
		gson = new Gson();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.
	 * BundleContext)
	 */
	public void start( BundleContext context ) throws Exception {
		logInstance = getLog();

		super.start( context );
		keystrokeMgr = SoftwareCoKeystrokeManager.getInstance();
		clientMgr = SoftwareCoHttpClientManager.getInstance();

		editorListener = new SoftwareCoFileEditorListener();
	}

	/**
	 * This will initialize the file editor listener to any already open file and perform
	 * other initialization work.
	 */
	@Override
	public void earlyStartup() {
		final IWorkbench workbench = PlatformUI.getWorkbench();

		workbench.getDisplay().asyncExec(new Runnable() {
			public void run() {
				IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
				IWorkspace workspace = ResourcesPlugin.getWorkspace();

				if ( window == null || workspace == null ) {
					return;
				}

				SoftwareCoLogger.debug("Software.com: Loaded v" + VERSION + " on platform: " + SWT.getPlatform());
				
				SoftwareCoUtils.setStatusLineMessage("Software.com", "Loaded v" + VERSION);

				if ( window.getPartService() == null ) {
					//
					// We need to the part service to add a listener to, so return out since it's null
					//
					return;
				}

				//
				// listen for file changes
				//
				window.getPartService().addPartListener( editorListener );
				
				//
				// We've add the editor listener, start the processor
				//
				timer = new Timer();
				// run every minute
		        timer.scheduleAtFixedRate(new ProcessKeystrokesTask(), 60 * 1000, 60 * 1000);

				IEditorInput input = getWorkbenchIEditorInput();
				if ( input == null ) {
					return;
				}

				//
				// check if one is open by default and start the listener on that document
				//
				IEditorPart editor = null;
				try {
					editor = window.getPartService().getActivePart().getSite().getPage().getActiveEditor();
				} catch (Exception e) {
					// active editor may not be ready
				}
				
				if ( input instanceof IURIEditorInput ) {

					//
					// Add the current open document, if it's of IEditorInput, to the document
					// listener
					//
					if ( input instanceof IEditorInput ) {
						IDocument document = ( ( (ITextEditor) editor ).getDocumentProvider() ).getDocument( input );
						editorListener.addDocumentListener( document );
					}
				}
				
				// run the initial calls in 15 seconds
				new Thread(() -> {
			        try {
			            Thread.sleep(1000 * 15);
			            sessionMgr.sendOfflineData();
			        }
			        catch (Exception e){
			            System.err.println(e);
			        }
			    }).start();
				
				ProcessKpmSessionInfoTask sessionInfoTask = new ProcessKpmSessionInfoTask();
				
				// run the kpm fetch task every minute
				kpmFetchTimer = new Timer();
				kpmFetchTimer.scheduleAtFixedRate(sessionInfoTask, 0, 60 * 1000);
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop( BundleContext context ) throws Exception {
		keystrokeMgr = null;
		super.stop(context);

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if ( window != null && window.getPartService() != null ) {
			//
			// Remove the editor listener
			//
			window.getPartService().removePartListener( editorListener );
		}
		
		//
		// Kill the timers
		//
		if ( timer != null ) {
			timer.cancel();
			timer = null;
		}
		
		if (kpmFetchTimer != null) {
			kpmFetchTimer.cancel();
			kpmFetchTimer = null;
		}
	}
	
	public static String getUserHomeDir() {
		return System.getProperty("user.home");
	}
	
	public static boolean isWindows() {
		return (SWT.getPlatform().startsWith("win"));
	}
	
	public static boolean isMac() {
		return (SWT.getPlatform().equals("carbon") || SWT.getPlatform().equals("cocoa"));
	}
	
	public static void handleFileOpenedEvent() {
		String fileName = getCurrentFileName();
		String projectName = SoftwareCo.getActiveProjectName(fileName);
		if (fileName == null) {
			return;
		}
		
		initializeKeystrokeObjectGraph(projectName, fileName);
		
		SoftwareCoKeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount(projectName);
		JsonObject fileInfo = keystrokeCount.getFileInfo(fileName);
		
		updateFileInfoValue(fileInfo, "open", 1);
		
		SoftwareCoLogger.info("Software.com: file opened: " + fileName);
	}
	
	public static void handleFileClosedEvent(String fileName) {
		String projectName = SoftwareCo.getActiveProjectName(fileName);
		if (fileName == null) {
			return;
		}
		
		initializeKeystrokeObjectGraph(projectName, fileName);
		
		SoftwareCoKeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount(projectName);
		JsonObject fileInfo = keystrokeCount.getFileInfo(fileName);
		
		updateFileInfoValue(fileInfo, "close", 1);
		
		SoftwareCoLogger.info("Software.com: file closed: " + fileName);
	}
	
	protected static int getLineCount(String fileName) {
        Path path = Paths.get(fileName);
        try {
            return (int) Files.lines(path).count();
        } catch (IOException e) {
        		SoftwareCoLogger.info("Software.com: failed to get the line count for file " + fileName);
            return 0;
        }
    }

	/**
	 * Take the changed document metadata and process them.
	 * @param docEvent
	 */
	public static void handleChangeEvents(DocumentEvent docEvent) {
		
		//
		// The docEvent.getLength() will return a positive number
		// for character deletion, single or bulk deletion
		//
		int numKeystrokes = docEvent.getLength();
		
		boolean isNewLine = false;
		if (docEvent.getText().matches("\n.*") || docEvent.getText().matches("\r\n.*")) {
			isNewLine = true;
		}
		
		String fileName = getCurrentFileName();
		String projectName = SoftwareCo.getActiveProjectName(fileName);
		
		initializeKeystrokeObjectGraph(projectName, fileName);
		
		SoftwareCoKeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount(projectName);
		JsonObject fileInfo = keystrokeCount.getFileInfo(fileName);
		
		String currentTrack = SoftwareCoUtils.getCurrentMusicTrack();
        String trackInfo = fileInfo.get("trackInfo").getAsString();
        if ((trackInfo == null || trackInfo.equals("")) && (currentTrack != null && !currentTrack.equals(""))) {
            updateFileInfoStringValue(fileInfo, "trackInfo", currentTrack);
        }

		if (!isNewLine) {
			//
			// get keystroke count from docEvent if getLength() return zero
			//
			if ( numKeystrokes == 0 ) {
				//
				// This means we had a copy and paste or it was normal kpm addition
				// Count the number of characters in the text attribute
				//
				numKeystrokes = ( docEvent.getText() != null ) ? docEvent.getText().length() : 0;
			} else {
				// make it negative since it's character deletion
				numKeystrokes = numKeystrokes / -1;
			}
	        
	        if (numKeystrokes > 1) {
	        	// It's a copy and paste event
		        updateFileInfoValue(fileInfo, "paste", numKeystrokes);
		        SoftwareCoLogger.info("Software.com: Copy+Paste incremented");
	        } else if (numKeystrokes < 0) {
	        	int deleteKpm = Math.abs(numKeystrokes);
	        	// It's a character delete event
		        updateFileInfoValue(fileInfo, "delete", deleteKpm);
		        SoftwareCoLogger.info("Software.com: Delete incremented");
	        } else if (numKeystrokes == 1) {
	        	// increment the specific file keystroke value
		        updateFileInfoValue(fileInfo, "add", 1);
				SoftwareCoLogger.info("Software.com: KPM incremented");
	        }
		}
		
		if (isNewLine || numKeystrokes != 0) {
			// increment the data count
			int incrementedCount = Integer.parseInt(keystrokeCount.getData()) + 1;
			keystrokeCount.setData( String.valueOf(incrementedCount) );
		}
        
        int filelen = (docEvent.getDocument() != null) ? docEvent.getDocument().getLength() : -1;
        if (filelen != -1) {
        	updateFileInfoValue(fileInfo, "length", filelen);
        }
        
        int lines = getPreviousLineCount(fileInfo);
        if (lines == -1) {
        	lines = getLineCount(fileName);
        	if (lines == -1) {
        		lines = 0;
        	}
        }
        
        if (isNewLine) {
        	lines += 1;
        	// new lines added
            updateFileInfoValue(fileInfo, "linesAdded", 1);
            SoftwareCoLogger.info("Software.com: lines added incremented");
        }

        updateFileInfoValue(fileInfo, "lines", lines);
	}
	
	private static int getPreviousLineCount(JsonObject fileInfo) {
        JsonPrimitive keysVal = fileInfo.getAsJsonPrimitive("lines");
        return keysVal.getAsInt();
    }
	
	private static void updateFileInfoStringValue(JsonObject fileInfo, String key, String value) {
		fileInfo.addProperty(key, value);
	}
	
	private static void updateFileInfoValue(JsonObject fileInfo, String key, int incrementVal) {
		JsonPrimitive keysVal = fileInfo.getAsJsonPrimitive(key);
		
		if (key.equals("length") || key.equals("lines")) {
			// it's not additive
			fileInfo.addProperty(key, incrementVal);
		} else {
			// it's additive
			int totalVal = keysVal.getAsInt() + incrementVal;
			fileInfo.addProperty(key, totalVal);
		}
        
        if (key.equals("add") || key.equals("delete")) {
        		// update the netkeys and the keys
            	// "netkeys" = add - delete
            	// "keys" = add + delete
        		int addVal = fileInfo.get("add").getAsInt();
        		int deleteVal = fileInfo.get("delete").getAsInt();
        		fileInfo.addProperty("netkeys", (addVal - deleteVal));
        		fileInfo.addProperty("keys", (addVal + deleteVal));
        }
	}
	
	public static void initializeKeystrokeObjectGraph(String projectName, String fileName) {
		SoftwareCoKeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount(projectName);
		if ( keystrokeCount == null ) {
			//
			// Create one since it hasn't been created yet
			// and set the start time (in seconds)
			//
			keystrokeCount = new SoftwareCoKeystrokeCount();
			
			//
			// Update the manager with the newly created KeystrokeCount object
			//
			keystrokeMgr.setKeystrokeCount(projectName, keystrokeCount);
		}
		
		//
		// Make sure we have the project name and directory info
		updateKeystrokeProject(fileName, keystrokeCount);
	}
	
	private static void updateKeystrokeProject(String fileName, SoftwareCoKeystrokeCount keystrokeCount) {
		if (keystrokeCount == null) {
			return;
		}
		SoftwareCoProject project = keystrokeCount.getProject();
		String projectName = SoftwareCo.getActiveProjectName(fileName);
		IProject iproj = getFileProject(fileName);
		String projectDirectory = (iproj != null)? iproj.getLocation().toString() : "";
		
		if (project == null) {
			project = new SoftwareCoProject( projectName, projectDirectory );
			keystrokeCount.setProject( project );
		} else if (project.getName() == null || project.getName().equals("")) {
			project.setDirectory(projectDirectory);
			project.setName(projectName);
		} else if ((project.getDirectory() == null || project.getDirectory().equals("")) && !projectDirectory.equals("")) {
			project.setDirectory(projectDirectory);
		}
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in relative
	 * path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin( PLUGIN_ID, path );
	}
	
	protected static void processKeystrokes() {
		
		List<KeystrokeCountWrapper> wrapperList = keystrokeMgr.getKeystrokeCountWrapperList();
		for (KeystrokeCountWrapper wrapper : wrapperList) {
			SoftwareCoKeystrokeCount keystrokeCount = wrapper.getKeystrokeCount();
			//
			// Only send an update if the keystroke count object is not null and we have keystroke activity
			//
			if ( keystrokeCount != null && keystrokeCount.hasData() ) {

				keystrokeCount.setEnd( keystrokeCount.getStart() + 60 );
				
				//
				// Send the info now
				//
				clientMgr.sendKeystrokeData(keystrokeCount);
				
				SoftwareCoUtils.reDisplayStatusMessage();
			}
		}
	}
	
	/**
	 * 
	 * Inner class to handle the 1-minute timer to process any keystrokes that
	 * need to get sent to the plugin manager.
	 *
	 */
	private class ProcessKeystrokesTask extends TimerTask {
        public void run() {
	        	//
	    		// This will check if we should process the keystrokes (send the data to the server)
	    		//
	    		processKeystrokes();
        }
    }
	
	private class ProcessKpmSessionInfoTask extends TimerTask {
		public void run() {
			sessionMgr.fetchDailyKpmSessionInfo();
		}
	}
	
	/**
	 * Retrieve the IEditorInput which gives us the current Document. It will return null
	 * if any of the paths to get the editor input is null.
	 * @return IEditorInput
	 */
	private static IEditorInput getWorkbenchIEditorInput() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		
		try {
			return window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput();
		} catch (NullPointerException e) {
			SoftwareCoLogger.info("Software.com: Unable to retrieve the IEditorInput from workbench window. " + e.getMessage());
			return null;
		}
	}
	
	public static String getActiveProjectName(String fileName) {
		IProject project = getFileProject(fileName);
		if (project != null) {
			return project.getName();
		}
		return "None";
	}
	
	private static IProject getFileProject(String fileName) {
		if (fileName == null) {
			return null;
		}
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			IPath locationPath = project.getLocation();
			String pathStr = locationPath.toString();
			if (pathStr != null && fileName.indexOf(pathStr) != -1) {
				return project;
			}
		}
		return null;
	}
	
	private static String getCurrentFileName() {
		IEditorInput input = getWorkbenchIEditorInput();
		if ( input == null ) {
			return null;
		}

		if ( input instanceof IURIEditorInput ) {

			URI uri = ( (IURIEditorInput) input ).getURI();
			
			//
			// Set the current file
			//
			if ( uri != null && uri.getPath() != null ) {
				String currentFile = uri.getPath();
				return currentFile;
			}
		}
		return null;
	}
}