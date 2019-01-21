package com.seyahdoo.pmdeck

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import com.danimahardhika.cafebar.CafeBar
import com.danimahardhika.cafebar.CafeBarTheme
import kotlinx.android.synthetic.main.activity_main.*
import android.net.wifi.WifiManager


class MainActivity : AppCompatActivity() {

    var Synced:Boolean = false
    var SyncedID:String = ""
    var Pass:String = ""

    var SyncTrying:Boolean = false
    var SyncPass:String = ""
    var SyncCon:Connection? = null
    var PassAccepted:Boolean = false

    var c:Connection? = null

    var sharedPref:SharedPreferences? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = this?.getPreferences(Context.MODE_PRIVATE)

        Synced = sharedPref?.getBoolean("Synced", false) ?: false
        SyncedID = sharedPref?.getString("SyncedID", "") ?: ""
        Pass = sharedPref?.getString("Pass", "") ?: ""

        val buttonList: List<ImageButton> = listOf(btn0,btn1,btn2,btn3,btn4,btn5,btn6,btn7,btn8,btn9,btn10,btn11,btn12,btn13,btn14);

        val controlListener = fun (con:Connection, s:String){
            for (msg in s.split(";")){
                val spl = msg.split(":");
                val cmd = spl[0]
                @SuppressLint("Range")
                when (cmd){
                    "IMAGE" -> {
                        if (!Synced || (Synced && !PassAccepted)){
                            con.closeConnection()
                            return
                        }
                        try {
                            val args = spl[1].split(",")
                            val image = buttonList[(args[0]).toInt()]
                            val decodedString = Base64.decode(args[1], Base64.DEFAULT);
                            var bitmap: Bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            bitmap = Bitmap.createScaledBitmap(bitmap, image.measuredWidth, image.measuredHeight, true);
                            runOnUiThread {
                                image.setImageBitmap(bitmap)
                            }
                        }catch (e: Exception){
                            e.printStackTrace()
                        }
                    }
                    "PING" -> {
                        con.sendMessage("PONG;")
                    }
                    "PONG" -> {

                    }
                    "CONN" -> {
                        try {
                            val args = spl[1].split(",")
                            if (args[0] == SyncedID && args[1] == Pass){
                                PassAccepted = true
                                con.sendMessage("CONNACCEPT;")
                                c = con
                            }
                        }catch (e:Exception){
                            con.closeConnection()
                        }
                    }
                    "SYNCREJ" -> {
                        con.closeConnection()
                    }
                    "SYNCTRY" -> {
                        if (Synced) return
                        if (SyncTrying) return
                        try {
                            val args = spl[1].split(",")
                            SyncPass = args[1]
                            SyncTrying = true
                            //Open Sync UI
                            CafeBar.builder(this)
                                .theme(CafeBarTheme.LIGHT)
                                .floating(true)
                                .swipeToDismiss(true)
                                .duration(Int.MAX_VALUE)
                                .content("Sync request came Do you accept? Password is $SyncPass ")
                                .positiveText("Accept")
                                .positiveColor(Color.BLUE)
                                .negativeText("Reject")
                                .negativeColor(Color.RED)
                                .onPositive {
                                    if (!SyncTrying) return@onPositive
                                    Synced = true
                                    SyncedID = args[0]
                                    Pass = SyncPass
                                    with (sharedPref?.edit()) {
                                        this?.putBoolean("Synced", Synced)
                                        this?.putString("SyncedID", SyncedID)
                                        this?.putString("Pass", Pass)
                                        this?.commit()
                                    }

                                    SyncTrying = false
                                    SyncPass = "0"
                                    doThreaded {
                                        con.sendMessage("SYNCACCEPT:${getUID()},$Pass;")
                                    }
                                    it.dismiss()
                                }
                                .onNegative {
                                    if (!SyncTrying) return@onNegative
                                    SyncCon?.sendMessage("SYNCREJ;")
                                    SyncTrying = false
                                    SyncPass = "0"
                                    SyncCon?.closeConnection()
                                    it.dismiss()
                                }
                                .show();
                        }catch (e:Exception){
                            e.printStackTrace()
                            Log.e("Network Listener","Closing Connection, Stuff Happened")
                            con.closeConnection()
                        }
                    }
                }
            }
        }


        buttonList.forEachIndexed{ index, element ->
            element.setOnTouchListener { _:View, e:MotionEvent ->
                if (!Synced) return@setOnTouchListener true
                doThreaded {
                    when (e.action){
                        MotionEvent.ACTION_DOWN -> {
                            c?.sendMessage("BTNEVENT:$index,0;")
                        }
                        MotionEvent.ACTION_UP -> {
                            c?.sendMessage("BTNEVENT:$index,1;")
                        }
                    }
                }
                return@setOnTouchListener true
            }
        }

        var d:NetworkDiscovery? = null

        val discoveryListener = NetworkDiscovery.OnFoundListener {
            val con = Connection()
            con.OnDataCallback = controlListener
            con.openConnection(it.inetAddresses[0],it.port){
                if(Synced){
                    con.sendMessage("CONN:${getUID()};")
                }else{
                    SyncTrying = false
                    SyncPass = ""
                    con.sendMessage("SYNCREQ:${getUID()};")
                }
            }
        }

        doThreaded {
            d = NetworkDiscovery(this)
            d?.findServers("_pmdeck._tcp.local.", discoveryListener)
        }

        //On Wifi connection state change
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        registerReceiver( object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action:String? = intent?.action
                if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                    if (intent?.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)!!) {
                        //do stuff
                        c?.closeConnection()
                        d?.reset()
                        d?.findServers("_pmdeck._tcp.local.", discoveryListener)
                    } else {
                        // wifi connection was lost
                    }
                }


            }
        },intentFilter)

    }




    var swap:Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            Synced = false
            SyncedID = ""
            SyncPass = "0"
            SyncTrying = false

            with (sharedPref?.edit()) {
                this?.putBoolean("Synced", Synced)
                this?.putString("SyncedID", SyncedID)
                this?.putString("Pass", Pass)
                this?.commit()
            }

            RebirthHelper.doRestart(this)
        }else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
            //swap = !swap;
            //setSystemUIEnabled(swap);

        }
        return true
    }


    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

            Synced = false
            SyncedID = ""
            SyncPass = "0"
            with (sharedPref?.edit()) {
                this?.putBoolean("Synced", Synced)
                this?.putString("SyncedID", SyncedID)
                this?.putString("Pass", Pass)
                this?.commit()
            }


            SyncTrying = false
            RebirthHelper.doRestart(this)
            //c?.closeConnection()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }



}
