/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;


public class SoftwareCoHttpClientManager {
	
	private final static String PM_BUCKET = "https://s3-us-west-1.amazonaws.com/swdc-plugin-manager/";
    private final static String PM_NAME = "software";

	private static SoftwareCoHttpClientManager instance = null;
	
	protected static boolean downloadingPM = false;
    
    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();

	/**
	 * Protected constructor to defeat instantiation
	 */
	protected SoftwareCoHttpClientManager() {
		//
	}

	public static SoftwareCoHttpClientManager getInstance() {
		if (instance == null) {
			instance = new SoftwareCoHttpClientManager();
		}
		return instance;
	}
	
	private static String getFileUrl() {
        String fileUrl = PM_BUCKET + PM_NAME;
        if (SoftwareCo.isWindows()) {
            fileUrl += ".exe";
        } else if (SoftwareCo.isMac()) {
            fileUrl += ".dmg";
        } else {
            fileUrl += ".deb";
        }
        return fileUrl;
    }

    private static String getDownloadFilePathName() {
        String downloadFilePathName = SoftwareCo.getUserHomeDir();
        if (SoftwareCo.isWindows()) {
            downloadFilePathName += "\\Desktop\\" + PM_NAME + ".exe";
        } else if (SoftwareCo.isMac()) {
            downloadFilePathName += "/Desktop/" + PM_NAME + ".dmg";
        } else {
            downloadFilePathName += "/Desktop/" + PM_NAME + ".deb";
        }

        return downloadFilePathName;
    }

    private static String getPmInstallDirectoryPath() {
        if (SoftwareCo.isWindows()) {
            return SoftwareCo.getUserHomeDir() + "\\AppData\\Local\\Programs";
        } else if (SoftwareCo.isMac()) {
            return "/Applications";
        } else {
            return "/user/lib";
        }
    }

    protected static boolean hasPluginInstalled() {
        String installDir = getPmInstallDirectoryPath();
        File f = new File(installDir);
        if (f.exists() && f.isDirectory()) {
            for (File file : f.listFiles()) {
                if (!file.isDirectory() && file.getName().toLowerCase().indexOf("software") == 0) {
                    return true;
                }
            }
        }
        return false;
    }

	/**
	 * The public method used to send the keystroke object information to the plugin manager
	 * @param keystrokeCount
	 */
	public void sendKeystrokeData(SoftwareCoKeystrokeCount keystrokeCount) {

		final IWorkbench workbench = PlatformUI.getWorkbench();

		workbench.getDisplay().asyncExec(new Runnable() {
			public void run() {
				
				sessionMgr.sendOfflineData();
        
				String projectName = keystrokeCount.getProject().getName();
				KeystrokeDataSendTask sendTask = new KeystrokeDataSendTask(keystrokeCount);
				
				Future<HttpResponse> response = SoftwareCoUtils.executorService.submit(sendTask);
				
				boolean isRequestOk = true;
				
				//
				// Handle the Future if it exist
				//
				if ( response != null ) {
					HttpResponse httpResponse = null;
					try {
						httpResponse = response.get();
						
						if (httpResponse != null) {
							//
							// Handle the response from the Future (consume the entity to prevent connection pool leak/timeout)
							//
							String entityResult = "";
							if (httpResponse.getEntity() != null) {
								try {
									entityResult = EntityUtils.toString(httpResponse.getEntity());
								} catch (ParseException | IOException e) {
									SoftwareCoLogger.error("Software.com: Unable to parse the non-null plugin manager response.", e);
								}
							}
							
							//
							// If it's a response status of anything other than the 200 series then the POST request failed
							//
							int responseStatus = httpResponse.getStatusLine().getStatusCode();
							if (responseStatus >= 300) {
								SoftwareCoLogger.error("Software.com: Unable to send the keystroke payload, "
										+ "response: [status: " + responseStatus + ", entityResult: '" + entityResult + "']");
								isRequestOk = false;
							}
						}
						
					} catch (InterruptedException | ExecutionException e) {
						SoftwareCoLogger.error("Software.com: Unable to get the response from the http request.", e);
						isRequestOk = false;
					}
				}
				
				if (!isRequestOk) {
					// save the data offline
					String payload = SoftwareCo.gson.toJson(keystrokeCount);
					sessionMgr.storePayload(payload);
					sessionMgr.chekUserAuthenticationStatus();
				}
				
				// reset the data
				SoftwareCoKeystrokeManager.getInstance().resetData(projectName);
				
			}
		});
	}
	
