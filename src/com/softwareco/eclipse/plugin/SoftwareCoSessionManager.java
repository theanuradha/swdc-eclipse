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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

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
	
	private static long lastTimeAuthenticated = 0;
	
	private Timer authenticationCheckTimer;
	
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
	private static boolean isAuthenticated() {
	    String tokenVal = getItem("token");
	    if (tokenVal == null) {
	        return false;
	    }
	    
	    boolean isOk = SoftwareCoUtils.getResponseInfo(makeApiCall("/users/ping/", false, null)).isOk;
	    if (!isOk) {
	    	lastTimeAuthenticated = -1;
	    } else {
	    	lastTimeAuthenticated = System.currentTimeMillis();
	    }
	    if (!isOk) {
	    	// update the status bar with Sign Up message
	    	SoftwareCoUtils.setStatusLineMessage("⚠️Software.com", "Click to log in to Software.com");
	    }
	    return isOk;
	}
	
	private boolean isServerOnline() {
		return SoftwareCoUtils.getResponseInfo(makeApiCall("/ping", false, null)).isOk;
	}

	public void storePayload(String payload) {
		if (payload == null || payload.length() == 0) {
			return;
		}
		if (SoftwareCo.isWindows()) {
			payload += "\r\n";
		} else {
			payload += "\n";
		}
		String dataStoreFile = getSoftwareDataStoreFile();
		File f = new File(dataStoreFile);
		try {
			Writer output;
			output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
			output.append(payload);
			output.close();
		} catch (Exception e) {
			SoftwareCoLogger.error("Software.com: Error appending to the Software data store file", e);
		}
	}

	public void sendOfflineData() {
	    String dataStoreFile = getSoftwareDataStoreFile();
	    File f = new File(dataStoreFile);

	    if (f.exists()) {
	    		// JsonArray jsonArray = new JsonArray();
	    		StringBuffer sb = new StringBuffer();
		    	try {
		    		FileInputStream fis = new FileInputStream(f);
		    		 
		    		//Construct BufferedReader from InputStreamReader
		    		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		    	 
		    		String line = null;
		    		while ((line = br.readLine()) != null) {
		    			if (line.length() > 0) {
		    				sb.append(line).append(",");
		    			}
		    		}
		    	 
		    		br.close();
		    		
		    		if (sb.length() > 0) {
		    			String payloads = sb.toString();
		    			payloads = payloads.substring(0, payloads.lastIndexOf(","));
		    			payloads = "[" + payloads + "]";
			    		if (SoftwareCoUtils.getResponseInfo(makeApiCall("/data/batch", true, payloads)).isOk) {
			    		
				    		// delete the file
				    		this.deleteFile(dataStoreFile);
			    		}
		    		} else {
		    			SoftwareCoLogger.info("Software.com: No offline data to send");
		    		}
		    	} catch (Exception e) {
		    		SoftwareCoLogger.error("Software.com: Error trying to read and send offline data.", e);
		    	}
	    }
	}

	public static void setItem(String key, String val) {
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
		String jwtToken = getItem("jwt");
		
		String msg = "To see your coding data in Software.com, please log in to your account.";
		if (isOnline && !authenticated && pastThresholdTime && !confirmWindowOpen) {
	        // set the last update time so we don't try to ask too frequently
	        setItem("eclipse_lastUpdateTime", String.valueOf(System.currentTimeMillis()));
	        confirmWindowOpen = true;
	        
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
							new String[] { "Not now", "Log In" }, // dialogButtonLabels
							1 // defaultIndex
					);
					// waits until the user has closed the dialog and returns the dialog's return code
					int selectedButtonIdx = dialog.open();
					dialog.close();
					
					if (selectedButtonIdx == 1) {
						// create the token value
						launchDashboard();
					}
					
					confirmWindowOpen = false;
				}
			});
	        
		}
		
		if (!authenticated || jwtToken == null) {
			SoftwareCoUtils.setStatusLineMessage("⚠️Software.com", msg);
			
			// try again in 10 min
			authenticationCheckTimer = new Timer();
			authenticationCheckTimer.schedule(new ProcessAuthenticationCheck(), 1000 * 60 * 10);
		}
	}
	
	private class ProcessAuthenticationCheck extends TimerTask {
		public void run() {
			chekUserAuthenticationStatus();
		}
	}

	/**
	 * Checks the last time we've updated the session info
	 */
	private boolean isPastTimeThreshold() {
	    String lastUpdateTimeStr = getItem("eclipse_lastUpdateTime");
	    Long lastUpdateTime = (lastUpdateTimeStr != null) ? Long.valueOf(lastUpdateTimeStr) : null;
	    if (lastUpdateTime != null &&
	        System.currentTimeMillis() - lastUpdateTime.longValue() < MILLIS_PER_HOUR) {
	        return false;
	    }
	    return true;
	}
	
	public static void checkTokenAvailability() {
		String tokenVal = getItem("token");
		
		if (tokenVal == null || tokenVal.equals("")) {
			return;
		}
		
		JsonObject responseData = SoftwareCoUtils.getResponseInfo(
				makeApiCall("/users/plugin/confirm?token=" + tokenVal, false, null)).jsonObj;
		if (responseData != null) {
			// update the jwt, user and eclipse_lastUpdateTime
			setItem("jwt", responseData.get("jwt").getAsString());
			setItem("user", responseData.get("user").getAsString());
			setItem("eclipse_lastUpdateTime", String.valueOf(System.currentTimeMillis()));
		} else {
			// check again in 2 minutes
			new Thread(() -> {
		        try {
		            Thread.sleep(1000 * 120);
		            checkTokenAvailability();
		        }
		        catch (Exception e){
		            System.err.println(e);
		        }
		    }).start();
		}
	}
	
	public void fetchDailyKpmSessionInfo() {
		long fromSeconds = Math.round(System.currentTimeMillis() / 1000);
		// make an async call to get the kpm info
		JsonObject jsonObj = SoftwareCoUtils.getResponseInfo(
				makeApiCall("/sessions?from=" + fromSeconds +"&summary=true", false, null)).jsonObj;
		if (jsonObj != null) {
			boolean inFlow = true;
			if (jsonObj.has("inFlow")) {
				inFlow = jsonObj.get("inFlow").getAsBoolean();
			}
			String sessionTimeIcon = "";
			float currentSessionGoalPercent = 0;
			if (jsonObj.has("currentSessionGoalPercent")) {
				currentSessionGoalPercent = jsonObj.get("currentSessionGoalPercent").getAsFloat();
				if (currentSessionGoalPercent > 0) {
					if (currentSessionGoalPercent < 0.45) {
                        sessionTimeIcon = "❍";
                    } else if (currentSessionGoalPercent < 0.70) {
                        sessionTimeIcon = "◒";
                    } else if (currentSessionGoalPercent < 0.95) {
                        sessionTimeIcon = "◍";
                    } else {
                        sessionTimeIcon = "●";
                    }
				}
			}
			int currentSessionKpm = 0;
			if (jsonObj.has("currentSessionKpm")) {
				currentSessionKpm = jsonObj.get("currentSessionKpm").getAsInt();
			}
			long currentSessionMinutes = 0;
			if (jsonObj.has("currentSessionMinutes")) {
				currentSessionMinutes = jsonObj.get("currentSessionMinutes").getAsLong();
			}
            String sessionTime = "";
            if (currentSessionMinutes == 60) {
                sessionTime = "1 hr";
            } else if (currentSessionMinutes > 60) {
            		sessionTime =  String.format("%.2f", (currentSessionMinutes / 60)) + " hrs";
            } else if (currentSessionMinutes == 1) {
                sessionTime = "1 min";
            } else {
                sessionTime = currentSessionMinutes + " min";
            }
            if (currentSessionKpm > 0 || currentSessionMinutes > 0) {
            	String sessionMsg = (sessionTime.equals("")) ? sessionTime : sessionTimeIcon + " " + sessionTime;
            	String statusMsg = String.valueOf(currentSessionKpm) + " KPM, " + sessionMsg;
            	if (inFlow) {
            		statusMsg = "🚀" + " " + statusMsg;
            	}
                SoftwareCoUtils.setStatusLineMessage("<S> " + statusMsg,
                        "Click to see more from Software.com");
            } else {
            	SoftwareCoUtils.setStatusLineMessage("Software.com", "Click to see more from Software.com");
            }
		} else {
			SoftwareCoUtils.setStatusLineMessage("⚠️Software.com", "Click to log in to Software.com");
			chekUserAuthenticationStatus();
		}
	}
	
	public static void launchDashboard() {
		
		String url = SoftwareCoUtils.launch_url;
		
		// create the token value
		String token = getItem("token");
		String jwt = getItem("jwt");
		boolean addToken = false;
		if (token == null || token.equals("")) {
			token = SoftwareCoUtils.generateToken();
			setItem("token", token);
			addToken = true;
		} else if (jwt == null || jwt.equals("") || !isAuthenticated()) {
			addToken = true;
		}
		if (addToken) {
			url += "/onboarding?token=" + token;
			
			// checkTokenAvailability in a minute
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
			HttpUriRequest req = null;
			try {
				
				HttpResponse response = null;
				
				if (!isPost) {
					req = new HttpGet(SoftwareCoUtils.api_endpoint + "" + this.api);
				} else {
					req = new HttpPost(SoftwareCoUtils.api_endpoint + "" + this.api);

					if (payload != null) {
						//
						// add the json payload
						//
						StringEntity params = new StringEntity(payload);
						((HttpPost)req).setEntity(params);
					}
				}
				
				String jwtToken = getItem("jwt");
				// obtain the jwt session token if we have it
				if (jwtToken != null) {
					req.addHeader("Authorization", jwtToken);
				}
				
				req.addHeader("Content-type", "application/json");
				
				// execute the request
				SoftwareCoUtils.logApiRequest(req, payload);
				response = SoftwareCoUtils.httpClient.execute(req);
				
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