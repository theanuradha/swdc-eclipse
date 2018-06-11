/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;


import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SoftwareCoKeystrokeCount {

	// event type
	private String type = "Events";
	// sublime = 1, vs code = 2, eclipse = 3, intelliJ = 4,
	// visual studio = 6, atom = 7
	private int pluginId = 3;
	
	// non-hardcoded attributes
	private JsonObject source = new JsonObject();
	private String data = "0"; // keystroke count
	private long start;
	private long end;
	private SoftwareCoProject project;
	private String version = "0.1.0";
	
	public SoftwareCoKeystrokeCount() {
		this.start = Math.round(System.currentTimeMillis() / 1000);
		if (SoftwareCo.VERSION != null) {
			this.version = SoftwareCo.VERSION;
		}
	}
	
	public void resetData() {
        this.data = "0";
        this.source = new JsonObject();
        if (this.project != null) {
            this.project.resetData();
        }
        this.start = Math.round(System.currentTimeMillis() / 1000);
        this.end = 0L;
    }
	
	public JsonObject getFileInfo(String fileName) {
		if (source.has(fileName)) {
			return source.get(fileName).getAsJsonObject();
        }
		
		// create one and return the one just created
		JsonObject fileInfoData = new JsonObject();
		fileInfoData.addProperty("keys", 0);
        fileInfoData.addProperty("paste", 0);
        fileInfoData.addProperty("open", 0);
        fileInfoData.addProperty("close", 0);
        fileInfoData.addProperty("delete", 0);
        fileInfoData.addProperty("length", 0);
        source.add(fileName, fileInfoData);
        
        return fileInfoData;
    }
	
	public boolean hasData() {
		
		//
		// Check the file info data properties to see if any of those have data
		// keys, open, close, paste

		//
		// Get the entry set of the file info [{fileName: {props...}}...]
		Set<Entry<String, JsonElement>> fileInfoDataSet = this.source.entrySet();
		for ( Entry<String, JsonElement> fileInfoData : fileInfoDataSet ) {
			JsonObject fileinfoDataJsonObj = (JsonObject) fileInfoData.getValue();
			// go through all of the different types of event vals and check if we have an incremented value
			if (this.hasValueDataForProperty(fileinfoDataJsonObj, "keys") ||
					this.hasValueDataForProperty(fileinfoDataJsonObj, "open") ||
					this.hasValueDataForProperty(fileinfoDataJsonObj, "close") ||
					this.hasValueDataForProperty(fileinfoDataJsonObj, "paste") ||
					this.hasValueDataForProperty(fileinfoDataJsonObj, "delete")) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean hasValueDataForProperty(JsonObject fileInfoData, String property) {
		try {
	        int val = fileInfoData.getAsJsonPrimitive(property).getAsInt();
	        if (val > 0) {
	        		return true;
	        }
		} catch (Exception e) {}
		return false;
	}
	
	public String getSource() {
		return SoftwareCo.gson.toJson(source);
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
	public long getStart() {
		return start;
	}
	public void setStart(long start) {
		this.start = start;
	}
	public long getEnd() {
		return end;
	}
	public void setEnd(long end) {
		this.end = end;
	}
	public SoftwareCoProject getProject() {
		return project;
	}
	public void setProject(SoftwareCoProject project) {
		this.project = project;
	}
	public String getType() {
		return type;
	}
	public int getPluginId() {
		return pluginId;
	}
	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return "SoftwareCoKeystrokeCount [type=" + type + ", pluginId=" + pluginId + ", source=" + source + ", data="
				+ data + ", start=" + start + ", end=" + end + ", project=" + project + ", version=" + version + "]";
	}

}