	/***
	 * ExecutiveService Callable task to make the HTTP Post request
	 * to the plugin manager.
	 * 
	 * Payload INFO:
	 * 
	 * POST http://localhost:19234/api/v1/data
	 * 
	 * {
	 *     // source file edited in the chunk
	 *     source: {‘/path/to/file1’: {'keys': 10}}
	 *     // constant
	 *     type: ‘Keystrokes’,
	 *     // time the chunk is sent in *seconds* since epoch
	 *     timestamp: 1234567890,
	 *     // keystrokes count
	 *     data: '10',
	 *     // measure start time in *seconds* since epoch
	 *     start: 1234567890,
	 *     // measure start time in *seconds* since epoch, BE expects a minute long
	 *     end: 1234567890,
	 *     project: {
	 *         // project name
	 *         name: ‘project_name’,
	 *         // project path
	 *         directory: ‘path/to/project’
	 *     },
	 *     pluginId: 3
	 * }
	 * data: {
        source: {},
        type: 'string',
        timestamp: 'number',
        data: 'string',
        start: 'number',
        end: 'number',
        project: {
            directory: 'string',
            name: 'string'
        },
        pluginId: 'number'
    }
	***/
	protected class KeystrokeDataSendTask implements Callable<HttpResponse> {
		
		private SoftwareCoKeystrokeCount keystrokeCount;
		
		public KeystrokeDataSendTask(SoftwareCoKeystrokeCount keystrokeCount) {
			this.keystrokeCount = keystrokeCount;
		}

		@Override
		public HttpResponse call() throws Exception {
			//
			// create the json string
			//
			String kpmData = SoftwareCo.gson.toJson(keystrokeCount);
			HttpPost request = null;
			try {

				//
				// Add the json body to the outgoing post request
				//
				request = new HttpPost(SoftwareCoUtils.api_endpoint + "/data");
				String jwtToken = SoftwareCoSessionManager.getItem("jwt");
                // we need the header, but check if it's null anyway
                if (jwtToken != null) {
                    request.addHeader("Authorization", jwtToken);
                }
				StringEntity params = new StringEntity(kpmData);
				request.addHeader("Content-type", "application/json");
				request.setEntity(params);

				//
				// Send the POST request
				//
				SoftwareCoUtils.logApiRequest(request, kpmData);
				HttpResponse response = SoftwareCoUtils.httpClient.execute(request);
				//
				// Return the response
				//
				return response;
			} catch (Exception e) {
				SoftwareCoLogger.error("Software.com: Unable to send the keystroke payload request.", e);
			} finally {
				if (request != null) {
					request.releaseConnection();
				}
			}
			
			return null;
		}
	}
	
	protected class DownloadPMHandler implements Callable<Void> {
		
		public DownloadPMHandler() {
			//
		}

		@Override
		public Void call() throws Exception {
			downloadingPM = true;
			
	        String saveAs = getDownloadFilePathName();
	        SoftwareCoLogger.info("Downloading Software plugin manager");

	        URL downloadUrl = null;
	        try {
	            downloadUrl = new URL(getFileUrl());
	        } catch (MalformedURLException e) {}

	        ReadableByteChannel rbc = null;
	        FileOutputStream fos = null;
	        try {
	            rbc = Channels.newChannel(downloadUrl.openStream());
	            fos = new FileOutputStream(saveAs);
	            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
	            fos.close();
	            SoftwareCoLogger.info("Completed download of the Software plugin manager");
	            Desktop.getDesktop().open(new File(saveAs));
	        } catch (Exception e) {
	        		SoftwareCoLogger.error("Failed to download the Software plugin manager, reason: " + e.toString());
	        }
	        downloadingPM = false;

			return null;
		}
	}
}
