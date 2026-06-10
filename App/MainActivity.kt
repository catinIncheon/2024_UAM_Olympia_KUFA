package com.example.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : AppCompatActivity() {

    private lateinit var sktCheckBox: CheckBox
    private lateinit var ktCheckBox: CheckBox
    private lateinit var lgCheckBox: CheckBox
    private lateinit var frequencyBand3CheckBox: CheckBox
    private lateinit var frequencyBand1CheckBox: CheckBox
    private lateinit var frequencyBand5_8CheckBox: CheckBox
    private lateinit var frequencyBand7CheckBox: CheckBox
    private lateinit var frequencyBand3500CheckBox: CheckBox
    private lateinit var startMeasurementButton: Button
    private lateinit var stopMeasurementButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var rsrpGraph: GraphView
    private lateinit var rsrqGraph: GraphView
    private lateinit var sinrGraph: GraphView
    private val handler = Handler(Looper.getMainLooper())
    private var isMeasuring = false
    private val rsrpSeries = LineGraphSeries<DataPoint>()
    private val rsrqSeries = LineGraphSeries<DataPoint>()
    private val sinrSeries = LineGraphSeries<DataPoint>()
    private var graphLastXValue = 0.0
    private var phoneStateListener: PhoneStateListener? = null
    private lateinit var locationManager: LocationManager
    private lateinit var usbManager: UsbManager
    private var port: UsbSerialPort? = null
    private lateinit var tvStatus: TextView
    private var latitude = 0.0
    private var longitude = 0.0

    companion object {
        const val PERMISSION_REQUEST_CODE = 1
        const val TAG = "MainActivity"
        const val MEASUREMENT_INTERVAL: Long = 500
        val SKT_EARFCN_RANGE_BAND5 = 2400..2649
        val SKT_EARFCN_RANGE_BAND3 = 1200..1949
        val SKT_EARFCN_RANGE_BAND1 = 0..599
        val SKT_EARFCN_RANGE_BAND7 = 2750..3449
        val SKT_EARFCN_RANGE_BAND3500 = 620000..653333
        val KT_EARFCN_RANGE_BAND8 = 3450..3799
        val KT_EARFCN_RANGE_BAND3 = 1200..1949
        val KT_EARFCN_RANGE_BAND1 = 0..599
        val KT_EARFCN_RANGE_BAND3500 = 620000..653333
        val LG_EARFCN_RANGE_BAND5 = 2400..2649
        val LG_EARFCN_RANGE_BAND1 = 0..599
        val LG_EARFCN_RANGE_BAND7 = 2750..3449
        val LG_EARFCN_RANGE_BAND3500 = 620000..653333
        const val ACTION_USB_PERMISSION = "com.example.app.USB_PERMISSION"
        private const val WRITE_WAIT_MILLIS = 500
    }

    private val usbReceiver = object : BroadcastReceiver() { //USB 세팅
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Broadcast received: ${intent.action}")
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d(TAG, "Permission granted for device $device")
                            device?.let {
                                connectUsbSerial(it)
                            }
                        } else {
                            Log.d(TAG, "Permission denied for device $device")
                            tvStatus.text = "USB 상태: 권한 거부됨"
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        tvStatus.text = "USB 상태: 장치 연결됨"
                        requestUsbPermission(it)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    port?.close()
                    port = null
                    tvStatus.text = "USB 상태: 장치 분리됨"
                    Log.d(TAG, "USB device detached")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { //첫 세팅
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        tvStatus = findViewById(R.id.tvStatus)

        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)

        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val device = deviceList.values.first()
            tvStatus.text = "USB 상태: 이미 연결됨"
            Log.d(TAG, "USB device already connected")
            requestUsbPermission(device)
        } else {
            tvStatus.text = "USB 상태: 장치 없음"
            Log.d(TAG, "No USB devices found")
        }

        try {
            sktCheckBox = findViewById(R.id.sktCheckBox)
            ktCheckBox = findViewById(R.id.ktCheckBox)
            lgCheckBox = findViewById(R.id.lgCheckBox)
            frequencyBand3CheckBox = findViewById(R.id.frequencyBand3CheckBox)
            frequencyBand1CheckBox = findViewById(R.id.frequencyBand1CheckBox)
            frequencyBand5_8CheckBox = findViewById(R.id.frequencyBand5_8CheckBox)
            frequencyBand7CheckBox = findViewById(R.id.frequencyBand7CheckBox)
            frequencyBand3500CheckBox = findViewById(R.id.frequencyBand3500CheckBox)
            startMeasurementButton = findViewById(R.id.startMeasurementButton)
            stopMeasurementButton = findViewById(R.id.stopMeasurementButton)
            statusTextView = findViewById(R.id.statusTextView)
            rsrpGraph = findViewById(R.id.rsrpGraph)
            rsrqGraph = findViewById(R.id.rsrqGraph)
            sinrGraph = findViewById(R.id.sinrGraph)

            rsrpGraph.addSeries(rsrpSeries)
            rsrqGraph.addSeries(rsrqSeries)
            sinrGraph.addSeries(sinrSeries)

            initializeGraph(rsrpGraph, -120.0, -40.0, "RSRP [dBm]")
            initializeGraph(rsrqGraph, -40.0, 10.0, "RSRQ [dB]")
            initializeGraph(sinrGraph, -20.0, 40.0, "SINR [dB]")

            frequencyBand3CheckBox.isEnabled = false
            frequencyBand7CheckBox.isEnabled = false
            frequencyBand1CheckBox.isEnabled = false
            frequencyBand5_8CheckBox.isEnabled = false
            frequencyBand3500CheckBox.isEnabled = false
            startMeasurementButton.isEnabled = false
            stopMeasurementButton.isEnabled = false

            sktCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    ktCheckBox.isChecked = false
                    ktCheckBox.isEnabled = false
                    lgCheckBox.isChecked = false
                    lgCheckBox.isEnabled = false
                }
                enableFrequencyBandCheckBoxes(isChecked)
            }

            ktCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sktCheckBox.isChecked = false
                    sktCheckBox.isEnabled = false
                    lgCheckBox.isChecked = false
                    lgCheckBox.isEnabled = false
                }
                enableFrequencyBandCheckBoxes(isChecked)
                frequencyBand7CheckBox.isEnabled = false
            }

            lgCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sktCheckBox.isChecked = false
                    sktCheckBox.isEnabled = false
                    ktCheckBox.isChecked = false
                    ktCheckBox.isEnabled = false
                }
                enableFrequencyBandCheckBoxes(isChecked)
                frequencyBand3CheckBox.isEnabled = false
            }

            val checkBoxListener = { checkBox: CheckBox, _: Boolean ->
                if (checkBox.isChecked) {
                    disableOtherCheckBoxes(checkBox)
                } else {
                    enableAllFrequencyCheckBoxes()
                    if (ktCheckBox.isChecked)
                        frequencyBand7CheckBox.isEnabled = false
                    if (lgCheckBox.isChecked)
                        frequencyBand3CheckBox.isEnabled = false
                }
                startMeasurementButton.isEnabled = frequencyBand3CheckBox.isChecked ||
                        frequencyBand7CheckBox.isChecked ||
                        frequencyBand1CheckBox.isChecked ||
                        frequencyBand5_8CheckBox.isChecked ||
                        frequencyBand3500CheckBox.isChecked
            }

            frequencyBand3CheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxListener(frequencyBand3CheckBox, isChecked) }
            frequencyBand7CheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxListener(frequencyBand7CheckBox, isChecked) }
            frequencyBand1CheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxListener(frequencyBand1CheckBox, isChecked) }
            frequencyBand5_8CheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxListener(frequencyBand5_8CheckBox, isChecked) }
            frequencyBand3500CheckBox.setOnCheckedChangeListener { _, isChecked -> checkBoxListener(frequencyBand3500CheckBox, isChecked) }

            telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            if (!hasRequiredPermissions()) {
                requestRequiredPermissions()
            } else {
                detectCarrierAndCheckCheckbox()
                startLocationUpdates()
            }

            startMeasurementButton.setOnClickListener { onStartMeasurementClick() }
            stopMeasurementButton.setOnClickListener { onStopMeasurement() }

        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.error_initialization), e)
            Toast.makeText(this, getString(R.string.error_initialization), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsbPermission(device: UsbDevice) { //다시 연결 요구하는 코드
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        if (!usbManager.hasPermission(device)) {
            Log.d(TAG, "Requesting permission for device: ${device.deviceName}")
            usbManager.requestPermission(device, permissionIntent)
        } else {
            connectUsbSerial(device)
        }
    }

    private fun connectUsbSerial(device: UsbDevice) { //포트 연결하는 함수
        Log.d(TAG, "Connecting to USB device: ${device.deviceName}")

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            tvStatus.text = "USB 상태: 지원되지 않는 장치"
            Log.d(TAG, "Unsupported USB device: ${device.deviceName}")
            return
        }

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            tvStatus.text = "USB 상태: 연결 실패 (권한이 없거나 장치가 사용 중일 수 있음)"
            Log.d(TAG, "Unable to open USB device connection. Possible reasons: no permission, device in use.")
            return
        }

        port = driver.ports[0]

        try {
            port?.open(connection)
            port?.setParameters(
                9600,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            tvStatus.text = "USB 상태: 연결됨"
            Log.d(TAG, "USB connection established")

        } catch (e: Exception) {
            Log.e(TAG, "포트를 여는 도중 오류가 발생했습니다: ${e.message}")
            tvStatus.text = "USB 상태: 포트 열기 실패"
            port?.close()
        }
    }

    private fun sendMessage(values: DoubleArray) { //메세지 보내는 함수
        try {
            if (port == null || !port!!.isOpen) {
                Log.e(TAG, "Error: Port is not open")
                tvStatus.text = "USB 상태: 포트가 열리지 않음"
                return
            }

            // 각 값을 띄어쓰기로 구분하여 하나의 문자열로 만들고, 끝에 줄바꿈 추가
            val message = values.joinToString(separator = " ") { it.toString() } + " "
            val messageBytes = message.toByteArray()

            port?.write(messageBytes, WRITE_WAIT_MILLIS)
            Log.d(TAG, "Message sent: $message")
            tvStatus.text = "USB 상태: 데이터 전송됨"

        } catch (e: Exception) {
            Log.e(TAG, "Error during sending message: ${e.message}")
            tvStatus.text = "USB 상태: 메시지 전송 오류"
        }
    }


    override fun onResume() {
        super.onResume()
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
        }
    }

    private fun detectCarrierAndCheckCheckbox() { //유심 자동으로 찾아서 체크해주는 코드
        enableFrequencyBandCheckBoxes(true)
        val networkOperatorName = telephonyManager.networkOperatorName
        when {
            networkOperatorName.contains("SKT", true) -> {
                sktCheckBox.isChecked = true
                ktCheckBox.isEnabled = false
                lgCheckBox.isEnabled = false
                sktCheckBox.isEnabled = false
            }
            networkOperatorName.contains("KT", true) -> {
                ktCheckBox.isChecked = true
                sktCheckBox.isEnabled = false
                ktCheckBox.isEnabled = false
                lgCheckBox.isEnabled = false
                frequencyBand7CheckBox.isEnabled = false
            }
            networkOperatorName.contains("LG", true) -> {
                lgCheckBox.isChecked = true
                sktCheckBox.isEnabled = false
                lgCheckBox.isEnabled = false
                ktCheckBox.isEnabled = false
                frequencyBand3CheckBox.isEnabled = false
            }
            else -> {
                Toast.makeText(this, "알 수 없는 통신사입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onStartMeasurementClick() { //ON 버튼이 열리는 조건들
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            requestRequiredPermissions()
            return
        }

        if ((sktCheckBox.isChecked || ktCheckBox.isChecked || lgCheckBox.isChecked) && (frequencyBand3CheckBox.isChecked ||
                    frequencyBand7CheckBox.isChecked ||
                    frequencyBand1CheckBox.isChecked ||
                    frequencyBand5_8CheckBox.isChecked ||
                    frequencyBand3500CheckBox.isChecked)) {
            startMeasurement()
            Toast.makeText(this, getString(R.string.measurement_started), Toast.LENGTH_SHORT).show()
            setCheckBoxesEnabled(false)
            statusTextView.text = getString(R.string.status_measuring)
            stopMeasurementButton.isEnabled = true
            startMeasurementButton.isEnabled = false
        } else {
            Toast.makeText(this, getString(R.string.select_all_checkboxes), Toast.LENGTH_SHORT).show()
        }
    }

    private fun onStopMeasurement() { //STOP 버튼이 열리는 조건과 STOP 작동
        if (!isMeasuring) return

        isMeasuring = false
        Toast.makeText(this, "측정이 중지되었습니다.", Toast.LENGTH_SHORT).show()
        statusTextView.text = getString(R.string.status_stopped)
        handler.removeCallbacksAndMessages(null)
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error", e)
        }

        phoneStateListener?.let {
            telephonyManager.listen(it,PhoneStateListener.LISTEN_NONE)
        }
        startMeasurementButton.isEnabled = true
        stopMeasurementButton.isEnabled = false
        setCheckBoxesEnabled(true)
    }
    private fun startMeasurement() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            requestRequiredPermissions()
            return
        }

        isMeasuring = true
        Toast.makeText(this, "측정 시작 중...", Toast.LENGTH_SHORT).show()
        handler.post(object : Runnable {
            override fun run() {
                updateMeasurementData()
                handler.postDelayed(this, MEASUREMENT_INTERVAL)
            }
        })
    }
    private fun updateMeasurementData(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this@MainActivity, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val cellInfoList = telephonyManager.allCellInfo
        var bestRsrp: Int? = null
        var bestCell: CellInfoLte? = null

        for (cellInfo in cellInfoList) {
            if (cellInfo is CellInfoLte) {
                val earfcn = cellInfo.cellIdentity.earfcn

                if ((sktCheckBox.isChecked && isSKTSelectedFrequency(earfcn)) ||
                    (ktCheckBox.isChecked && isKTSelectedFrequency(earfcn)) ||
                    (lgCheckBox.isChecked && isLGSelectedFrequency(earfcn))) {

                    val rsrp = cellInfo.cellSignalStrength.rsrp

                    if (bestRsrp == null || rsrp > bestRsrp) {
                        bestRsrp = rsrp
                        bestCell = cellInfo
                    }
                }
            }
        }

        bestCell?.let {
            val cellSignalStrength = it.cellSignalStrength
            val earfcn = it.cellIdentity.earfcn
            val rsrp = cellSignalStrength.rsrp
            val rsrq = cellSignalStrength.rsrq
            val sinr = cellSignalStrength.rssnr

            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0

            graphLastXValue += 0.5
            rsrpSeries.appendData(DataPoint(graphLastXValue, rsrp.toDouble()), true, 100)
            rsrqSeries.appendData(DataPoint(graphLastXValue, rsrq.toDouble()), true, 100)
            sinrSeries.appendData(DataPoint(graphLastXValue, sinr.toDouble()), true, 100)

            when {
                sktCheckBox.isChecked && isSKTSelectedFrequency(earfcn) -> {
                    Toast.makeText(this, "SKT - RSRP: $rsrp, RSRQ: $rsrq, SINR: $sinr, LAT: $latitude, LNG: $longitude, EARFCN: $earfcn", Toast.LENGTH_SHORT).show()
                    sendMessage(doubleArrayOf(rsrp.toDouble(), rsrq.toDouble(), sinr.toDouble(), latitude, longitude))
                }
                ktCheckBox.isChecked && isKTSelectedFrequency(earfcn) -> {
                    Toast.makeText(this, "KT - RSRP: $rsrp, RSRQ: $rsrq, SINR: $sinr, LAT: $latitude, LNG: $longitude, EARFCN: $earfcn", Toast.LENGTH_SHORT).show()
                    sendMessage(doubleArrayOf(rsrp.toDouble(), rsrq.toDouble(), sinr.toDouble(), latitude, longitude))
                }
                lgCheckBox.isChecked && isLGSelectedFrequency(earfcn) -> {
                    Toast.makeText(this, "LG U+ - RSRP: $rsrp, RSRQ: $rsrq, SINR: $sinr, LAT: $latitude, LNG: $longitude, EARFCN: $earfcn", Toast.LENGTH_SHORT).show()
                    sendMessage(doubleArrayOf(rsrp.toDouble(), rsrq.toDouble(), sinr.toDouble(), latitude, longitude))
                }
                else -> {
                    Log.d(TAG, "No matching EARFCN range found: EARFCN: $earfcn")
                }
            }
        } ?: run {
            Log.d(TAG, "No LTE cells found")
        }
    }

    private fun initializeGraph(graph: GraphView, minY: Double, maxY: Double, title: String) {
        graph.viewport.isScalable = true
        graph.viewport.isScrollable = true
        graph.viewport.isXAxisBoundsManual = true
        graph.viewport.isYAxisBoundsManual = true
        graph.viewport.setMinX(0.0)
        graph.viewport.setMaxX(10.0)
        graph.viewport.setMinY(minY)
        graph.viewport.setMaxY(maxY)
        graph.title = title
    }

    private fun isSKTSelectedFrequency(earfcn: Int): Boolean {
        return (frequencyBand3CheckBox.isChecked && earfcn in SKT_EARFCN_RANGE_BAND3) ||
                (frequencyBand7CheckBox.isChecked && earfcn in SKT_EARFCN_RANGE_BAND7) ||
                (frequencyBand1CheckBox.isChecked && earfcn in SKT_EARFCN_RANGE_BAND1) ||
                (frequencyBand5_8CheckBox.isChecked && earfcn in SKT_EARFCN_RANGE_BAND5) ||
                (frequencyBand3500CheckBox.isChecked && earfcn in SKT_EARFCN_RANGE_BAND3500)
    }

    private fun isKTSelectedFrequency(earfcn: Int): Boolean {
        return (frequencyBand3CheckBox.isChecked && earfcn in KT_EARFCN_RANGE_BAND3) ||
                (frequencyBand7CheckBox.isChecked && earfcn in KT_EARFCN_RANGE_BAND8) ||
                (frequencyBand1CheckBox.isChecked && earfcn in KT_EARFCN_RANGE_BAND1) ||
                (frequencyBand3500CheckBox.isChecked && earfcn in KT_EARFCN_RANGE_BAND3500)
    }

    private fun isLGSelectedFrequency(earfcn: Int): Boolean {
        return (frequencyBand7CheckBox.isChecked && earfcn in LG_EARFCN_RANGE_BAND7) ||
                (frequencyBand1CheckBox.isChecked && earfcn in LG_EARFCN_RANGE_BAND1) ||
                (frequencyBand5_8CheckBox.isChecked && earfcn in LG_EARFCN_RANGE_BAND5) ||
                (frequencyBand3500CheckBox.isChecked && earfcn in LG_EARFCN_RANGE_BAND3500)
    }

    private fun enableFrequencyBandCheckBoxes(isChecked: Boolean) {
        frequencyBand3CheckBox.isEnabled = isChecked
        frequencyBand7CheckBox.isEnabled = isChecked
        frequencyBand1CheckBox.isEnabled = isChecked
        frequencyBand5_8CheckBox.isEnabled = isChecked
        frequencyBand3500CheckBox.isEnabled = isChecked
        if (!isChecked) {
            frequencyBand3CheckBox.isChecked = false
            frequencyBand7CheckBox.isChecked = false
            frequencyBand1CheckBox.isChecked = false
            frequencyBand5_8CheckBox.isChecked = false
            frequencyBand3500CheckBox.isChecked = false
            startMeasurementButton.isEnabled = false
        }
    }

    private fun disableOtherCheckBoxes(enabledCheckBox: CheckBox) {
        val checkBoxes = listOf(sktCheckBox, ktCheckBox, lgCheckBox, frequencyBand3CheckBox, frequencyBand7CheckBox, frequencyBand1CheckBox, frequencyBand5_8CheckBox, frequencyBand3500CheckBox)
        checkBoxes.forEach { checkBox ->
            if (checkBox != enabledCheckBox) {
                checkBox.isEnabled = false
            }
        }
    }

    private fun enableAllFrequencyCheckBoxes() {
        frequencyBand3CheckBox.isEnabled = true
        frequencyBand7CheckBox.isEnabled = true
        frequencyBand1CheckBox.isEnabled = true
        frequencyBand5_8CheckBox.isEnabled = true
        frequencyBand3500CheckBox.isEnabled = true
    }

    private fun setCheckBoxesEnabled(enabled: Boolean) {
        frequencyBand3CheckBox.isEnabled = enabled && frequencyBand3CheckBox.isChecked
        frequencyBand7CheckBox.isEnabled = enabled && frequencyBand7CheckBox.isChecked
        frequencyBand1CheckBox.isEnabled = enabled && frequencyBand1CheckBox.isChecked
        frequencyBand5_8CheckBox.isEnabled = enabled && frequencyBand5_8CheckBox.isChecked
        frequencyBand3500CheckBox.isEnabled = enabled && frequencyBand3500CheckBox.isChecked
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener!!)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener!!)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
                detectCarrierAndCheckCheckbox()
                startLocationUpdates()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latitude = location.latitude
            longitude = location.longitude
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    override fun onDestroy() {
        super.onDestroy()
        port?.close()
        unregisterReceiver(usbReceiver)
    }
}
