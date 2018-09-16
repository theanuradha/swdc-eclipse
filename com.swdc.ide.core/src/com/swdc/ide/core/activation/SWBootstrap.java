package com.swdc.ide.core.activation;

import org.eclipse.ui.IStartup;

import com.swdc.ide.core.SWCorePlugin;

public class SWBootstrap implements IStartup
{

    public void earlyStartup()
    {
        SWCorePlugin.getDefault();
    }

}
