package com.witcher.mp3cutdemo;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    /**
     * 输入内容: 一个音频文件 开始时间 结尾时间
     * 输出内容: 剪裁后的音频文件
     * 目前已经实验过的输入内容方式
     * res.raw
     * 网络url
     * 目前已经实验过的输入内容格式
     * mp3
     * 输出内容格式
     * pcm
     * pcm转换后的wav
     * 可能存在的问题
     * 目前实验所用mp3为漫播上随机找的一首歌和一部剧，不确定更多不同采样率、声道数量的mp3能否顺利剪裁
     *
     */

    /**
     * 1.读取一段音频到内存  or 录制一段音频
     * 2.裁剪一段音频  输入一个起始时间 一个结尾时间
     * seek到起始时间 读到结尾时间
     * 3.保存裁剪后的音频到文件
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.bt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                init1();
            }
        });

        findViewById(R.id.bt2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                init2();
            }
        });

        findViewById(R.id.bt3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initMediaPlayer();
            }
        });

        findViewById(R.id.bt4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                L.i("p1:" + new File(getFilesDir(), "aaa2.wav").getPath());
                L.i("p2:" + new File(getFilesDir(), "aaa2.wav").getAbsolutePath());
                new File(getFilesDir(), "aaa2.wav").delete();
                new File(getFilesDir(), "temp.pcm").delete();
            }
        });

    }

    private void initMediaPlayer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                long endTimeUs = 120 * 1000 * 1000;
                long startTimeUs = 60 * 1000 * 1000;

                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.aaa);
                MediaExtractor mediaExtractor = new MediaExtractor();
                //http://drama.kilamanbo.com/audio_transcoding/16644629665653.mp3  之前不是这样的 ---- 我借了个新综艺
                //http://drama.kilamanbo.com/audio_transcoding/16644629665653.mp3
//                mediaExtractor.setDataSource(afd);
                mediaExtractor.setDataSource("http://drama.kilamanbo.com/audio_transcoding/16644629665653.mp3");
                // 媒体文件中的轨道数量 （一般有视频，音频，字幕等）
                int trackCount = mediaExtractor.getTrackCount();
                L.i("trackCount:" + trackCount);
                // 记录轨道索引id，MediaExtractor 读取数据之前需要指定分离的轨道索引
                int trackID = -1;
                // 视频轨道格式信息
                MediaFormat trackFormat = null;
                for (int i = 0; i < trackCount; i++) {
                    trackFormat = mediaExtractor.getTrackFormat(i);
                    if (trackFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                        trackID = i;
                        break;
                    }
                }

                // 媒体文件中存在视频轨道
                if (trackID != -1) {
                    L.i("找到 trackID:" + trackID);
                    mediaExtractor.selectTrack(trackID);
                }

                if (trackFormat != null) {
                    mediaExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    L.i("current time:" + mediaExtractor.getSampleTime());
                    // 获取最大缓冲区大小，
                    int maxInputSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    //拿的是微妙 1000微妙是1毫秒
                    long duration = trackFormat.getLong(MediaFormat.KEY_DURATION);
                    L.i("时长:" + duration);
//                    L.i("帧数:"+trackFormat.getLong(MediaFormat.KEY_FRAME_RATE));
                    L.i("maxInputSize:" + maxInputSize);
                    // 开辟一个字节缓冲区，用于存放分离的媒体数据
                    ByteBuffer byteBuffer = ByteBuffer.allocate(maxInputSize);

                    File outPcmFile = new File(getFilesDir(), "temp.pcm");
                    FileChannel outPcmChannel = new FileOutputStream(outPcmFile).getChannel();

                    MediaCodec mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
                    mediaCodec.configure(trackFormat, null, null, 0);
                    mediaCodec.start();

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    while (true) {
                        int inputIndex = mediaCodec.dequeueInputBuffer(10_1000);
                        if (inputIndex >= 0) {
                            long sampleTimeUs = mediaExtractor.getSampleTime();
                            if (sampleTimeUs == -1L) {
                                L.i("-1 break");
                                break;
                            } else if (sampleTimeUs > endTimeUs) {
                                L.i("sampleTimeUs > endTimeUs break");
                                break;
                            } else if (sampleTimeUs < startTimeUs) {
                                mediaExtractor.advance();
                            }

                            info.presentationTimeUs = sampleTimeUs;
//                            L.i("getSampleTime:"+sampleTimeUs);
                            info.flags = mediaExtractor.getSampleFlags();
                            info.size = mediaExtractor.readSampleData(byteBuffer, 0);

                            byte[] byteArr = new byte[byteBuffer.remaining()];
                            byteBuffer.get(byteArr);

                            // 送入MP3数据到解码器
                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
                            inputBuffer.clear();
                            inputBuffer.put(byteArr);

                            mediaCodec.queueInputBuffer(inputIndex, 0, info.size, info.presentationTimeUs, info.flags);
                            // 读取下一个采样
                            mediaExtractor.advance();
                        }
                        // 获取解码后的pcm
                        int outputIndex = mediaCodec.dequeueOutputBuffer(info, 10_1000);
                        while (outputIndex >= 0) {
                            ByteBuffer outByteBuffer = mediaCodec.getOutputBuffer(outputIndex);

                            // 这里写入文件
                            outPcmChannel.write(outByteBuffer);

                            mediaCodec.releaseOutputBuffer(outputIndex, false);
                            outputIndex = mediaCodec.dequeueOutputBuffer(info, 0);
                        }
                    }

                    //  demo的MP3：采样率是44100hz，声道数是 双声道 2，16位的
                    int sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    L.i("pcm -> WAV sampleRate:" + sampleRate);
                    L.i("pcm -> WAV channelCount:" + channelCount);

                    // pcm -> WAV
                    File outWavPath = new File(getFilesDir(), "aaa2.wav");
                    new PcmToWavUtil(sampleRate, channelCount, 16)
                            .pcmToWav(outPcmFile.getAbsolutePath(), outWavPath.getAbsolutePath());
                    L.i("pcm -> WAV done:$outWavPath");
                }

            } catch (Exception e) {
                e.printStackTrace();
                L.i("e:" + e.getMessage());
            }
        }
    }

    private void init1() {
        try {
//            MediaPlayer mMediaPlayer = MediaPlayer.create(this, R.raw.aaa);
            MediaPlayer mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource("http://drama.kilamanbo.com/audio_transcoding/16644629665653.mp3");
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    L.i("onPrepared " + mMediaPlayer.getDuration());
                    mMediaPlayer.start();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    L.i("error what:" + what + ",extra:" + extra);
                    return false;
                }
            });
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    L.i("onSeekComplete " + mMediaPlayer.getCurrentPosition());
//                    mLyricParentView.updateTime(mMediaPlayer.getCurrentPosition());
                }
            });

            mMediaPlayer.prepareAsync();
        } catch (Exception e) {
            L.i("e:" + e.getMessage());
        }
    }

    private void init2() {
        try {
            MediaPlayer mMediaPlayer = new MediaPlayer();

            mMediaPlayer.setDataSource(new File(getFilesDir(), "aaa2.wav").getPath());
            L.i("path:" + new File(getFilesDir(), "aaa2.wav").getPath());
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    L.i("onPrepared " + mMediaPlayer.getDuration());
                    mMediaPlayer.start();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    L.i("error what:" + what + ",extra:" + extra);
                    return false;
                }
            });
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    L.i("onSeekComplete " + mMediaPlayer.getCurrentPosition());
                }
            });

            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}