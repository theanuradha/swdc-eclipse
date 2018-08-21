/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.StatusLineContributionItem;

import com.google.gson.JsonObject;

public class SoftwareCoUtils {

	private final static String PROD_API_ENDPOINT = "https://api.software.com";
	private final static String PROD_URL_ENDPOINT = "https://app.software.com";

	// set the api endpoint to use
	public final static String api_endpoint = PROD_API_ENDPOINT;
	// set the launch url to use
	public final static String launch_url = PROD_URL_ENDPOINT;
	
	private static final String KPM_ITEM_ID = "software.kpm.item";
	
	private final static int EOF = -1;

	public static ExecutorService executorService;
	public static HttpClient httpClient;

	static {
		// initialize the HttpClient
		RequestConfig config = RequestConfig.custom()
				.setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000)
				.setSocketTimeout(10000)
				.build();

		httpClient = HttpClientBuilder
				.create()
				.setDefaultRequestConfig(config)
				.build();

		executorService = Executors.newCachedThreadPool();
	}
	
	public static class HttpResponseInfo {
		public boolean isOk;
		public String jsonStr;
		public JsonObject jsonObj;
	}
	
	public static HttpResponseInfo getResponseInfo(HttpResponse response) {
		HttpResponseInfo responseInfo = new HttpResponseInfo();
		try {
			// get the entity json string
			// (consume the entity so there's no connection leak causing a connection pool timeout)
			String jsonStr = getStringRepresentation(response.getEntity());
			if (jsonStr != null) {
				JsonObject jsonObj = SoftwareCo.jsonParser.parse(jsonStr).getAsJsonObject();
				responseInfo.jsonObj = jsonObj;
				responseInfo.jsonStr = jsonStr;
			}
			responseInfo.isOk = isOk(response);
		} catch (Exception e) {
			SoftwareCoLogger.error("Unable to get http response info.", e);
		}
		return responseInfo;
	}

	private static String getStringRepresentation(HttpEntity res) throws IOException {
		if (res == null) {
			return null;
		}

		InputStream inputStream = res.getContent();

		// Timing information--- verified that the data is still streaming
		// when we are called (this interval is about 2s for a large response.)
		// So in theory we should be able to do somewhat better by interleaving
		// parsing and reading, but experiments didn't show any improvement.
		//

		StringBuffer sb = new StringBuffer();
		InputStreamReader reader;
		reader = new InputStreamReader(inputStream);

		BufferedReader br = new BufferedReader(reader);
		boolean done = false;
		while (!done) {
			String aLine = br.readLine();
			if (aLine != null) {
				sb.append(aLine);
			} else {
				done = true;
			}
		}
		br.close();

		return sb.toString();
	}

	private static boolean isOk(HttpResponse response) {
		if (response == null || response.getStatusLine() == null || response.getStatusLine().getStatusCode() != 200) {
			return false;
		}
		return true;
	}
	
	public static void logApiRequest(HttpUriRequest req, String payload) {
		String jwt = SoftwareCoSessionManager.getItem("jwt");
		SoftwareCoLogger.info("Software.com: executing request "
				+ "[method: " + req.getMethod() + ", URI: " + req.getURI()
				+ ", payload: " + payload + ", jwt: " + jwt + "]");
	}
	
	public static void setStatusLineMessage(
			final String statusMsg,
			final String tooltip) {
		final IWorkbench workbench = PlatformUI.getWorkbench();

		workbench.getDisplay().asyncExec(new Runnable() {
			public void run() {
				IStatusLineManager statusLineManager = null;
				IWorkbenchWindow window = null;
				try {
					window = workbench.getActiveWorkbenchWindow();
					IWorkbenchPage[] workbenchPages = window.getPages();
					if (workbenchPages != null) {
						for (IWorkbenchPage page : workbenchPages) {
							if (page.getActivePart() instanceof IViewPart) {
								statusLineManager = ((IViewPart) page.getActivePart()).getViewSite().getActionBars().getStatusLineManager();
							} else if (page.getActivePart() instanceof IEditorPart) {
								statusLineManager = ((IEditorPart) page.getActivePart()).getEditorSite().getActionBars().getStatusLineManager();
							}
						}
					}
				} catch (Exception e) {
					SoftwareCoLogger.error("Unable to obtain status line manager.", e);
				}
				if (statusLineManager != null) {
					// remove the status kpm item
					IContributionItem contributeItem = statusLineManager.find(KPM_ITEM_ID);
					if (contributeItem != null) {
						statusLineManager.remove(contributeItem);
					}
					
					// create the custom item
					com.softwareco.eclipse.plugin.StatusLineContributionItem kpmItem = null;
					
					kpmItem = new com.softwareco.eclipse.plugin.StatusLineContributionItem(
								KPM_ITEM_ID);
					
					Listener listener = new Listener() {
						@Override
						public void handleEvent(Event event) {
							SoftwareCoSessionManager.launchDashboard();
						}
					};
					kpmItem.addClickListener(listener);
					
					kpmItem.setText(statusMsg);
					kpmItem.setToolTipText(tooltip);
					kpmItem.setVisible(true);

					String firstContribItem = null;
					for (IContributionItem contribItem : statusLineManager.getItems()) {
						if (contribItem instanceof StatusLineContributionItem) {
							if (contribItem.getId().toLowerCase().equals("elementstate")) {
								firstContribItem = contribItem.getId();
							}
						}
					}
					if (firstContribItem != null) {
						statusLineManager.insertBefore(firstContribItem, kpmItem);
					} else {
						statusLineManager.add(kpmItem);
					}

					
					// show the item right away
					statusLineManager.markDirty();
					statusLineManager.update(true);
				}
			}
		});
	}
	
	public static String getCurrentMusicTrack() {
        String script =
                "on buildItunesRecord(appState)\n" +
                    "tell application \"iTunes\"\n" +
                        "set track_artist to artist of current track\n" +
                        "set track_name to name of current track\n" +
                        "set track_genre to genre of current track\n" +
                        "set track_id to database ID of current track\n" +
                        "set json to {genre:track_genre, artist:track_artist, id:track_id, name:track_name, state:appState}\n" +
                    "end tell\n" +
                    "return json\n" +
                "end buildItunesRecord\n" +
                "on buildSpotifyRecord(appState)\n\n" +
                    "tell application \"Spotify\"\n" +
                        "set track_artist to artist of current track\n" +
                        "set track_name to name of current track\n" +
                        "set track_duration to duration of current track\n" +
                        "set track_id to id of current track\n" +
                        "set json to {genre:\"\", artist:track_artist, id:track_id, name:track_name, state:appState}\n" +
                    "end tell\n" +
                    "return json\n" +
                "end buildSpotifyRecord\n\n" +
                "try\n" +
                    "if application \"Spotify\" is running and application \"iTunes\" is not running then\n" +
                        "tell application \"Spotify\" to set spotifyState to (player state as text)\n" +
                        "-- spotify is running and itunes is not\n" +
                        "if (spotifyState is \"paused\" or spotifyState is \"running\") then\n" +
                            "set jsonRecord to buildSpotifyRecord(spotifyState)\n" +
                        "else\n" +
                            "set jsonRecord to {}\n" +
                        "end if\n" +
                    "else if application \"Spotify\" is running and application \"iTunes\" is running then\n" +
                        "tell application \"Spotify\" to set spotifyState to (player state as text)\n" +
                        "tell application \"iTunes\" to set itunesState to (player state as text)\n" +
                        "-- both are running but use spotify as a higher priority\n" +
                        "if spotifyState is \"playing\" then\n" +
                            "set jsonRecord to buildSpotifyRecord(spotifyState)\n" +
                        "else if itunesState is \"playing\" then\n" +
                            "set jsonRecord to buildItunesRecord(itunesState)\n" +
                        "else if spotifyState is \"paused\" then\n" +
                            "set jsonRecord to buildSpotifyRecord(spotifyState)\n" +
                        "else\n" +
                            "set jsonRecord to {}\n" +
                        "end if\n" +
                    "else if application \"iTunes\" is running and application \"Spotify\" is not running then\n" +
                        "tell application \"iTunes\" to set itunesState to (player state as text)\n" +
                        "set jsonRecord to buildItunesRecord(itunesState)\n" +
                    "else\n" +
                        "set jsonRecord to {}\n" +
                    "end if\n" +
                    "return jsonRecord\n" +
                "on error\n" +
                    "return {}\n" +
                "end try";

        String trackInfoStr = eval(script);
        // genre:Alternative, artist:AWOLNATION, id:6761, name:Kill Your Heroes, state:playing
        JsonObject jsonObj = new JsonObject();
        if (trackInfoStr != null && trackInfoStr.indexOf(":") != -1 && trackInfoStr.indexOf(",") != -1) {
            trackInfoStr.trim();
            String[] paramParts = trackInfoStr.split(",");
            for (String paramPart : paramParts) {
                paramPart = paramPart.trim();
                String[] params = paramPart.split(":");
                jsonObj.addProperty(params[0], params[1]);
            }
        }
        return SoftwareCo.gson.toJson(jsonObj);
    }

    /**
     * Execute AppleScript using {@literal osascript} No exceptions are thrown by this method.
     *
     * @param code the code to evaluate
     * @return script result.
     */
    private static String eval(String code) {
        Runtime runtime = Runtime.getRuntime();
        String[] args = { "osascript", "-e", code };

        try {
            Process process = runtime.exec(args);
            process.waitFor();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            InputStream is = process.getInputStream();
            copyLarge(is, baos, new byte[4096]);
            return baos.toString().trim();

        } catch (IOException e) {
        	SoftwareCoLogger.warn(e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {

        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
	
	public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }
}
