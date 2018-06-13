/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonObject;

public class SoftwareCoUtils {

	// public final static String PLUGIN_MGR_ENDPOINT = "http://localhost:19234/api/v1/data";
	private final static String PROD_API_ENDPOINT = "https://api.software.com";
	private final static String PROD_URL_ENDPOINT = "https://alpha.software.com";

	private final static String LOCAL_API_ENDPOINT = "https://localhost:5000";
	private final static String LOCAL_URL_ENDPOINT = "https://localhost:3000";

	// set the api endpoint to use
	public final static String api_endpoint = PROD_API_ENDPOINT;
	// set the launch url to use
	public final static String launch_url = PROD_URL_ENDPOINT;

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
	
	public static void setStatusLineMessage(final String msg) {
		final IWorkbench workbench = PlatformUI.getWorkbench();

		workbench.getDisplay().asyncExec(new Runnable() {
			public void run() {
				IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
				IStatusLineManager statusLineManager = null;
				try {
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
					statusLineManager.setMessage(msg);
				}
			}
		});
	}
}
