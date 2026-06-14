package com.example.ludo

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnHost = findViewById<Button>(R.id.btnHost)
        val btnJoin = findViewById<Button>(R.id.btnJoin)
        val hostOptions = findViewById<LinearLayout>(R.id.hostOptions)
        val joinOptions = findViewById<LinearLayout>(R.id.joinOptions)
        val tvHostInfo = findViewById<TextView>(R.id.tvHostInfo)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnStartHost = findViewById<Button>(R.id.btnStartHost)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val etHostIp = findViewById<EditText>(R.id.etHostIp)
        val radioPlayers = findViewById<RadioGroup>(R.id.radioPlayers)

        btnHost.setOnClickListener {
            hostOptions.visibility = LinearLayout.VISIBLE
            joinOptions.visibility = LinearLayout.GONE

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip = try {
                Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
            } catch (e: Exception) { "?" }

            tvHostInfo.text = "১) প্রথমে এই ফোনের Hotspot অন করুন।\n" +
                    "২) অন্য প্লেয়াররা সেই Hotspot-এ কানেক্ট করবে।\n" +
                    "৩) তাদের 'Join' স্ক্রিনে এই ফোনের Hotspot IP দিতে হবে (সাধারণত 192.168.43.1)।\n" +
                    "আপনার বর্তমান WiFi IP: $ip"
        }

        btnJoin.setOnClickListener {
            joinOptions.visibility = LinearLayout.VISIBLE
            hostOptions.visibility = LinearLayout.GONE
        }

        btnStartHost.setOnClickListener {
            val numPlayers = when (radioPlayers.checkedRadioButtonId) {
                R.id.rb2 -> 2
                R.id.rb3 -> 3
                else -> 4
            }
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("mode", "host")
            intent.putExtra("numPlayers", numPlayers)
            startActivity(intent)
        }

        btnConnect.setOnClickListener {
            val ip = etHostIp.text.toString().trim()
            if (ip.isEmpty()) {
                tvStatus.text = "Host IP লিখুন।"
                return@setOnClickListener
            }
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("mode", "join")
            intent.putExtra("hostIp", ip)
            startActivity(intent)
        }
    }
}
