/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 
 * Manages the plugin to software.com session
 *
 */
public class SoftwareCoSessionManager {
	
	private static SoftwareCoSessionManager instance = null;
	
	private static long MILLIS_PER_HOUR = 1000 * 60 * 60;
	private static int LONG_THRESHOLD_HOURS = 24;
	
	private boolean confirmWindowOpen = false;
	
	private long lastTimeAuthenticated = 0;
	
	public static SoftwareCoSessionManager getInstance() {
		if (instance == null) {
			instance = new SoftwareCoSessionManager();
		}
		return instance;
	}
	
	private static String getSoftwareDir() {
	    String softwareDataDir = SoftwareCo.getUserHomeDir();
	    if (SoftwareCo.isWindows()) {
	        softwareDataDir += "\\.software";
	    } else {
	        softwareDataDir += "/.software";
	    }
	    
	    File f = new File(softwareDataDir);
	    if (!f.exists()) {
	    		// make the directory
	    		f.mkdirs();
	    }

	    return softwareDataDir;
	}
	
	private static String getSoftwareSessionFile() {
	    String file = getSoftwareDir();
	    if (SoftwareCo.isWindows()) {
	        file += "\\session.json";
	    } else {
	        file += "/session.json";
	    }
	    return file;
	}

	private String getSoftwareDataStoreFile() {
	    String file = getSoftwareDir();
	    if (SoftwareCo.isWindows()) {
	        file += "\\data.json";
	    } else {
	        file += "/data.json";
	    }
	    return file;
	}

	/**
	 * User session will have...
	 * { user: user, jwt: jwt }
	 */
	private boolean isAuthenticated() {
	    String tokenVal = getItem("token");
	    if (tokenVal == null) {
	        return false;
	    }
	    
	    if (lastTimeAuthenticated > 0 && System.currentTimeMillis() - lastTimeAuthenticated < 10000) {
	    		return true;
	    }
	    
	    boolean isOk = SoftwareCoUtils.getResponseInfo(makeApiCall("/users/ping/", false, null)).isOk;
	    if (!isOk) {
	    		lastTimeAuthenticated = -1;
	    } else {
	    		lastTimeAuthenticated = System.currentTimeMillis();
	    }
	    if (!isOk) {
	    		// update the status bar with Sign Up message
	    		SoftwareCoUtils.setStatusLineMessage("Software.com", "Double click to sign up to Software.com");
	    }
	    return isOk;
	}
	
	private boolean isServerOnline() {
		return SoftwareCoUtils.getResponseInfo(makeApiCall("/ping", false, null)).isOk;
	}

	public void storePayload(String payload) {
		String dataStoreFile = getSoftwareDataStoreFile();
		File f = new File(dataStoreFile);
		if (f.exists()) {
			try {
				Writer output;
				output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
				output.append(payload);
				output.close();
			} catch (Exception e) {
				SoftwareCoLogger.error("Software.com: Error appending to the Software data store file", e);
			}
		}
	}

	public void sendOfflineData() {
	    String dataStoreFile = getSoftwareDataStoreFile();
	    File f = new File(dataStoreFile);

	    if (f.exists()) {
	    		JsonArray jsonArray = new JsonArray();
		    	try {
		    		FileInputStream fis = new FileInputStream(f);
		    		 
		    		//Construct BufferedReader from InputStreamReader
		    		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		    	 
		    		String line = null;
		    		while ((line = br.readLine()) != null) {
		    			if (line.length() > 0) {
		    				// payloads.add(line);
		    				JsonObject jsonObj = SoftwareCo.jsonParser.parse(line).getAsJsonObject();
		    				jsonArray.add(jsonObj);
		    			}
		    		}
		    	 
		    		br.close();
		    		
		    		if (SoftwareCoUtils.getResponseInfo(makeApiCall("/data/batch", true, jsonArray.getAsString())).isOk) {
		    		
			    		// delete the file
			    		this.deleteFile(dataStoreFile);
		    		}
		    	} catch (Exception e) {
		    		SoftwareCoLogger.error("Software.com: Error trying to read and send offline data.", e);
		    	}
	    }
	}

	private static void setItem(String key, String val) {
		JsonObject jsonObj = getSoftwareSessionAsJson();
	    jsonObj.addProperty(key, val);

	    String content = jsonObj.toString();

	    String sessionFile = getSoftwareSessionFile();
	    
	    try {
		    Writer output = new BufferedWriter(new FileWriter(sessionFile));
		    output.write(content);
		    output.close();
	    } catch (Exception e) {
	    		SoftwareCoLogger.error("Software.com: Failed to write the key value pair (" + key + ", " + val + ") into the session.", e);
	    }
	}

