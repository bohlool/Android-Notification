package com.wanikani.androidnotifier;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wanikani.androidnotifier.notification.NotificationService;

/* 
 *  Copyright (c) 2013 Alberto Cuda
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This activity allows the user to perform its reviews through an integrated
 * browser. The only reason we need this (instead of just spawning an external
 * browser) is that we also display a minimal keyboard, that interacts with WK scripts
 * to compose kanas. Ordinarily, in fact, Android keyboards do not behave correctly.
 * <p>
 * The keyboard is displayed only when needed, so we need to check whether the
 * page contains a <code>user_response</code> text box, and it is enabled.
 * In addition, to submit the form, we simulate a click on the <code>option-submit</code>
 * button. Since the keyboard hides the standard controls (in particular the
 * info ("did you know...") balloons), we hide the keyboard when the user enters 
 * his/her response. 
 * <p>
 * To accomplish this, we register a JavascriptObject (<code>wknKeyboard</code>) and inject
 * a javascript to check how the page looks like. If the keyboard needs to be shown,
 * it calls its <code>show</code> (vs. <code>hide</code>) method.
 * The JavascriptObject is implemented by @link WebReviewActivity.WKNKeyboard.
 */
public class WebReviewActivity extends Activity {
	
	/**
	 * This class is barely a container of all the strings that should match with the
	 * WaniKani portal. Hopefully none of these will ever be changed, but in case
	 * it does, here is where to look for.
	 */
	public static class WKConfig {
		
		/** HTML id of the textbox the user types its answer in (reviews, client-side) */
		static final String ANSWER_BOX = "user-response";

		/** HTML id of the textbox the user types its answer in (lessons) */
		static final String LESSON_ANSWER_BOX_JP = "translit";
		
		/** HTML id of the textbox the user types its answer in (lessons) */
		static final String LESSON_ANSWER_BOX_EN = "lesson_user_response";

		/** HTML id of the submit button */
		static final String SUBMIT_BUTTON = "option-submit";

		/** HTML id of the lessons review form */
		static final String LESSONS_REVIEW_FORM = "new_lesson";
		
		/** HTML id of the lessons quiz */
		static final String QUIZ = "quiz";
		
		/** Any object on the lesson pages */
		static final String LESSONS_OBJ = "nav-lesson";
		
		/** Reviews div */
		static final String REVIEWS_DIV = "reviews";		
	};
	
	/**
	 * The listener attached to the ignore button tip message.
	 * When the user taps the ok button, we write on the property
	 * that it has been acknowleged, so it won't show up any more. 
	 */
	private class OkListener implements DialogInterface.OnClickListener {
		
		@Override
		public void onClick (DialogInterface ifc, int which)
		{
			SettingsActivity.setIgnoreButtonMessage (prefs (), true);
		}		
	}
	
	/**
	 * The listener attached to the hw accel tip message.
	 * When the user taps the ok button, we write on the property
	 * that it has been acknowleged, so it won't show up any more. 
	 */
	private class AccelOkListener implements DialogInterface.OnClickListener {
		
		@Override
		public void onClick (DialogInterface ifc, int which)
		{
			SettingsActivity.setHWAccelMessage (prefs (), true);
		}		
	}

	/**
	 * The listener that receives events from the mute buttons.
	 */
	private class MuteListener implements View.OnClickListener {
		
		@Override
		public void onClick (View w)
		{
			SettingsActivity.toggleMute (prefs ());
			applyMuteSettings ();
		}
	}

	/**
	 * The listener that receives events from the single buttons.
	 */
	private class SingleListener implements View.OnClickListener {
		
		@Override
		public void onClick (View w)
		{
			single = !single;
			applySingleSettings ();
		}
	}

	/**
	 * Web view controller. This class is used by @link WebView to tell whether
	 * a link should be opened inside of it, or an external browser needs to be invoked.
	 * Currently, I will let all the pages inside the <code>/review</code> namespace
	 * to be opened here. Theoretically, it could even be stricter, and use
	 * <code>/review/session</code>, but that would be prevent the final summary
	 * from being shown. That page is useful, albeit not as integrated with the app as
	 * the other pages.
	 */
	private class WebViewClientImpl extends WebViewClient {
				
