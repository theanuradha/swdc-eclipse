/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.eclipse.plugin;

import org.eclipse.core.runtime.Status;

public class SoftwareCoLogger {

	public static void info(String msg) {
		logMessage(msg, Status.INFO, null);
	}

	public static void debug(String msg) {
		logMessage(msg, Status.INFO, null);
	}

	public static void error(String msg) {
		logMessage(msg, Status.ERROR, null);
	}

	public static void error(String msg, Exception e) {
		logMessage(msg, Status.ERROR, e);
	}

	public static void warn(String msg, Exception e) {
		logMessage(msg, Status.WARNING, e);
	}

	public static void warn(Exception e) {
		logMessage("Warning", Status.WARNING, e);
	}

	public static void logMessage(String msg, int level, Exception e) {
		if (SoftwareCo.logInstance != null) {
			
			if ( msg == null || msg.trim().length() == 0 ) {
				msg = "";
				if (e != null) {
					msg += "Plugin Error:\n";
				}
			}
			msg = msg.trim();
			
			//
			// Add the subset error message to the msg, or make it the msg if
			// that's blank.
			//
			if ( e != null ) {
				StringBuilder sb = new StringBuilder();
				StackTraceElement[] ste = e.getStackTrace();
				if ( ste != null && ste.length > 0 ) {
					for ( StackTraceElement el : ste ) {
						if (el.getClassName().indexOf(SoftwareCo.PLUGIN_ID) != -1) {
							sb.append(el.toString()).append("\n");
						}
					}
				}
				
				if ( e.getCause() != null && e.getCause().getMessage() != null & e.getCause().getMessage().length() > 0 ) {
					msg += "\nREASON: " + e.getCause().getMessage();
				} else if (e.getMessage() != null && e.getMessage().length() > 0) {
					msg += "\nREASON: " + e.getMessage();
				}
				
				msg += "\n" + sb.toString();
				e = null;
			}
			
			SoftwareCo.logInstance.log(new Status(level, SoftwareCo.PLUGIN_ID, Status.OK, msg, e));
		}
	}
}