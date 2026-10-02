package com.media.ibox.service;

import com.media.ibox.dlna.DLNAServiceConfiguration;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DLNACastService extends AndroidUpnpServiceImpl {

    @Override
    public void onCreate() {
        super.onCreate();
        upnpService.startup();
    }

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new DLNAServiceConfiguration() {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                return new ServiceType[]{
                        new UDAServiceType("AVTransport", 1),
                        new UDAServiceType("RenderingControl", 1)
                };
            }
        };
    }
}