		/**
		 * Called to check whether a link should be opened in the view or not.
		 * We also display the progress bar.
		 * 	@param view the web view
		 *  @url the URL to be opened
		 */
	    @Override
	    public boolean shouldOverrideUrlLoading (WebView view, String url) 
	    {
    		Intent intent;
    		    		
    		if (shouldOpenExternal (url)) {
    			intent = new Intent (Intent.ACTION_VIEW);	    		
	    		intent.setData (Uri.parse (url));
	    		startActivity (intent);	
	    		
	    		return true;
	    	}
	    	
    		return false;
	    }
	    
	    /**
	     * Tells if we should spawn an external browser
	     *  @param url the url we are opening
	     * 	@return true if we should
	     */
	    public boolean shouldOpenExternal (String url)
	    {	    	
	    	String curl;
	    	
	    	if (!url.contains ("wanikani.com") && !download)
	    		return true;
	    	
	    	curl = wv.getUrl ();
	    	if (curl == null)
	    		return false;

	    	/* Seems the only portable way to do this */
	    	if (curl.contains ("www.wanikani.com/lesson") ||
	    		curl.contains ("www.wanikani.com/review")) {
	    		
	    		if ((url.contains ("/kanji/") ||
	    			 url.contains ("/radicals/")) &&
	    			 SettingsActivity.getExternalItems (WebReviewActivity.this))
	    			return true;
	    			
	    	}
	    	
	    	return false;
	    }
		
	    /**
	     * Called when something bad happens while accessing the resource.
	     * Show the splash screen and give some explanation (based on the <code>description</code>
	     * string).
	     * 	@param view the web view
	     *  @param errorCode HTTP error code
	     *  @param description an error description
	     *  @param failingUrl error
	     */
	    public void onReceivedError (WebView view, int errorCode, String description, String failingUrl)
	    {
	    	String s;
	    	
	    	s = getResources ().getString (R.string.fmt_web_review_error, description);
	    	splashScreen (s);
	    	bar.setVisibility (View.GONE);
	    }

		@Override  
	    public void onPageStarted (WebView view, String url, Bitmap favicon)  
	    {  
	        bar.setVisibility (View.VISIBLE);
    		keyboard.reset ();
		}
	
		/**
	     * Called when a page finishes to be loaded. We hide the progress bar
	     * and run the initialization javascript that shows the keyboard, if needed.
	     * In addition, if this is the initial page, we check whether Viet has
	     * deployed the client-side review system
	     */
		@Override  
	    public void onPageFinished (WebView view, String url)  
	    {  
			ExternalFramePlacer.Dictionary dict;
			boolean wasVisible;
			
			wasVisible = bar.getVisibility () != View.GONE;
			bar.setVisibility (View.GONE);
			
			/* wasVisible is just an hack because externalframeplacer 
			 * causes a spurious call to onPageFinished on some devices */
			if (wasVisible && url.startsWith ("http")) {
				
				wv.js (JS_INIT_KBD);
				if (SettingsActivity.getExternalFramePlacer (WebReviewActivity.this)) {
					dict = SettingsActivity.getExternalFramePlacerDictionary (WebReviewActivity.this);
					ExternalFramePlacer.run (wv, dict);
				}
				
				if (SettingsActivity.getPartOfSpeech (WebReviewActivity.this))
					PartOfSpeech.enter (WebReviewActivity.this, wv, url);
			}			
	    }
	}
	
	/**
	 * An additional webclient, that receives a few callbacks that a simple 
	 * {@link WebChromeClient} does not intecept. 
	 */
	private class WebChromeClientImpl extends WebChromeClient {
	
