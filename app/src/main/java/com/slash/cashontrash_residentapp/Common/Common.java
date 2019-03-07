package com.slash.cashontrash_residentapp.Common;

import com.slash.cashontrash_residentapp.Remote.FCMClient;
import com.slash.cashontrash_residentapp.Remote.IFCMService;

public class Common {

    public static final String collector_tbl = "TrashCollectors";
    public static final String user_collector_tbl = "CollectorsInformation";
    public static final String user_resident_tbl = "ResidentsInformation";
    public static final String trashpickup_request_tbl = "TrashPickRequest";
    public static final String token_tbl = "Tokens";


    public static final String fcmURL = "https://fcm.googleapis.com/";



    public static IFCMService getIFCMService(){
        return FCMClient.getClient(fcmURL).create(IFCMService.class);

    }
}
