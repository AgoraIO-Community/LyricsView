package io.agora.examples.karaoke_view.customized;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.Nullable;

import com.plattysoft.leonids.ParticleSystem;

import io.agora.karaoke_view.v11.ScoringView;

public class MyScoringView extends ScoringView {
    public MyScoringView(Context context) {
        super(context);
    }

    public MyScoringView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void initParticleSystem(Drawable[] particles) {
        // Import Leonids as below when re-write initParticleSystem
        // api 'com.github.guohai:Leonids:9f5a9190f6'
        mParticlesPerSecond = 16;

        // Build your customized particles when necessary
        // also can specify through setParticles
        if (particles == null) { // For default particles
            particles = buildDefaultParticleList();
        }

        mParticleSystem = new ParticleSystem((ViewGroup) this.getParent(), particles.length * 8, particles, 900);
        mParticleSystem.setRotationSpeedRange(90, 180).setScaleRange(0.7f, 1.6f)
                .setSpeedModuleAndAngleRange(0.10f, 0.20f, 120, 240)
                .setFadeOut(300, new AccelerateInterpolator());
    }
}