		/**
		 * Called as the download progresses. We update the progress bar.
		 * @param view the web view
		 * @param progress progress percentage
		 */
		@Override	
		public void onProgressChanged (WebView view, int progress)
		{
			bar.setProgress (progress);
		}		
	};

	/**
	 * A small job that hides, shows or iconizes the keyboard. We need to implement this
	 * here because {@link WebReviewActibity.WKNKeyboard} gets called from a
	 * javascript thread, which is not necessarily an UI thread.
	 * The constructor simply calls <code>runOnUIThread</code> to make sure
	 * we hide/show the views from the correct context.
	 */
	private class ShowHideKeyboard implements Runnable {
		
		/** New state to enter */
		KeyboardStatus kbstatus;
		
		/**
		 * Constructor. It also takes care to schedule the invokation
		 * on the UI thread, so all you have to do is just to create an
		 * instance of this object
		 * @param kbstatus the new keyboard status to enter
		 */
		ShowHideKeyboard (KeyboardStatus kbstatus)
		{
			this.kbstatus = kbstatus;
			
			runOnUiThread (this);
		}
		
		/**
		 * Hides/shows the keyboard. Invoked by the UI thread.
		 * As a side effect, we update the {@link WebReviewActivity#flushCaches}
		 * bit.
		 */
		public void run ()
		{	
			kbstatus.apply (WebReviewActivity.this);
			if (kbstatus.isRelevantPage ())
				reviewsSession ();			
		}
		
		private void reviewsSession ()
		{
			flushCaches = true;
			CookieSyncManager.getInstance ().sync ();
		}
		
	}
	
	/**
	 * This class implements the <code>wknKeyboard</code> javascript object.
	 * It implements the @link {@link #show} and {@link #hide} methods. 
	 */
	private class WKNKeyboard {
		
		/**
		 * Called by javascript when the keyboard should be shown.
		 */
		@JavascriptInterface
		public void show ()
		{
			new ShowHideKeyboard (KeyboardStatus.REVIEWS_MAXIMIZED);
		}

		/**
		 * Called by javascript when the keyboard should be shown, using
		 * new lessons layout.
		 */
		@JavascriptInterface
		public void showLessonsNew ()
		{
			new ShowHideKeyboard (KeyboardStatus.LESSONS_MAXIMIZED_NEW);
		}

		/**
		 * Called by javascript when the keyboard should be hidden.
		 */
		@JavascriptInterface
		public void hide ()
		{
			new ShowHideKeyboard (KeyboardStatus.INVISIBLE);
		}
	}
	
	/**
	 * Our implementation of a menu listener. We listen for configuration changes. 
	 */
	private class MenuListener extends MenuHandler.Listener {
		
		public MenuListener ()
		{
			super (WebReviewActivity.this);
		}
		
		/**
		 * The dashboard listener exits the activity
		 */
		public void dashboard ()
		{
			Intent i;
			
			i = new Intent (WebReviewActivity.this, MainActivity.class);
			i.setAction (Intent.ACTION_MAIN);
			i.addFlags (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			
			startActivity (i);

			finish ();
		}
		
		/**
		 * Toggle override fonts
		 */
		@Override
		public void fonts ()
		{
			keyboard.overrideFonts ();
			ActivityCompat.invalidateOptionsMenu (WebReviewActivity.this);
		}
		
		/**
		 * Refresh the page, clearing the cache too.
		 */
		@Override
		public void refresh ()
		{
			wv.clearCache (true);
			wv.js ("window.location.reload (true)");
		}
	}
	
	/**
	 * Keyboard visiblity status.
	 */
	enum KeyboardStatus {
		
		/** Keyboard visible, all keys visible */
		REVIEWS_MAXIMIZED {
			 public void apply (WebReviewActivity wav) { wav.show (this); }

 			 public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			 {
				 return SettingsActivity.getReviewsKeyboard (wav);
			 }

 			 public boolean canMute ()
 			 {
 				 return true;
 			 }
 			 
 			 public boolean canDoSingle ()
 			 {
 				 return true;
 			 }
 		},
		
