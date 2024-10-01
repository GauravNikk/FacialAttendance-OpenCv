package webview.again.facialattendance

import android.content.Context
import java.io.File

class AttendanceManager(private val context: Context) {

    fun saveEmployeeData(employeeName: String) {
        val file = File(context.filesDir, "attendance.txt")
        file.appendText("Employee: $employeeName, Time: ${System.currentTimeMillis()}\n")
    }
}
