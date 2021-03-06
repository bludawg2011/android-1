/* ownCloud Android client application
 *   Copyright (C) 2012-2013 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.ui.preview;

import com.owncloud.android.Log_OC;
import com.owncloud.android.R;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.authentication.AccountUtils.AccountNotFoundException;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.media.MediaService;
import com.owncloud.android.ui.activity.FileActivity;

import android.accounts.Account;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;

/**
 *  Activity implementing a basic video player.
 * 
 *  Used as an utility to preview video files contained in an ownCloud account.
 *  
 *  Currently, it always plays in landscape mode, full screen. When the playback ends,
 *  the activity is finished. 
 *  
 *  @author David A. Velasco
 */
public class PreviewVideoActivity extends FileActivity implements OnCompletionListener, OnPreparedListener, OnErrorListener {

    /** Key to receive a flag signaling if the video should be started immediately */
    public static final String EXTRA_AUTOPLAY = "AUTOPLAY";
    
    /** Key to receive the position of the playback where the video should be put at start */
    public static final String EXTRA_START_POSITION = "START_POSITION";
    
    private static final String TAG = PreviewVideoActivity.class.getSimpleName();

    private FileDataStorageManager mStorageManager;
    
    private int mSavedPlaybackPosition;         // in the unit time handled by MediaPlayer.getCurrentPosition()
    private boolean mAutoplay;                  // when 'true', the playback starts immediately with the activity
    private VideoView mVideoPlayer;             // view to play the file; both performs and show the playback
    private MediaController mMediaController;   // panel control used by the user to control the playback
          
    /** 
     *  Called when the activity is first created.
     *  
     *  Searches for an {@link OCFile} and ownCloud {@link Account} holding it in the starting {@link Intent}.
     *  
     *  The {@link Account} is unnecessary if the file is downloaded; else, the {@link Account} is used to 
     *  try to stream the remote file - TODO get the streaming works
     * 
     *  {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log_OC.e(TAG, "ACTIVITY\t\tonCreate");
        
        setContentView(R.layout.video_layout);
    
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            mSavedPlaybackPosition = extras.getInt(EXTRA_START_POSITION);
            mAutoplay = extras.getBoolean(EXTRA_AUTOPLAY);
            
        } else {
            mSavedPlaybackPosition = savedInstanceState.getInt(EXTRA_START_POSITION);
            mAutoplay = savedInstanceState.getBoolean(EXTRA_AUTOPLAY);
        }
          
        mVideoPlayer = (VideoView) findViewById(R.id.videoPlayer);

        // set listeners to get more contol on the playback
        mVideoPlayer.setOnPreparedListener(this);
        mVideoPlayer.setOnCompletionListener(this);
        mVideoPlayer.setOnErrorListener(this);
          
        // keep the screen on while the playback is performed (prevents screen off by battery save)
        mVideoPlayer.setKeepScreenOn(true);
    }    
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log_OC.e(TAG, "ACTIVITY\t\tonSaveInstanceState");
        outState.putInt(PreviewVideoActivity.EXTRA_START_POSITION, mVideoPlayer.getCurrentPosition());
        outState.putBoolean(PreviewVideoActivity.EXTRA_AUTOPLAY , mVideoPlayer.isPlaying());
    }

    
    @Override
    public void onBackPressed() {
        Log_OC.e(TAG, "ACTIVTIY\t\tonBackPressed");
        Intent i = new Intent();
        i.putExtra(EXTRA_AUTOPLAY, mVideoPlayer.isPlaying());
        i.putExtra(EXTRA_START_POSITION, mVideoPlayer.getCurrentPosition());
        setResult(RESULT_OK, i);
        super.onBackPressed();
    }

    
    /** 
     * Called when the file is ready to be played.
     * 
     * Just starts the playback.
     * 
     * @param   mp    {@link MediaPlayer} instance performing the playback.
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        Log_OC.e(TAG, "ACTIVITY\t\tonPrepare");
        mVideoPlayer.seekTo(mSavedPlaybackPosition);
        if (mAutoplay) { 
            mVideoPlayer.start();
        }
        mMediaController.show(5000);  
    }
    
    
    /**
     * Called when the file is finished playing.
     *  
     * Rewinds the video
     * 
     * @param   mp    {@link MediaPlayer} instance performing the playback.
     */
    @Override
    public void onCompletion(MediaPlayer  mp) {
        mVideoPlayer.seekTo(0);
    }
    
    
    /**
     * Called when an error in playback occurs.
     * 
     * @param   mp      {@link MediaPlayer} instance performing the playback.
     * @param   what    Type of error
     * @param   extra   Extra code specific to the error
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log_OC.e(TAG, "Error in video playback, what = " + what + ", extra = " + extra);
        
        if (mMediaController != null) {
            mMediaController.hide();
        }
        
        if (mVideoPlayer.getWindowToken() != null) {
            String message = MediaService.getMessageForMediaError(this, what, extra);
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.VideoView_error_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    PreviewVideoActivity.this.onCompletion(null);
                                }
                            })
                    .setCancelable(false)
                    .show();
        }
        return true;
    }
    
    
    @Override
    protected void onAccountSet(boolean stateWasRecovered) {
        if (getAccount() != null) {
            OCFile file = getFile();
            /// Validate handled file  (first image to preview)
            if (file == null) {
                throw new IllegalStateException("Instanced with a NULL OCFile");
            }
            if (!file.isVideo()) {
                throw new IllegalArgumentException("Non-video file passed as argument");
            }
            mStorageManager = new FileDataStorageManager(getAccount(), getContentResolver());
            file = mStorageManager.getFileById(file.getFileId()); 
            if (file != null) {
                if (file.isDown()) {
                    mVideoPlayer.setVideoPath(file.getStoragePath());
                    
                } else {
                    // not working yet
                    String url;
                    try {
                        url = AccountUtils.constructFullURLForAccount(this, getAccount()) + file.getRemotePath();
                        mVideoPlayer.setVideoURI(Uri.parse(url));
                    } catch (AccountNotFoundException e) {
                        onError(null, MediaService.OC_MEDIA_ERROR, R.string.media_err_no_account);
                    }
                }
                
                // create and prepare control panel for the user
                mMediaController = new MediaController(this);
                mMediaController.setMediaPlayer(mVideoPlayer);
                mMediaController.setAnchorView(mVideoPlayer);
                mVideoPlayer.setMediaController(mMediaController);
                
            } else {
                finish();
            }
        } else {
            Log_OC.wtf(TAG, "onAccountChanged was called with NULL account associated!");
            finish();
        }
   }


}