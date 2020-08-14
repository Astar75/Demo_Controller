package com.example.democontrollerartnet

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_controller.*
import java.lang.StringBuilder
import java.util.*


class ControllerActivity : AppCompatActivity() {


    private val saberAdapter = SaberAdapter()
    private val rxBleClient = App.rxBleClient
    private var device: RxBleDevice? = null

    private val disconnectTriggerSubject = PublishSubject.create<Unit>()
    private var connectionDisposable = CompositeDisposable()
    private var connectionObservable: Observable<RxBleConnection>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        initRecycler()
        btnGet.setOnClickListener { getSabers() }

        if (intent.hasExtra(EXTRA_MAC_ADDRESS)) {
            val macAddress = intent.getStringExtra(EXTRA_MAC_ADDRESS)
            device = macAddress?.let { rxBleClient.getBleDevice(it) }
            connectionObservable = prepareConnection(device)
            connect()
            onNotifyEnable()
        }
    }

    override fun onPause() {
        dispose()
        connectionDisposable.clear()
        super.onPause()
    }

    private fun initRecycler() {
        with(sabers) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = saberAdapter
        }
    }

    private fun prepareConnection(device: RxBleDevice?): Observable<RxBleConnection>? =
        device?.establishConnection(false)
            ?.takeUntil(disconnectTriggerSubject)
            ?.compose(ReplayingShare.instance())

    private fun connect() {
        connectionObservable
            ?.flatMapSingle { it.discoverServices() }
            ?.flatMapSingle { it.getCharacteristic(UUID.fromString(CHR_REQUEST)) }
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.doOnSubscribe {
                Toast.makeText(this, "Успешеное подключение", Toast.LENGTH_SHORT).show()
            }
            ?.subscribe(
                { characteristic ->
                    Log.d("", "connect: $characteristic")
                }, {
                    onConnectionFailure(it)
                }
            )
            ?.let { connectionDisposable.add(it) }
    }

    private fun onWrite(message: String) {
        connectionObservable
            ?.firstOrError()
            ?.flatMap {
                it.writeCharacteristic(
                    UUID.fromString(CHR_REQUEST),
                    message.toByteArray()
                )
            }
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({
                onWriteSuccess()
            }, {
                onWriteFailure()
            })
            ?.let { connectionDisposable.add(it) }
    }

    private fun onWriteFailure() {
        Toast.makeText(this, "Ошибка передачи данных", Toast.LENGTH_SHORT).show()
    }

    private fun onWriteSuccess() {
        Toast.makeText(this, "Данные успешно переданы", Toast.LENGTH_SHORT).show()
    }

    private fun onReadFailure(it: Throwable) {
        Toast.makeText(this, "Ошибка чтения данных", Toast.LENGTH_SHORT).show()
    }

    private fun onConnectionFailure(throwable: Throwable) {
        Toast.makeText(this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
    }

    private fun onNotifyEnable() {
        connectionObservable
            ?.flatMap { it.setupNotification(UUID.fromString(CHR_RESPONSE)) }
            ?.doOnNext {
                runOnUiThread {
                    Toast.makeText(this, "Уведомнения включены", Toast.LENGTH_SHORT).show()
                }
            }
            ?.flatMap { it }
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({
                readData() //   если что то пришло то прочитать из характеристики список ламп
                Toast.makeText(this, "Что то пришло! ${String(it)}", Toast.LENGTH_SHORT).show()
            }, {
                Toast.makeText(this, "Что то хотело прийти, но не дошло!", Toast.LENGTH_SHORT).show()
            })
            ?.let { connectionDisposable.add(it) }
    }

    private fun dispose() {
        connectionDisposable.dispose()
    }

    private fun readData() {
        connectionObservable
            ?.firstOrError()
            ?.flatMap { it.readCharacteristic(UUID.fromString(CHR_RESPONSE)) }
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(
                {
                    Toast.makeText(this, "Считанные данные ${String(it)}", Toast.LENGTH_SHORT).show()
                }, {
                    Toast.makeText(this, "Ошибка чтения данных", Toast.LENGTH_SHORT).show()
                }
            )?.let { connectionDisposable.add(it) }
    }

    private fun getSabers() {
        onWrite("get_sabers")
    }

    private fun sendSettings() {
        val message = StringBuilder()
        val ssidstr = ssid.editText?.text
        val passstr = password.editText?.text
        val universestr = universe.editText?.text
        val maxchannelsstr = max_channels.editText?.text
        with(message) {
            append("save_settings:")
            append("ssid=").append(ssidstr)
            append("pass=").append(passstr)
            append("universe=").append(universestr)
            append("max_channels=").append(maxchannelsstr)
            append(";")
        }
        onWrite(message.toString())
    }

    companion object {

        private const val EXTRA_MAC_ADDRESS = "extra_mac_address"

        private const val CHR_REQUEST = "4536a992-ab6b-4a07-9c71-cd6a1f86053d"
        private const val CHR_RESPONSE = "ba9abf18-9561-416e-8958-b70f49d158eb"
        private const val CHR_BATTERY = "ba9abf18-9561-416e-8958-b70f49d158eb"

        fun newInstance(context: Context, macAddress: String): Intent =
            Intent(context, ControllerActivity::class.java).apply {
                putExtra(
                    EXTRA_MAC_ADDRESS,
                    macAddress
                )
            }
    }
}