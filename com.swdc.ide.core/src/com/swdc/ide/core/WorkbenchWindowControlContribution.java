package com.swdc.ide.core;

import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.swdc.ide.core.ui.SoftwareCoSessionManager;

public class WorkbenchWindowControlContribution extends org.eclipse.ui.menus.WorkbenchWindowControlContribution {

	private static WorkbenchWindowControlContribution ITEM;

	private CLabel label;

	Listener listener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			SoftwareCoSessionManager.launchDashboard();
		}
	};

	private String errorText;

	private String errorDetail;

	private String tooltip = "";

	private String text = "                                      ";

	private String iconName;

	public WorkbenchWindowControlContribution() {
		ITEM = this;
	}

	public WorkbenchWindowControlContribution(String id) {
		super(id);
		ITEM = this;
	}

	public static WorkbenchWindowControlContribution get() {
		return ITEM;
	}

	public String getErrorText() {
		return errorText;
	}

	public void setErrorText(String errorText) {
		this.errorText = errorText;
	}

	public String getErrorDetail() {
		return errorDetail;
	}

	public void setErrorDetail(String errorDetail) {
		this.errorDetail = errorDetail;
	}

	public String getTooltip() {
		return tooltip;
	}

	public void setTooltip(String tooltip) {
		this.tooltip = tooltip;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getIconName() {
		return iconName;
	}

	public void setIconName(String iconName) {
		this.iconName = iconName;
	}

	@Override
	protected Control createControl(Composite parent) {

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new FillLayout());
		new Label(composite, SWT.SEPARATOR);
		label = new CLabel(composite, SWT.SHADOW_NONE);
		update();

		label.addListener(SWT.MouseDown, listener);

		return composite;
	}

	public void update() {
		if (label != null && !label.isDisposed()) {
			Display display = label.getDisplay();
			if (errorText != null && errorText.length() > 0) {
				label.setForeground(JFaceColors.getErrorText(display));
				label.setText(escape(errorText));
				label.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK));
				if (errorDetail != null)
					label.setToolTipText(escape(errorDetail));
				else if (tooltip != null)
					label.setToolTipText(escape(tooltip));
				else
					label.setToolTipText(null);

			} else {
				label.setForeground(label.getParent().getForeground());
				label.setText(escape(text));

				if (iconName != null) {
					ImageDescriptor imgDescriptor = SWCoreImages.create("icons/", iconName + ".gif");
					try {
						if (label.getImage() != null)
							label.getImage().dispose();

						label.setImage(SWCoreImages.getImage(imgDescriptor));
					} catch (Exception e) {
						SWCoreLog.log(e);
					}
				}

				if (tooltip != null)
					label.setToolTipText(escape(tooltip));
				else
					label.setToolTipText(null);
			}
		}
		label.getParent().layout(true);
	}

	private String escape(String text) {
		if (text == null)
			return text;
		return LegacyActionTools.escapeMnemonics(text);
	}

}
