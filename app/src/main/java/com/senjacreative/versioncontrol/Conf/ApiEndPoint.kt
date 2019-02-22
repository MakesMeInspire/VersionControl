package com.senjacreative.versioncontrol.Conf

class ApiEndPoint {
    companion object {
        private val HOST = "http://192.168.8.98/aplikasi/master/android/updater/"

        // filetype
        val FT = ".php"

        // FILE
        val READ = HOST+"getUpdateKt"+FT
    }
}