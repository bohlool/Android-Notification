package com.wanikani.androidnotifier.graph;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import com.wanikani.androidnotifier.R;
import com.wanikani.androidnotifier.graph.Pager.DataSet;
import com.wanikani.wklib.UserInformation;

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

public class HistogramPlot extends View {

	public class Series {
		
		public int color;
		
	}
	
	public class Sample {
		
		public Series series;
		
		public long value;
				
	}
	
	public class Samples {
		
		public String tag;
		
		public List<Sample> samples;
		
	}
		
	/**
	 * The listener that intercepts motion and fling gestures.
	 */
	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		
		@Override
		public boolean onDown (MotionEvent mev)
		{
			scroller.forceFinished (true);
			ViewCompat.postInvalidateOnAnimation (HistogramPlot.this);
			
			return true;
		}
		
		@Override
		public boolean onScroll (MotionEvent mev1, MotionEvent mev2, float dx, float dy)
		{
			vp.scroll ((int) dx, (int) dy);
			ViewCompat.postInvalidateOnAnimation (HistogramPlot.this);
			
			return true;
		}

		@Override
		public boolean onFling (MotionEvent mev1, MotionEvent mev2, float vx, float vy)
		{			
			scroller.forceFinished (true);
			scroller.fling (vp.getAbsPosition (), 0, (int) -vx, 0, 0, 
							vp.barToAbsPosition (vp.bars) + 2000000, 0, 0);
			ViewCompat.postInvalidateOnAnimation (HistogramPlot.this);
			
			return true;
		}
	}

	/**
	 * A repository of all the sizes and measures. Currently no variables
	 * can be customized, however I've kept them separated from their default
	 * values, so allowing layouts to override these default is just a matter of adding
	 * an attributes parser.
	 */
	private static class Measures {
		
		/// Default margin around the diagram
		public float DEFAULT_MARGIN = 24;
		
		/// Default number of pixels per bar
		public float DEFAULT_DIP_PER_BAR = 20;
				
		/// Default gap between bars
		public float DEFAULT_GAP = 8;
		
		/// Default label font size
		public float DEFAULT_DATE_LABEL_FONT_SIZE = 12;
		
		/// Default axis width
		public int DEFAULT_AXIS_WIDTH = 2;
		
		/// Default space between axis and label
		public int DEFAULT_HEADROOM = 10;
		
		/// Default number of items represented by a vertical mark
		public int DEFAULT_YAXIS_GRID = 100;

		/// The plot area
		public RectF plotArea;
		
		/// The complete view area
		public RectF viewArea;
		
		/// Actual margin around the diagram
		public float margin;
		
		/// Actual number of pixels per bar
		public float dipPerBar;
		
		/// Actual gap between bars
		public float gap;
		
		/// Actual label font size
		public float axisWidth;
		
		/// Actual space between axis and label
		public int headroom = 10;

		/// Actual height of a day tick
		public float dateLabelFontSize;
		
		/// Actual number of items represented by a vertical mark
		public int yaxisGrid;
				
		/**
		 * Constructor
		 * @param ctxt the context 
		 * @param attrs attributes of the plot. Currently ignored
		 */
		public Measures (Context ctxt, AttributeSet attrs)
		{
			DisplayMetrics dm;
			
			dm = ctxt.getResources ().getDisplayMetrics ();
			
			margin = DEFAULT_MARGIN;
			dipPerBar = DEFAULT_DIP_PER_BAR;
			gap = DEFAULT_GAP;
			axisWidth = DEFAULT_AXIS_WIDTH;
			headroom = DEFAULT_HEADROOM;
			dateLabelFontSize = DEFAULT_DATE_LABEL_FONT_SIZE;
			yaxisGrid = DEFAULT_YAXIS_GRID;
			
			dateLabelFontSize = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_SP, 
					 									   dateLabelFontSize, dm);
			updateSize (new RectF ());
		}

		/**
		 * Called when the plot changes it size. Updates the inner plot rect
		 * @param rect the new plot size
		 */
		public void updateSize (RectF rect)
		{
			viewArea = new RectF (rect);
			plotArea = new RectF (rect);
			
			plotArea.top += margin;
			plotArea.bottom -= margin;
		}
		
		/**
		 * Makes sure that the margin is large enough to display the time axis labels.
		 * @param mm minimum margin
		 */
		public void ensureFontMargin (long mm)
		{
			margin = Math.max (DEFAULT_MARGIN, mm + headroom);
			updateSize (viewArea);
		}
		
	}
	
	/**
	 * A collection of all the paint objects that will be needed when drawing
	 * on the canvas. We create them beforehand and recycle them for performance
	 * reasons.
	 */
	private static class PaintAssets {

		/// Paint used to draw the axis
		Paint axisPaint;

		/// Paint used to draw the grids				
		Paint gridPaint;
		
		/// Paint used to draw labels
		Paint labelPaint;
		
		/// Series to paint map
		Map<Series, Paint> series;
		
		/**
		 * Constructor. Creates all the paints, using the chart attributes and
		 * measures
		 * @param res the resources
		 * @param attrs the chart attributes
		 * @param meas measures object
		 */
		public PaintAssets (Resources res, AttributeSet attrs, Measures meas)
		{
			FontMetrics fm;
			Bitmap bmp;
			Shader shader;
			float points [];
			
			axisPaint = new Paint ();
			axisPaint.setColor (Color.BLACK);
			axisPaint.setStrokeWidth (meas.axisWidth);
			
			points = new float [] { 1, 1 };
			gridPaint = new Paint ();
			gridPaint.setColor (Color.BLACK);
			gridPaint.setPathEffect (new DashPathEffect (points, 0));
			
			labelPaint = new Paint ();
			labelPaint.setColor (Color.BLACK);
			labelPaint.setTextAlign (Paint.Align.CENTER);
			labelPaint.setTextSize ((int) meas.dateLabelFontSize);
			labelPaint.setAntiAlias (true);
			
			fm = labelPaint.getFontMetrics ();
			meas.ensureFontMargin ((int) (fm.bottom - fm.ascent));
			
			bmp = BitmapFactory.decodeResource (res, R.drawable.partial);
			
			series = new Hashtable<Series, Paint> ();
		}		
		
		/**
		 * Called when the series set changes. Recreates the mapping between
		 * series and paint objects
		 * @param series the new series
		 */
		public void setSeries (List<Series> series)
		{
			Paint p;
			
			this.series.clear ();
			for (Series s : series) {
				p = new Paint ();
				p.setColor (s.color);
				p.setStyle (Paint.Style.FILL_AND_STROKE);
				p.setAntiAlias (true);
				this.series.put (s, p);
			}
		}
	}
	
	/**
	 * This object tracks the position of the interval of the plot which is
	 * currently visible.
	 */
	private static class Viewport {
		
		/// The measure object
		Measures meas;
		
		/// The first (leftmost) tag 
		float t0;
		
		/// The last (rightmost) tag
		float t1;
		
		/// Size of interval (<tt>t1-t0</tt>) 
		float interval;
		
		/// Max value of Y axis
		long yMax;
		
		/// Y scale
		float yScale;
		
		/// Number of bars
		int bars;
		
		/**
		 * Constructor
		 * @param meas the measure object
		 * @param bars the number of bars
		 * @param yMax max value of Y axis
		 */
		public Viewport (Measures meas, int bars, long yMax)
		{			
			this.meas = meas;
			this.bars = bars;
			this.yMax = yMax;
			
			t1 = bars;
			
			updateSize ();
		}
				
		/**
		 * Called when the plot area changes
		 */
		public void updateSize ()
		{
			interval = meas.plotArea.width () / (meas.dipPerBar + meas.gap);
			yScale = meas.plotArea.height () / yMax;
			t0 = t1 - interval;
			adjust ();
		}
		
		/**
		 * Updates the lower and upper edges after the viewport is resized 
		 */
		private void adjust ()
		{
			if (t0 < 0)
				t0 = 0;
			else if (t0 > bars - interval)
				t0 = bars - interval;

			t1 = t0 + interval;
		}
		
		/**
		 * Returns the number of pixels between the left margin of the viewport
		 * and the first bar. Of course these pixels are not displayed because they
		 * are outside the viewport.
		 * @return the number of pixels
		 */
		public int getAbsPosition ()
		{
			return barToAbsPosition (t0);
		}
		
		/**
		 * Moves the viewport, putting its left margin at a number of pixels to
		 * the right of first bar
		 * @param pos the new position
		 */
		public void setAbsPosition (int pos)
		{
			t0 = absPositionToBar (pos);
			t1 = t0 + interval;
			adjust ();
		}
		
		/**
		 * Returns the number of pixels between the leftmost bar and a given bar
		 * @param bar a bar
		 * @return the number of pixels
		 */
		public int getRelPosition (int bar)
		{
			return (int) ((bar - t0) * (meas.dipPerBar + meas.gap));
		}
		
		/**
		 * Converts item numbers to pixel
		 * @param y item numbers
		 * @return the number of pixel
		 */
		public float getY (float y)
		{
			return meas.plotArea.bottom - y * yScale;
		}
		
		/**
		 * Scrolls the viewport by a given interval
		 * @param dx the horizontal interval
		 * @param dy the vertical interval (ignored)
		 */
		public void scroll (int dx, int dy)
		{
			setAbsPosition (getAbsPosition () + dx);
		}

		/**
		 * Returns the number of pixels between a given day and the
		 * day of subscription.
		 * @param bar a bar
		 * @return the number of pixels
		 */
		public int barToAbsPosition (float bar)
		{
			return (int) (bar * (meas.dipPerBar + meas.gap));
		}
		
		/**
		 * Returns the day, given the number of pixels from subscription day
		 * @param pos number of pixels
		 * @return the bar number
		 */
		public float absPositionToBar (int pos)
		{
			return ((float) pos) / (meas.dipPerBar + meas.gap);
		}
		
		/**
		 * A floor operation that always points to -inf.
		 * @param d a number 
		 * @return the floor
		 */
		private int floor (float d)
		{
			return (int) (d > 0 ? Math.floor (d) : Math.ceil (d));
		}
		
		/**
		 * A ceil operation that always points to +inf.
		 * @param d a number 
		 * @return the ceil
		 */
		private int ceil (float d)
		{
			return (int) (d > 0 ? Math.ceil (d) : Math.floor (d));
		}

		/**
		 * Returns the rightmost bar represented in this viewport.
		 * This differs from {@link #t1} because it is an integer
		 * @return the bar
		 */
		public int rightmostBar ()
		{
			return floor (t1);
		}
		
		/**
		 * Returns the leftmost bar represented in this viewport.
		 * This differs from {@link #t0} because it is an integer
		 * @return the bar
		 */
		public int leftmostBar ()
		{
			return ceil (t0);
		}
		
		/**
		 * Returns the current displayed interval
		 * @return the interval
		 */
		public Pager.Interval getInterval ()
		{
			return new Pager.Interval (floor (t0), ceil (t1));
		}		
	}
			
	/// The scroller object that tracks fling gestures
	private Scroller scroller;
	
	/// The android gesture detector
	private GestureDetector gdect;
	
	/// Our gesture listener
	private GestureListener glist;
	
	/// The measure object
	private Measures meas;
	
	/// The current viewport
	private Viewport vp;
	
	/// The paint objects
	private PaintAssets pas;
	
	/// <tt>true</tt> during fling gestures
	private boolean scrolling;
	
	/// The actual data
	private List<Samples> bars;
	
	/**
	 * Constructor
	 * @param ctxt the context
	 * @param attrs the attributes
	 */
	public HistogramPlot (Context ctxt, AttributeSet attrs)
	{
		super (ctxt, attrs);
		
		scroller = new Scroller (ctxt);
		glist = new GestureListener ();
		gdect = new GestureDetector (ctxt, glist);
		
		loadAttributes (ctxt, attrs);
	}
		
	/**
	 * Constructs the objects that use attributes.
	 * @param ctxt the context
	 * @param attrs the attributes
	 */
	void loadAttributes (Context ctxt, AttributeSet attrs)
	{
		meas = new Measures (ctxt, attrs);
		pas = new PaintAssets (getResources (), attrs, meas);		
	}
		
	/**
	 * Sets the data samples.
	 * @param series a list of series that will be referenced by <tt>data</tt> 
	 * @param bars a list of samples, each representing a bar
	 */
	public void setDataSource (List<Series> series, List<Samples> bars)
	{
		pas.setSeries (series);
		vp = new Viewport (meas, bars.size (), getMaxY (bars));
		
		this.bars = bars;
		
		invalidate ();
	}
	
	static private long getMaxY (List<Samples> bars)
	{
		long ans, current;
		
		ans = 0;
		for (Samples bar : bars) {
			current = 0;
			for (Sample s : bar.samples)
				current += s.value;
			ans = Math.max (ans, current);
		}
		
		return ans;
	}

	@Override
	public boolean onTouchEvent (MotionEvent mev)
	{
		boolean ans;

		switch (mev.getAction ()) {
		case MotionEvent.ACTION_DOWN:
			scrolling = true;
			break;

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			scrolling = false;
			break;
		}
		
		ans = gdect.onTouchEvent (mev);
		
		return ans || super.onTouchEvent (mev);
	}
	
	@Override
	protected void onSizeChanged (int width, int height, int ow, int oh)
	{
		meas.updateSize (new RectF (0, 0, width, height));
		vp.updateSize ();
		
		invalidate ();
	}

	@Override
    public void computeScroll () 
	{
        super.computeScroll ();
        
        if (scroller.computeScrollOffset ()) {
        	vp.setAbsPosition (scroller.getCurrX ());
			ViewCompat.postInvalidateOnAnimation (this);
        }
	}
	
	@Override
	protected void onDraw (Canvas canvas)
	{			
		drawPlot (canvas);
	}
	
	protected void drawPlot (Canvas canvas)
	{
		float left, right, tagLabelBaseline;
		int d, lo, hi, ascent;
		Samples bar;
		
		canvas.drawLine (meas.plotArea.left, meas.plotArea.bottom,
				         meas.plotArea.right, meas.plotArea.bottom, pas.axisPaint);
		lo = vp.leftmostBar ();
		hi = vp.rightmostBar ();
				
		ascent = (int) pas.labelPaint.getFontMetrics ().ascent;
		
		tagLabelBaseline = meas.plotArea.bottom - ascent + meas.headroom / 2;
		
		for (d = lo; d <= hi; d++) {
			left = vp.getRelPosition (d);
			right = left + vp.meas.dipPerBar;
			bar = bars.get (d);

			drawBar (canvas, bar, left, right);
			
			canvas.drawText (bar.tag, (left + right) / 2, tagLabelBaseline, pas.labelPaint);							
		}
		
		for (d = meas.yaxisGrid; vp.getY (d) >= meas.plotArea.top; d += meas.yaxisGrid)
			canvas.drawLine (meas.plotArea.left, vp.getY (d), 
							 meas.plotArea.right, vp.getY (d), pas.gridPaint);
	}
	
	protected void drawBar (Canvas canvas, Samples bar, float left, float right)
	{
		float base, height;
		
		base = 0;
		for (Sample sample : bar.samples) {
			if (sample.value > 0) {
				height = vp.getY (sample.value);
				canvas.drawRect(left, base, right, base - height, 
								pas.series.get (sample.series));
				base -= height;
			}
		}
	}
	
	/**
	 * True if scrolling 
	 * @return <tt>true</tt> if scrolling
	 */
	public boolean scrolling ()
	{
		return scrolling;
	}
}