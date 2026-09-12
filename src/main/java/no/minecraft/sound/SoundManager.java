package no.minecraft.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundManager {
    private static final SoundManager INSTANCE = new SoundManager();

    private static volatile boolean muted = Boolean.getBoolean("minecraft.sound.disabled")
            || System.getProperty("surefire.test.class.path") != null
            || System.getProperty("test") != null;

    private final ExecutorService soundExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundManager-AudioThread");
        t.setDaemon(true);
        return t;
    });

    private boolean initialized = false;
    private long device = MemoryUtil.NULL;
    private long context = MemoryUtil.NULL;
    private final Map<String, Integer> soundBuffers = new HashMap<>();
    private static final int NUM_SOURCES = 16;
    private final int[] sources = new int[NUM_SOURCES];
    private int nextSourceIndex = 0;

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    public static void setMuted(boolean mute) {
        muted = mute;
    }

    public static boolean isMuted() {
        return muted;
    }

    private SoundManager() {
        if (!muted) {
            soundExecutor.submit(() -> {
                initOpenAL();
                if (initialized) {
                    preloadAll();
                }
            });
        }
    }

    private void initOpenAL() {
        if (initialized || muted) return;
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == MemoryUtil.NULL) {
                return;
            }

            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (IntBuffer) null);
            if (context == MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device);
                device = MemoryUtil.NULL;
                return;
            }

            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);

            for (int i = 0; i < NUM_SOURCES; i++) {
                sources[i] = AL10.alGenSources();
            }

            initialized = true;
        } catch (Throwable t) {
            initialized = false;
        }
    }

    private void preloadAll() {
        String[] sounds = {
                "click", "pop", "hurt", "crit", "fall_small",
                "explode", "fuse", "break_stone", "break_wood", "break_grass",
                "dig_grass", "dig_stone", "dig_wood", "dig_sand", "dig_gravel",
                "bow_shoot", "zombie_say", "skeleton_say", "spider_say", "eat", "burp"
        };
        for (String sound : sounds) {
            doPreload(sound);
        }
    }

    public void preload(String name) {
        if (muted) return;
        soundExecutor.submit(() -> doPreload(name));
    }

    private void doPreload(String name) {
        if (!initialized || soundBuffers.containsKey(name)) return;

        try (InputStream is = getClass().getResourceAsStream("/assets/sounds/" + name + ".wav")) {
            if (is == null) return;

            try (BufferedInputStream bis = new BufferedInputStream(is);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {

                AudioFormat format = ais.getFormat();
                AudioFormat targetFormat = format;

                if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED && format.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {
                    targetFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            format.getSampleRate(),
                            16,
                            format.getChannels(),
                            format.getChannels() * 2,
                            format.getSampleRate(),
                            false
                    );
                }

                byte[] rawData;
                if (targetFormat != format) {
                    try (AudioInputStream convertedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {
                        rawData = convertedAis.readAllBytes();
                    }
                } else {
                    rawData = ais.readAllBytes();
                }

                int alFormat;
                if (targetFormat.getChannels() == 1) {
                    alFormat = (targetFormat.getSampleSizeInBits() == 8) ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
                } else {
                    alFormat = (targetFormat.getSampleSizeInBits() == 8) ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;
                }

                int sampleRate = (int) targetFormat.getSampleRate();
                ByteBuffer dataBuffer = BufferUtils.createByteBuffer(rawData.length);
                dataBuffer.put(rawData);
                dataBuffer.flip();

                int bufferId = AL10.alGenBuffers();
                AL10.alBufferData(bufferId, alFormat, dataBuffer, sampleRate);
                soundBuffers.put(name, bufferId);
            }
        } catch (Throwable ignored) {
        }
    }

    public void play(String name) {
        play(name, 1.0f);
    }

    public void play(String name, float volume) {
        if (muted) return;
        float masterVolume = no.minecraft.settings.GameSettings.getInstance().getSoundVolume();
        if (masterVolume <= 0.001f) return;
        final float finalVol = Math.clamp(volume * masterVolume, 0.0f, 1.0f);

        soundExecutor.submit(() -> doPlay(name, finalVol));
    }

    private void doPlay(String name, float finalVol) {
        if (!initialized) {
            initOpenAL();
            if (!initialized) return;
        }

        Integer bufferId = soundBuffers.get(name);
        if (bufferId == null) {
            doPreload(name);
            bufferId = soundBuffers.get(name);
        }
        if (bufferId == null) return;

        int source = -1;
        for (int i = 0; i < NUM_SOURCES; i++) {
            int state = AL10.alGetSourcei(sources[i], AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                source = sources[i];
                break;
            }
        }
        if (source == -1) {
            source = sources[nextSourceIndex];
            nextSourceIndex = (nextSourceIndex + 1) % NUM_SOURCES;
        }

        AL10.alSourceStop(source);
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        AL10.alSourcei(source, AL10.AL_BUFFER, bufferId);
        AL10.alSourcef(source, AL10.AL_GAIN, finalVol);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
        AL10.alSource3f(source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        AL10.alSource3f(source, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcePlay(source);
    }

    public void cleanup() {
        soundExecutor.submit(this::doCleanup);
        soundExecutor.shutdown();
    }

    private void doCleanup() {
        if (!initialized) return;
        try {
            for (int source : sources) {
                if (source != 0) {
                    AL10.alSourceStop(source);
                    AL10.alDeleteSources(source);
                }
            }
            for (int buffer : soundBuffers.values()) {
                if (buffer != 0) {
                    AL10.alDeleteBuffers(buffer);
                }
            }
            soundBuffers.clear();

            if (context != MemoryUtil.NULL) {
                ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
                ALC10.alcDestroyContext(context);
                context = MemoryUtil.NULL;
            }
            if (device != MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device);
                device = MemoryUtil.NULL;
            }
            initialized = false;
        } catch (Throwable ignored) {
        }
    }
}
