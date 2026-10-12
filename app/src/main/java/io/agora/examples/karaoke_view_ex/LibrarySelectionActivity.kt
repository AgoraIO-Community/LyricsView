package io.agora.examples.karaoke_view_ex

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.agora.examples.karaoke_view_ex.databinding.ActivityLibrarySelectionBinding
import io.agora.examples.utils.ServiceType

/** Choose a library before creating RTC or music-content-center objects. */
class LibrarySelectionActivity : AppCompatActivity() {
    private var openingLibrary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLibrarySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.chooseTme.setOnClickListener { openLibrary(ServiceType.TME) }
        binding.chooseMcc.setOnClickListener { openLibrary(ServiceType.MCC) }
    }

    override fun onResume() {
        super.onResume()
        openingLibrary = false
    }

    private fun openLibrary(serviceType: ServiceType) {
        if (openingLibrary) return
        openingLibrary = true
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SERVICE_TYPE, serviceType.type))
    }
}
