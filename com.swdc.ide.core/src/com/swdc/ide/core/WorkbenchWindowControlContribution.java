package com.swdc.ide.core;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class WorkbenchWindowControlContribution extends org.eclipse.ui.menus.WorkbenchWindowControlContribution {

	public WorkbenchWindowControlContribution() {
	}

	public WorkbenchWindowControlContribution(String id) {
		super(id);
	}

	@Override
	protected Control createControl(Composite parent) {
	
		Composite composite = new Composite(parent, SWT.NONE);
		
       return composite;
	}

}