	public static String getItem(String key) {
		JsonObject jsonObj = getSoftwareSessionAsJson();
		if (jsonObj != null && jsonObj.has(key)) {
			return jsonObj.get(key).getAsString();
		}
		return null;
	}

	private static JsonObject getSoftwareSessionAsJson() {
	    JsonObject data = null;

	    String sessionFile = getSoftwareSessionFile();
	    File f = new File(sessionFile);
	    if (f.exists()) {
	    		try {
		    		byte[] encoded = Files.readAllBytes(Paths.get(sessionFile));
		    		String content = new String(encoded, Charset.defaultCharset());
		    		if (content != null) {
		    			// json parse it
		    			data = SoftwareCo.jsonParser.parse(content).getAsJsonObject();
		    		}
	    		} catch (Exception e) {
	    			SoftwareCoLogger.error("Software.com: Error trying to read and json parse the session file.", e);
	    		}
	    }
	    return (data == null) ? new JsonObject() : data;
	}

	private void deleteFile(String file) {
		File f = new File(file);
	    // if the file exists, delete it
	    if (f.exists()) {
	        f.delete();
	    }
	}

	public void chekUserAuthenticationStatus() {
		boolean isOnline = isServerOnline();
		boolean authenticated = isAuthenticated();
		boolean pastThresholdTime = isPastTimeThreshold();
		
		if (isOnline && !authenticated && pastThresholdTime && !confirmWindowOpen) {
	        // set the last update time so we don't try to ask too frequently
	        setItem("eclipse_lastUpdateTime", String.valueOf(System.currentTimeMillis()));
	        confirmWindowOpen = true;
	        
	        String msg = "To see insights into how you code, please sign in to Software.com.";
	        
	        final String dialogMsg = msg;
	        
	        final IWorkbench workbench = PlatformUI.getWorkbench();

	        // show the launch browser to login dialogs
			workbench.getDisplay().asyncExec(new Runnable() {
				public void run() {
			        MessageDialog dialog = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), // parentShell
							"Software", // dialogTitle
							null, // dialogTitleImage
							dialogMsg, // dialogMessage
							MessageDialog.INFORMATION, // dialogImageType
							new String[] { "Not now", "Login" }, // dialogButtonLabels
							1 // defaultIndex
					);
					// waits until the user has closed the dialog and returns the dialog's return code
					int selectedButtonIdx = dialog.open();
					dialog.close();
					
					if (selectedButtonIdx == 1) {
						// create the token value
						String token = generateToken();
						setItem("token", token);
						// launch the browser with the login view
						launchWebUrl(SoftwareCoUtils.launch_url + "/login?token=" + token);
						
						// checkTokenAvailability
						new Thread(() -> {
					        try {
					            Thread.sleep(1000 * 30);
					            checkTokenAvailability();
					        }
					        catch (Exception e){
					            System.err.println(e);
					        }
					    }).start();
					}
					
					confirmWindowOpen = false;
				}
			});
	        
		}
	}

	/**
	 * Checks the last time we've updated the session info
	 */
	private boolean isPastTimeThreshold() {
	    String lastUpdateTimeStr = getItem("eclipse_lastUpdateTime");
	    Long lastUpdateTime = (lastUpdateTimeStr != null) ? Long.valueOf(lastUpdateTimeStr) : null;
	    if (lastUpdateTime != null &&
	        System.currentTimeMillis() - lastUpdateTime.longValue() < MILLIS_PER_HOUR * LONG_THRESHOLD_HOURS) {
	        return false;
	    }
	    return true;
	}
	
	public static void checkTokenAvailability() {
		String tokenVal = getItem("token");
		
		JsonObject responseData = SoftwareCoUtils.getResponseInfo(
				makeApiCall("/users/plugin/confirm?token=" + tokenVal, false, null)).jsonObj;
		if (responseData != null) {
			// update the jwt, user and eclipse_lastUpdateTime
			setItem("jwt", responseData.get("jwt").getAsString());
			setItem("user", responseData.get("user").getAsString());
			setItem("eclipse_lastUpdateTime", String.valueOf(System.currentTimeMillis()));
		} else {
			// check again in a minute
			new Thread(() -> {
		        try {
		            Thread.sleep(1000 * 60);
		            checkTokenAvailability();
		        }
		        catch (Exception e){
		            System.err.println(e);
		        }
		    }).start();
		}
	}

	private String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }
	
	public void fetchDailyKpmSessionInfo() {
		if (!isAuthenticated()) {
	        SoftwareCoLogger.info("Software.com: Not authenticated to fetch KPM info, trying again later");
	        return;
	    }
		long fromSeconds = Math.round(System.currentTimeMillis() / 1000);
		// make an async call to get the kpm info
		JsonObject jsonObj = SoftwareCoUtils.getResponseInfo(
				makeApiCall("/sessions?from=" + fromSeconds +"&summary=true", false, null)).jsonObj;
		if (jsonObj != null) {
			int avgKpm = 0;
			if (jsonObj.has("kpm")) {
				avgKpm = jsonObj.get("kpm").getAsInt();
			}
			long totalMin = 0;
			if (jsonObj.has("minutesTotal")) {
				totalMin = jsonObj.get("minutesTotal").getAsLong();
			}
            String sessionTime = "";
            if (totalMin == 60) {
                sessionTime = "1 hr";
            } else if (totalMin > 60) {
                // sessionTime = (totalMin / 60).toFixed(2) + " hrs";
            		sessionTime =  String.format("%.2f", (totalMin / 60)) + " hrs";
            } else if (totalMin == 1) {
                sessionTime = "1 min";
            } else {
                sessionTime = totalMin + " min";
            }
            if (avgKpm > 0 || totalMin > 0) {
            		SoftwareCoUtils.setStatusLineMessage(avgKpm + " KPM, " + sessionTime, "Double click to see more from Software.com");
            } else {
            		SoftwareCoUtils.setStatusLineMessage("Software.com", "Click to see more from Software.com");
            }
		}
	}
	
	private void launchWebUrl(String url) {
		try {
			PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(url));
		} catch (PartInitException | MalformedURLException e) {
			SoftwareCoLogger.error("Failed to launch the url: " + url, e);
		}
	}
	
	private static HttpResponse makeApiCall(String api, boolean isPost, String payload) {
        
		SessionManagerHttpClient sendTask = new SessionManagerHttpClient(api, isPost, payload);
		
		Future<HttpResponse> response = SoftwareCoUtils.executorService.submit(sendTask);
		
		//
		// Handle the Future if it exist
		//
		if ( response != null ) {
			
			HttpResponse httpResponse = null;
			try {
				httpResponse = response.get();
				
				if (httpResponse != null) {
					return httpResponse;
				}
				
			} catch (InterruptedException | ExecutionException e) {
				SoftwareCoLogger.error("Software.com: Unable to get the response from the http request.", e);
			}
		}
		return null;
	}
	
	protected static class SessionManagerHttpClient implements Callable<HttpResponse> {
		
		private String payload = null;
		private String api = null;
		private boolean isPost = false;
		
		public SessionManagerHttpClient(String api, boolean isPost, String payload) {
			this.payload = payload;
			this.isPost = isPost;
			this.api = api;
		}

		@Override
		public HttpResponse call() throws Exception {
			HttpPost postRequest = null;
			HttpGet getRequest = null;
			try {
				
				HttpResponse response = null;
				String jwtToken = getItem("jwt");

				if (!isPost) {
					getRequest = new HttpGet(SoftwareCoUtils.api_endpoint + "" + this.api);
					getRequest.addHeader("Content-type", "application/json");
					
					// obtain the jwt session token if we have it
					if (jwtToken != null) {
						getRequest.addHeader("Authorization", jwtToken);
					}
					//
					// Send the GET request
					//
					response = SoftwareCoUtils.httpClient.execute(getRequest);
				} else {
					postRequest = new HttpPost(SoftwareCoUtils.api_endpoint + "" + this.api);
					postRequest.addHeader("Content-type", "application/json");
					
					// obtain the jwt session token if we have it
					if (jwtToken != null) {
						postRequest.addHeader("Authorization", jwtToken);
					}
					if (payload != null) {
						//
						// add the json payload
						//
						SoftwareCoLogger.info("Software.com: sending: " + payload);
						StringEntity params = new StringEntity(payload);
						postRequest.setEntity(params);
					}
					//
					// Send the POST request
					//
					response = SoftwareCoUtils.httpClient.execute(postRequest);
				}
				//
				// Return the response
				//
				return response;
			} catch (Exception e) {
				SoftwareCoLogger.error("Software.com: Unable to make api request.", e);
			}
			
			return null;
		}
	}
	
}