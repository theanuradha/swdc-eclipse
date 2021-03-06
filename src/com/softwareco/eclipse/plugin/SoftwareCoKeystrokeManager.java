/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import java.util.ArrayList;
import java.util.List;

public class SoftwareCoKeystrokeManager {

	private static SoftwareCoKeystrokeManager instance = null;
	
	// KeystrokeCount cache metadata
	List<KeystrokeCountWrapper> keystrokeCountWrapperList = new ArrayList<KeystrokeCountWrapper>();

	/**
	 * Protected constructor to defeat instantiation
	 */
	protected SoftwareCoKeystrokeManager() {
		//
	}

	public static SoftwareCoKeystrokeManager getInstance() {
		if (instance == null) {
			instance = new SoftwareCoKeystrokeManager();
		}
		return instance;
	}
	
	public void addKeystrokeWrapperIfNoneExists(SoftwareCoProject project) {
        if (this.getKeystrokeWrapper(project.getName()) == null) {
            KeystrokeCountWrapper wrapper = new KeystrokeCountWrapper();
            wrapper.setProjectName(project.getName());
            keystrokeCountWrapperList.add(wrapper);
        }
    }

    public void resetData(String projectName) {
        for (KeystrokeCountWrapper wrapper : keystrokeCountWrapperList) {
            if (wrapper.getProjectName() != null && projectName.equals(wrapper.getProjectName())) {
                wrapper.getKeystrokeCount().resetData();
                break;
            }
        }
    }

    public SoftwareCoKeystrokeCount getKeystrokeCount(String projectName) {
        for (KeystrokeCountWrapper wrapper : keystrokeCountWrapperList) {
            if (wrapper.getProjectName() != null && projectName.equals(wrapper.getProjectName())) {
                return wrapper.getKeystrokeCount();
            }
        }
        return null;
    }

    public void setKeystrokeCount(String projectName, SoftwareCoKeystrokeCount keystrokeCount) {
        for (KeystrokeCountWrapper wrapper : keystrokeCountWrapperList) {
            if (wrapper.getProjectName() != null && projectName.equals(wrapper.getProjectName())) {
                wrapper.setKeystrokeCount(keystrokeCount);
                return;
            }
        }

        // didn't find it, time to create a wrapper
        KeystrokeCountWrapper wrapper = new KeystrokeCountWrapper();
        wrapper.setKeystrokeCount(keystrokeCount);
        wrapper.setLastUpdateTime(System.currentTimeMillis());
        wrapper.setProjectName(projectName);
        keystrokeCountWrapperList.add(wrapper);
    }

    public KeystrokeCountWrapper getKeystrokeWrapper(String projectName) {
        for (KeystrokeCountWrapper wrapper : keystrokeCountWrapperList) {
            if (wrapper.getProjectName() != null && projectName.equals(wrapper.getProjectName())) {
                return wrapper;
            }
        }
        return null;
    }

    public void processKeystrokeCountForTermination() {
        for (KeystrokeCountWrapper wrapper : keystrokeCountWrapperList) {
            // this will ensure we process the latest keystroke updates
            wrapper.setLastUpdateTime(0l);
        }
    }

    public List<KeystrokeCountWrapper> getKeystrokeCountWrapperList() {
        return this.keystrokeCountWrapperList;
    }
	
	public class KeystrokeCountWrapper {
		// KeystrokeCount cache metadata
		protected SoftwareCoKeystrokeCount keystrokeCount;
		protected String projectName;
		protected long lastUpdateTime = 0; // in millis
		protected int currentTextLength = 0;
		
		public SoftwareCoKeystrokeCount getKeystrokeCount() {
            return keystrokeCount;
        }

        public void setKeystrokeCount(SoftwareCoKeystrokeCount keystrokeCount) {
            this.keystrokeCount = keystrokeCount;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public int getCurrentTextLength() {
            return currentTextLength;
        }

        public void setCurrentTextLength(int currentTextLength) {
            this.currentTextLength = currentTextLength;
        }

	}
}
