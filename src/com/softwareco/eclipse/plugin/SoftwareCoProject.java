/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

public class SoftwareCoProject {

	private String name;
	private String directory;
	
	public SoftwareCoProject(String name, String directory) {
		this.name = name;
		this.directory = directory;
	}
	
	public void resetData() {
        // intentional for now
    }

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDirectory() {
		return directory;
	}
	public void setDirectory(String directory) {
		this.directory = directory;
	}
	@Override
	public String toString() {
		return "SoftwareCoProject [name=" + name + ", directory=" + directory + "]";
	}
	
}