		/** Keyboard visible, all keys but ENTER visible */
		LESSONS_MAXIMIZED_NEW {
			public void apply (WebReviewActivity wav) { wav.show (this); } 
			
			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.getReviewsKeyboard (wav);				
			}

			public boolean canMute ()
			{
				return true;
			}
		},

		/** Keyboard invisible */
		INVISIBLE {
			public void apply (WebReviewActivity wav) { wav.hide (this); }
			
			public boolean isRelevantPage () { return false; }
			
			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.Keyboard.NATIVE;
			}
			
			public boolean backIsSafe () { return true; }		
		};
		
		public abstract void apply (WebReviewActivity wav);
		
		public void maximize (WebReviewActivity wav)
		{
			/* empty */
		}
		
		public boolean isIconized ()
		{
			return false;
		}
		
		public abstract SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav);
		
		public boolean isRelevantPage ()
		{
			return true;
		} 
		
		public boolean canMute ()
		{
			 return false;
		}
			
		public boolean canDoSingle ()
		{
			 return false;
		}
		
		public boolean hasEnter (WebReviewActivity wav)
		{
			return false;
		}

		public boolean backIsSafe ()
		{
			return false;
		}
	};
	
	private class ReaperTaskListener implements TimerThreadsReaper.ReaperTaskListener {
				
		public void reaped (int count, int total)
		{
			/* Here we could keep some stats. Currently unused */
		}		
	}
	
	private class IgnoreButtonListener implements View.OnClickListener {
		
		@Override
		public void onClick (View view)
		{
			ignore ();
		}
		
	}
	
	private class FileDownloader implements DownloadListener, FileDownloadTask.Listener {
		
		FileDownloadTask fdt;
		
		@Override
		public void onDownloadStart (String url, String userAgent, String contentDisposition, 
									 String mimetype, long contentLength)
		{			
			dbar.setVisibility (View.VISIBLE);
			cancel ();			
			fdt = new FileDownloadTask (WebReviewActivity.this, downloadPrefix, this);
			fdt.execute (url);
		}				
		
		private void cancel ()
		{
			if (fdt != null)
				fdt.cancel ();			
		}
		
		@Override
		public void setProgress (int percentage)
		{
			dbar.setProgress (percentage);
		}
		
		@Override
		public void done (File file)
		{
			Intent results;

			dbar.setVisibility (View.GONE);
			if (file != null) {			
				results = new Intent ();
				results.putExtra (EXTRA_FILENAME, file.getAbsolutePath ());
				setResult (RESULT_OK, results);
				finish ();
			} else
				Toast.makeText (WebReviewActivity.this, getString (R.string.tag_download_failed), 
						   	 	Toast.LENGTH_LONG).show ();
		}
	}

	/** The web view, where the web contents are rendered */
	FocusWebView wv;
	
	/** The view containing a splash screen. Visible when we want to display 
	 * some message to the user */ 
	View splashView;
	
	/**
	 * The view contaning the ordinary content.
	 */
	View contentView;
	
	/** A textview in the splash screen, where we can display some message */
	TextView msgw;
	
	/** The web progress bar */
	ProgressBar bar;
			
	/** The web download progress bar */
	ProgressBar dbar;
	
	/// Selected button color
	int selectedColor;
	
	/// Unselected button color
	int unselectedColor;	

	/** The local prefix of this class */
	private static final String PREFIX = "com.wanikani.androidnotifier.WebReviewActivity.";
	
	/** Open action, invoked to start this action */
	public static final String OPEN_ACTION = PREFIX + "OPEN";
	
	/** Download action, invoked to download a file */
	public static final String DOWNLOAD_ACTION = PREFIX + "DOWNLOAD";
	
	public static final String EXTRA_DOWNLOAD_PREFIX = PREFIX + "download_prefix";

	public static final String EXTRA_FILENAME = PREFIX + "filename";

	/** Flush caches bundle key */
	private static final String KEY_FLUSH_CACHES = PREFIX + "flushCaches";
	
	/** Local preferences file. Need it because we access preferences from another file */
	private static final String PREFERENCES_FILE = "webview.xml";
	
	/** Javascript to be called each time an HTML page is loaded. It hides or shows the keyboard */
	private static final String JS_INIT_KBD = 
			"var textbox, lessobj, ltextbox, reviews, style;" +
			"textbox = document.getElementById (\"" + WKConfig.ANSWER_BOX + "\"); " +
			"reviews = document.getElementById (\"" + WKConfig.REVIEWS_DIV + "\");" +
			"quiz = document.getElementById (\"" + WKConfig.QUIZ + "\");" +
			"if (quiz != null) {" +
			"   wknKeyboard.showLessonsNew ();" +
			"} else if (textbox != null && !textbox.disabled) {" +
			"   wknKeyboard.show (); " +
			"} else {" +
			"	wknKeyboard.hide ();" +			
			"}" +
			"if (reviews != null) {" +
			"   reviews.style.overflow = \"visible\";" +
			"}" + 
			"window.trueRandom = Math.random;" +
			"window.fakeRandom = function() { return 0;  };" +   // @Ikalou's fix
			/* This fixes a bug that makes SRS indication slow */
			"style = document.createElement('style');" + 
			"style.type = 'text/css';" + 
			"style.innerHTML = '.animated { -webkit-animation-duration:0s; }';" + 
			"document.getElementsByTagName('head')[0].appendChild(style);";
			
	
	private static final String
			JS_BULK_MODE = "if (window.trueRandom) Math.random=window.trueRandom;"; 
	private static final String
			JS_SINGLE_MODE = "if (window.fakeRandom) Math.random=window.fakeRandom;"; 
	
	/** The threads reaper */
	TimerThreadsReaper reaper;
	
	/** Thread reaper task */
	TimerThreadsReaper.ReaperTask rtask;
	
	/** The current keyboard status */
	protected KeyboardStatus kbstatus;
	
	/** The mute drawable */
	private Drawable muteDrawable;
	
	/** The sound drawable */
	private Drawable notMutedDrawable;
	
	/** The menu handler */
	private MenuHandler mh;
	
	/** The ignore button */
	private ImageButton ignbtn;
	
	/** Set if visible */
	boolean visible;
	
	/** Set if we have reviewed or had some lessons, so caches should be flushed */
	private boolean flushCaches;
	
	/** Is mute enabled */
	private boolean isMuted;
	
	/** The current keyboard */
	private Keyboard keyboard;
	
	/** The native keyboard */
	private Keyboard nativeKeyboard;
	
	/** The local IME keyboard */
	private Keyboard localIMEKeyboard;
	
	/** The mute button */
	private ImageButton muteH;
	
	/** The single button */
	private Button singleb;
	
	/** Single mode is on? */
	private boolean single;
	
	/** Shall we download a file? */
	private boolean download;
	
	/** Download prefix */
	private String downloadPrefix;
	
	/** The file downloader, if any */
	private FileDownloader fda;
		
	/**
	 * Called when the action is initially displayed. It initializes the objects
	 * and starts loading the review page.
	 * 	@param bundle the saved bundle
	 */
	@Override
	public void onCreate (Bundle bundle) 
	{		
		super.onCreate (bundle);

		Resources res;
		
		CookieSyncManager.createInstance (this);
		setVolumeControlStream (AudioManager.STREAM_MUSIC);

		mh = new MenuHandler (this, new MenuListener ());
		
		if (SettingsActivity.getFullscreen (this)) {
			getWindow ().addFlags (WindowManager.LayoutParams.FLAG_FULLSCREEN);
			requestWindowFeature (Window.FEATURE_NO_TITLE);
		}
		
		setContentView (R.layout.web_review);

		res = getResources ();
		
		selectedColor = res.getColor (R.color.selected);
    	unselectedColor = res.getColor (R.color.unselected);
		 
    	muteDrawable = res.getDrawable(R.drawable.ic_mute);
		notMutedDrawable = res.getDrawable(R.drawable.ic_not_muted);

		kbstatus = KeyboardStatus.INVISIBLE;
		
		bar = (ProgressBar) findViewById (R.id.pb_reviews);
		dbar = (ProgressBar) findViewById (R.id.pb_download);
		
		ignbtn = (ImageButton) findViewById (R.id.btn_ignore);
		ignbtn.setOnClickListener (new IgnoreButtonListener ());
				
		/* First of all get references to views we'll need in the near future */
		splashView = findViewById (R.id.wv_splash);
		contentView = findViewById (R.id.wv_content);
		msgw = (TextView) findViewById (R.id.tv_message);
		wv = (FocusWebView) findViewById (R.id.wv_reviews);

		wv.getSettings ().setJavaScriptEnabled (true);
		wv.getSettings().setJavaScriptCanOpenWindowsAutomatically (true);
		wv.getSettings ().setSupportMultipleWindows (false);
		wv.getSettings ().setUseWideViewPort (false);
		wv.getSettings ().setDatabaseEnabled (true);
		wv.getSettings ().setDomStorageEnabled (true);
		wv.getSettings ().setDatabasePath (getFilesDir ().getPath () + "/wv");
		wv.addJavascriptInterface (new WKNKeyboard (), "wknKeyboard");
		wv.setScrollBarStyle (ScrollView.SCROLLBARS_OUTSIDE_OVERLAY);
		wv.setWebViewClient (new WebViewClientImpl ());
		wv.setWebChromeClient (new WebChromeClientImpl ());		

		download = getIntent ().getAction ().equals (DOWNLOAD_ACTION);
		if (download) {
			downloadPrefix = getIntent ().getStringExtra (EXTRA_DOWNLOAD_PREFIX);
			wv.setDownloadListener (fda = new FileDownloader ());
		} 
		
		wv.loadUrl (getIntent ().getData ().toString ());
		
		nativeKeyboard = new NativeKeyboard (this, wv);
		localIMEKeyboard = new LocalIMEKeyboard (this, wv);
		
		muteH = (ImageButton) findViewById (R.id.kb_mute_h);
		muteH.setOnClickListener (new MuteListener ());

		singleb = (Button) findViewById (R.id.kb_single);
		singleb.setOnClickListener (new SingleListener ());

		if (SettingsActivity.getTimerReaper (this)) {
			reaper = new TimerThreadsReaper ();
			rtask = reaper.createTask (new Handler (), 2, 7000);
			rtask.setListener (new ReaperTaskListener ());
		}
	}
	
	@Override
	public void onNewIntent (Intent intent)
	{
		String curl, nurl;
		
		super.onNewIntent (intent);
		curl = wv.getOriginalUrl ();
		nurl = intent.getData ().toString ();
		if (curl == null || !curl.equals (nurl))		
			wv.loadUrl (nurl);
	}

	@Override
	protected void onResume ()
	{
		Window window;
		
		super.onResume ();

		window = getWindow ();
		if (SettingsActivity.getLockScreen (this))
			window.addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			window.clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		if (SettingsActivity.getResizeWebview (this))
			window.setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		else
			window.setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		
		visible = true;
		
		selectKeyboard ();
		
		applyMuteSettings ();
		applySingleSettings ();
		
		showHWAccelMessage ();
		
		wv.acquire ();
		
		kbstatus.apply (this);
		
		if (rtask != null)
			rtask.resume ();
	}
	
	@Override
	public void onDestroy ()
	{
		super.onDestroy ();

		mh.unregister (this);

		if (reaper != null)
			reaper.stopAll ();
		
		if (fda != null)
			fda.cancel ();
		
		if (SettingsActivity.getLeakKludge (this))
			System.exit (0);
	}
	
	@Override
	protected void onSaveInstanceState (Bundle bundle)
	{
		bundle.putBoolean (KEY_FLUSH_CACHES, flushCaches);
	}
	
	@Override 
	protected void onRestoreInstanceState (Bundle bundle)
	{
		if (bundle != null && bundle.containsKey (KEY_FLUSH_CACHES))
			flushCaches = bundle.getBoolean (KEY_FLUSH_CACHES);
	}
	
	@Override
	protected void onPause ()
	{
		Intent intent;
		
		visible = false;

		super.onPause ();
		intent = new Intent (MainActivity.ACTION_REFRESH);
		intent.putExtra (MainActivity.E_FLUSH_CACHES, flushCaches);
		sendBroadcast (intent);

		/* Alert the notification service too (the main action may not be active) */
		intent = new Intent (this, NotificationService.class);
		intent.setAction (NotificationService.ACTION_NEW_DATA);
		startService (intent);
		
		setMute (false);
		
		wv.release ();
		
		if (rtask != null)
			rtask.pause ();
		
		keyboard.hide ();
	}
	
	/**
	 * Tells if calling {@link WebView#goBack()} is safe. On some WK pages we should not use it. 
	 * @return <tt>true</tt> if it is safe.
	 */
	protected boolean backIsSafe ()
	{
		String lpage, rpage, url;
		
		url = wv.getUrl ();		
		lpage = "www.wanikani.com/lesson";
		rpage = "www.wanikani.com/review";
		
		return kbstatus.backIsSafe () &&
				/* Need this because the reviews summary page is dangerous */
				!(url.contains (rpage) || rpage.contains (url)) &&
				!(url.contains (lpage) || lpage.contains (url));
	}
	
	@Override
	public void onBackPressed ()
	{
        	String url;

        	url = wv.getUrl ();

        	if (url == null)
        		super.onBackPressed();
        	else if (url.contains ("http://www.wanikani.com/quickview"))
        		wv.loadUrl (SettingsActivity.getLessonURL (this));
        	else if (wv.canGoBack () && backIsSafe ())
        		wv.goBack();
        	else {
        		// Dialog box added 25/6/2015 by Aralox, based on http://stackoverflow.com/a/9901871/1072869
        		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        		builder.setMessage(R.string.back_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.back_yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            WebReviewActivity.super.onBackPressed();
                        }
                    })
                    .setNegativeButton(R.string.back_no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        		AlertDialog alert = builder.create();
        		alert.show();
        	}
	}
	
	/**
	 * Associates the menu description to the menu key (or action bar).
	 * The XML description is <code>review.xml</code>
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		getMenuInflater().inflate (R.menu.review, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu (Menu menu) 
	{
		MenuItem mi;
		int i;
		
		for (i = 0; i < menu.size (); i++) {
			mi = menu.getItem (i);
			if (mi.getItemId () == R.id.em_fonts) {
				mi.setVisible (keyboard.canOverrideFonts ());
				mi.setIcon (keyboard.getOverrideFonts () ? 
							R.drawable.ic_menu_font_enabled : R.drawable.ic_menu_font);
			}
		}
		
		return true;
	}
	
	/**
	 * Force menu invalidation.
	 */
	public void invalidateMenu ()
	{
		ActivityCompat.invalidateOptionsMenu (this);
	}	

	/**
	 * Menu handler. Relays the call to the common {@link MenuHandler}.
	 * 	@param item the selected menu item
	 */
	@Override
	public boolean onOptionsItemSelected (MenuItem item)
	{
		return mh.onOptionsItemSelected (item) || super.onOptionsItemSelected (item);
	}

	protected void selectKeyboard ()
	{
		Keyboard oldk;
		
		oldk = keyboard;
		
		switch (kbstatus.getKeyboard (this)) {
		case LOCAL_IME:
			keyboard = localIMEKeyboard;
			break;
			
		case NATIVE:
			keyboard = nativeKeyboard;
			break;
		}
				
		if (keyboard != oldk && oldk != null)
			oldk.hide ();
		
		updateCanIgnore ();
	}
	
	private void applyMuteSettings ()
	{
		boolean show;
		
		show = kbstatus.canMute () && SettingsActivity.getShowMute (this);
		muteH.setVisibility (show ? View.VISIBLE : View.GONE);
		
		setMute (show && SettingsActivity.getMute (prefs ()));
	}
	
	private void applySingleSettings ()
	{
		boolean show;
		
		show = kbstatus.canDoSingle () && SettingsActivity.getShowSingle (this); 
		singleb.setVisibility (show ? View.VISIBLE : View.GONE);
		if (single) {
			singleb.setTextColor (selectedColor);
			singleb.setTypeface (null, Typeface.BOLD);
			wv.js (JS_SINGLE_MODE);
		} else {
			singleb.setTextColor (unselectedColor);
			singleb.setTypeface (null, Typeface.NORMAL);			
			wv.js (JS_BULK_MODE);
		}
	}
	
	private void setMute (boolean m)
	{
		Drawable d;
		
		d = m ? muteDrawable : notMutedDrawable;
		muteH.setImageDrawable (d);

		if (isMuted != m && keyboard != null) { 
			keyboard.setMute (m);
			isMuted = m;
		}
	}	
		
	/**
	 * Displays the splash screen, also providing a text message
	 * @param msg the text message to display
	 */
	protected void splashScreen (String msg)
	{
		msgw.setText (msg);
		contentView.setVisibility (View.GONE);
		splashView.setVisibility (View.VISIBLE);
	}
	
	/**
	 * Hides the keyboard
	 * @param kbstatus the new keyboard status
	 */
	protected void hide (KeyboardStatus kbstatus)
	{
		this.kbstatus = kbstatus;
		
		applyMuteSettings ();
		applySingleSettings ();
		
		keyboard.hide ();
	}
	 
	protected void show (KeyboardStatus kbstatus)
	{
		this.kbstatus = kbstatus;

		selectKeyboard ();
		
		applyMuteSettings ();
		applySingleSettings ();

		keyboard.show (kbstatus.hasEnter (this));
	}

	protected void iconize (KeyboardStatus kbs)
	{
		kbstatus = kbs;
		
		selectKeyboard ();
		
		applyMuteSettings ();
		applySingleSettings ();
		
		keyboard.iconize (kbstatus.hasEnter (this));
	}
	
	public void updateCanIgnore ()
	{
		ignbtn.setVisibility (keyboard.canIgnore () ? View.VISIBLE : View.GONE);
	}
	
	/**
	 * Ignore button
	 */
	public void ignore ()
	{
		showIgnoreButtonMessage ();
		keyboard.ignore ();
	}
	
	protected void showIgnoreButtonMessage ()
	{
		AlertDialog.Builder builder;
		Dialog dialog;
					
		if (!visible || SettingsActivity.getIgnoreButtonMessage (this, prefs ()))
			return;
		
		builder = new AlertDialog.Builder (this);
		builder.setTitle (R.string.ignore_button_message_title);
		builder.setMessage (R.string.ignore_button_message_text);
		builder.setPositiveButton (R.string.ignore_button_message_ok, new OkListener ());
		
		dialog = builder.create ();
		SettingsActivity.setIgnoreButtonMessage (prefs (), true);

		dialog.show ();		
	}

	protected void showHWAccelMessage ()
	{
		AlertDialog.Builder builder;
		Dialog dialog;
					
		if (!visible || SettingsActivity.getHWAccelMessage (prefs ()))
			return;
		
		builder = new AlertDialog.Builder (this);
		builder.setTitle (R.string.hw_accel_message_title);
		builder.setMessage (R.string.hw_accel_message_text);
		builder.setPositiveButton (R.string.hw_accel_message_ok, new AccelOkListener ());
		
		dialog = builder.create ();
		SettingsActivity.setHWAccelMessage (prefs (), true);

		dialog.show ();		
	}
	
	protected SharedPreferences prefs ()
	{
		return getSharedPreferences (PREFERENCES_FILE, Context.MODE_PRIVATE);		
	}
	
}
