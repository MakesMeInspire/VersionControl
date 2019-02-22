package com.senjacreative.versioncontrol

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.*
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.github.kittinunf.fuel.Fuel
import com.senjacreative.versioncontrol.Conf.ApiEndPoint
import com.tbruyelle.rxpermissions2.RxPermissions
import io.karn.notify.Notify
import org.json.JSONObject
import java.io.File



class VersionControl : AppCompatActivity() {

    lateinit var app_package: String
    lateinit var app_version: String
    lateinit var app_upVersion: String
    lateinit var app_upStatus: String
    lateinit var app_upLink: String
    lateinit var pd_cekUpdate: ProgressDialog
    lateinit var dwnld: ProgressDialog
    var i: Int=0
    var updateName = "/update.apk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_vc)
        getAppBase()

        // start checking update
        cekFile()
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    }
    private fun cekFile(){
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
        val fFile = File(root+updateName)
        val file = File(fFile.path)
        if (file.exists()){
            file.getCanonicalFile().delete()
            if(file.exists()){
                getApplicationContext().deleteFile(file.getName())
            }
            Log.d("DELETE","delete successfuly")
        }
    }

    private fun requestPermission(permission: String) {
        RxPermissions(this)
                .request(permission)
                .subscribe({
                    if (!it) {
                        finish()
                    }else{
                        cekUpdate()
                    }
                })
    }

    private fun getAppBase(){
        app_package = BuildConfig.APPLICATION_ID
        app_version = BuildConfig.VERSION_NAME
    }

    private fun cekUpdate(){
        pd_cekUpdate = ProgressDialog(this)
        pd_cekUpdate.setMessage("Checking Update")
        pd_cekUpdate.setCancelable(false)
        pd_cekUpdate.show()
        Handler().postDelayed({
            goCekUpdate()
        },1500)
    }

    private fun goCekUpdate(){
        AndroidNetworking.post(ApiEndPoint.READ)
                .addBodyParameter("package",app_package)
                .setPriority(Priority.MEDIUM)
                .build()
                .getAsJSONObject(object : JSONObjectRequestListener{
                    override fun onResponse(response: JSONObject?) {
                        pd_cekUpdate.dismiss()
                        if(response?.getString("result")?.contains("unregistered")!!){
                            unRegDialog()
                        }else{
                            CompareApp(response)
                        }
                    }

                    override fun onError(anError: ANError?) {
                        pd_cekUpdate.dismiss()
                        Toast.makeText(this@VersionControl,"Error",Toast.LENGTH_SHORT).show()
                        Log.e("ERROR CHECK"," => "+anError)
                        this@VersionControl.finish()
                    }
                })
    }

    private fun unRegDialog(){
        val unReg = AlertDialog.Builder(this@VersionControl)
        unReg.setTitle("Error !!!")
        unReg.setMessage("This Application Is Not Registered in Our Repository")
        unReg.setCancelable(false)
        unReg.setPositiveButton("Exit",{_,_->
            this@VersionControl.finish()
        })
        val uR = unReg.create()
        uR.show()
    }

    private fun maintenanceDialog(){
        val unMt = AlertDialog.Builder(this@VersionControl)
        unMt.setTitle("Attention !!!")
        unMt.setMessage("This Application is Under Maintenance")
        unMt.setCancelable(false)
        unMt.setPositiveButton("Exit",{_,_->
            this@VersionControl.finish()
        })
        val uMt = unMt.create()
        uMt.show()
    }

    private fun updateFound(){
        val upFound = AlertDialog.Builder(this@VersionControl)
        upFound.setTitle("Update Available!!!")
        upFound.setMessage("Please Update This App Before Using It.")
        upFound.setCancelable(false)
        upFound.setPositiveButton("Update",{_,_->
            // Download Logic
            createDownloadTask()
            dwnld = ProgressDialog(this)
            dwnld.setMessage("Updating")
            dwnld.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            dwnld.progress=0
            dwnld.setCancelable(false)
            dwnld.show()
        })
        upFound.setNegativeButton("Exit",{_,_->
            this@VersionControl.finish()
        })
        val uF = upFound.create()
        uF.show()
    }

    private fun CompareApp(response: JSONObject?){
        app_upVersion=""
        app_upStatus=""
        app_upLink=""
        val jsonArray = response?.optJSONArray("result")
        for(a in 0 until jsonArray?.length()!!){
            val jsonObject = jsonArray?.optJSONObject(a)
            app_upVersion = jsonObject.getString("version")
            app_upStatus = jsonObject.getString("status")
            app_upLink = jsonObject.getString("url")

            if(jsonArray?.length() - 1 == a){
                if(app_upStatus.equals("online")){
                    if(app_upVersion.equals(app_version)){
                        Toast.makeText(this@VersionControl,"Welcome :)",Toast.LENGTH_SHORT).show()
                    }else{
                        updateFound()
                        showNotification("Update Available","Update to version"+app_upVersion)
                    }
                }else{
                    maintenanceDialog()
                }
            }
        }
    }

    private fun showNotification(Title: String, Text: String){
        Notify
                .with(applicationContext)
                .meta { // this: Payload.Meta
                    // Launch the MainActivity once the notification is clicked.
                    clickIntent = PendingIntent.getActivity(applicationContext,
                            1412,
                            Intent(applicationContext, VersionControl::class.java),
                            0)
                    // Start a service which clears the badge count once the notification is dismissed.
                    clearIntent = PendingIntent.getService(applicationContext,
                            1412,
                            Intent(applicationContext, VersionControl::class.java)
                                    .putExtra("action", "clear_badges"),
                            0)
                }
                .content {
                    title = Title
                    text = Text
                }
                .show()
    }

    // UPDATER ANDROID FUN
    private fun createDownloadTask() {
        Log.d("DOWNLOADING"," => "+ app_upLink)
        Fuel.download(app_upLink).fileDestination { response, url ->
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString())
            File(dir, updateName)
        }.progress {
            readBytes, totalBytes -> val progress = readBytes.toFloat() / totalBytes.toFloat()
            dwnld.max=totalBytes.toInt()
            dwnld.progress=readBytes.toInt()
            Log.d("log",progress.toString())
        }.response {
            req, res, result -> Log.d("log","download completed ")
            Log.d("log",Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + updateName)
            doInstall()
        }
    }

    private fun doInstall(){
        Log.d("INSTALL","BEGIN")
        val destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()+updateName
        val uri = Uri.parse("file://"+destination)
        val file = File(uri.path)

//        if (Build.VERSION.SDK_INT >= 24) {
//            try {
//                val m = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
//                m.invoke(null)
//            } catch (e: Exception) { e.printStackTrace() }
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d("INSTALL",">= N")
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            val fileUri = FileProvider.getUriForFile(this@VersionControl, BuildConfig.APPLICATION_ID+".fileprovider", file)
            intent.setData(fileUri)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
            this@VersionControl.finish()
        } else {
            Log.d("INSTALL","<= M")
            val intent = Intent(Intent.ACTION_VIEW)
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + updateName)
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
            Log.d("INSTALL","FINISH")
            this@VersionControl.finish()
        }
    }
}