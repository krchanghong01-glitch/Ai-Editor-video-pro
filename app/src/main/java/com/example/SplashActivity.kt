package com.example

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AppCompatActivity
import com.example.databinding.ActivitySplashBinding
import com.example.editor.EditorActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fade in logo dynamically for 500ms
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 500
            fillAfter = true
        }
        binding.imgLogo.startAnimation(fadeIn)

        // Auto transition after 2000ms delay to editor workspace
        binding.root.postDelayed({
            val intent = Intent(this, EditorActivity::class.java)
            startActivity(intent)
            finish()
        }, 2200)
    }
}
