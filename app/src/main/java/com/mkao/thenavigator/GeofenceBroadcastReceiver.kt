package com.mkao.thenavigator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver :BroadcastReceiver (){
    override fun onReceive(context:Context?, intent: Intent?) {
        val geofencingOccurence = intent?.let { GeofencingEvent.fromIntent(it) }
        if (geofencingOccurence != null) {
            if (geofencingOccurence.hasError()){
                Toast.makeText(context,context?.getText(R.string.geofence_error),Toast.LENGTH_LONG).show()
                return
            }
        }
        //transitions
        val transition = geofencingOccurence?.geofenceTransition
        if (transition==Geofence.GEOFENCE_TRANSITION_ENTER)
            context?.sendBroadcast(Intent("PLACE ARRIVED"))
    }

}
