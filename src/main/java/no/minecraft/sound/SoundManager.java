package no.minecraft.sound;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundManager {
    private static final SoundManager INSTANCE = new SoundManager();
    private final Map<String, byte[]> soundCache = new HashMap<>();
    private final ExecutorService soundPool = Executors.newFixedThreadPool(4);

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    private SoundManager() {
        preload("click");
        preload("pop");
        preload("hurt");
        preload("fall_small");
        preload("explode");
        preload("fuse");
        preload("break_stone");
        preload("break_wood");
        preload("break_grass");
        preload("dig_grass");
        preload("dig_stone");
        preload("dig_wood");
        preload("dig_sand");
        preload("dig_gravel");
        preload("bow_shoot");
        preload("zombie_say");
        preload("skeleton_say");
        preload("spider_say");
    }

    public void preload(String name) {
        try {
            InputStream is = getClass().getResourceAsStream("/assets/sounds/" + name + ".wav");
            if (is != null) {
                byte[] data = is.readAllBytes();
                soundCache.put(name, data);
                is.close();
            }
        } catch (Exception ignored) {
        }
    }

    public void play(String name) {
        play(name, 1.0f);
    }

    public void play(String name, float volume) {
        soundPool.submit(() -> {
            try {
                byte[] data = soundCache.get(name);
                if (data == null) {
                    preload(name);
                    data = soundCache.get(name);
                }
                if (data == null) return;

                AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(data)));
                Clip clip = AudioSystem.getClip();
                clip.open(ais);

                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log10(Math.max(0.0001f, volume)) * 20.0);
                    dB = Math.clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
                    gainControl.setValue(dB);
                }

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

                clip.start();
            } catch (Exception ignored) {
            }
        });
    }

    public void cleanup() {
        soundPool.shutdown();
    }
}
