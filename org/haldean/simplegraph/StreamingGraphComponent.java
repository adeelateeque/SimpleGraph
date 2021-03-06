package org.haldean.simplegraph;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;
import javax.swing.JComponent;

/**
 *  A component which draws a graph when provided with a feed of
 *  real-time data. The graph automatically scales along the Y axis
 *  and can be manually scaled along the X axis.
 *
 *  @author Will Brown (will.h.brown@gmail.com)
 */
public class StreamingGraphComponent<E extends Number> extends Component {
	private LinkedList<E> series;
	private double maximum = 1;
	private double minimum = -1;

	private int width;
	private int height;

	/* Area left at the top and bottom to ensure the graph never quite
	 * touches the edge, as a percentage of the value range */
	private double margin = 0.1;

	/* The number of data points visible on the graph */
	private int sampleCount = 100;
	private double pixelsPerSample;

	/* The index of the last added sample */
	private int lastSampleIndex = 0;

	/* The configuration object that holds the color, font and name of
	 * the graph */
	private GraphConfiguration config;

	/* The currently-moused-over x-value */
	private int currentFocusVertical;

	/**
	 * Create a new {@link StreamingGraphComponent} with the default
	 * {@link GraphConfiguration}.
	 */
	public StreamingGraphComponent() {
		this(new GraphConfiguration());
	}

	/**
	 * Create a new {@link StreamingGraphComponent} with the specified
	 * {@link GraphConfiguration}.
	 *
	 * @param gc The {@link GraphConfiguration} to use.
	 */
	public StreamingGraphComponent(GraphConfiguration gc) {
		series = new LinkedList<E>();
		config = gc;
		addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent e) {
					Dimension size = getSize();
					width = (int) size.getWidth();
					height = (int) size.getHeight();

					pixelsPerSample = width / sampleCount;
					currentFocusVertical = width - 1;
				}
			});
		GraphMouseHandler mouseHandler = new GraphMouseHandler();
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
	}

	/**
	 * Manually set the maximum. Note that if a data point larger than this
	 * maximum is added, the graph will automatically scale. This sets a lower
	 * bound on the actual maximum of the graph.
	 *
	 * @param max The lower bound for the maximum
	 */
	public void setMaximum(double max) {
		maximum = max;
		repaint();
	}

	/**
	 * Manually set the minimum. Note that if a data point smaller than this
	 * minimum is added, the graph will automatically scale. This sets an upper
	 * bound on the actual minimum of the graph.
	 *
	 * @param min The upper bound for the minimum
	 */
	public void setMinimum(double min) {
		minimum = min;
		repaint();
	}

	/**
	 * Set the {@link GraphConfiguration} of the graph.
	 *
	 * @param gc The configuration to assign.
	 */
	public void setGraphConfiguration(GraphConfiguration gc) {
		config = gc;
	}

	/**
	 * Get the {@link GraphConfiguration} of this component.
	 */
	public GraphConfiguration getGraphConfiguration() {
		return config;
	}

	/**
	 * Set the scale along the X axis.
	 *
	 * @param newSampleCount The number of samples shown along the
	 * axis. The graph will automatically adjust to show the most recent
	 * sampleCount samples.
	 */
	public void setSampleCount(int newSampleCount) {
		sampleCount = newSampleCount;
		pixelsPerSample = width / sampleCount;

		checkSeriesSize();
		repaint();
	}

	/**
	 * Add a value to the data series.
	 *
	 * @param value The value to add
	 */
	public void addValue(E value) {
		/* Create a lock on the series list */
		synchronized (series) {
			series.add(value);
		}

		lastSampleIndex++;
		checkSeriesSize();

		double doubleValue = value.doubleValue();
		/* Adjust the bounds if necessary */
		if (doubleValue > maximum)
			maximum = doubleValue;
		if (doubleValue < minimum)
			minimum = doubleValue;

		repaint();
	}

	/**
	 * Trims the data if we have more points then necessary
	 */
	private void checkSeriesSize() {
		synchronized (series) {
			while (series.size() > sampleCount) {
				series.removeFirst();
			}
		}
	}		

	/**
	 * Convert a value to a canvas pixel location.
	 *
	 * @param p The sample value
	 * @return The number of pixels between the top of the graph and the
	 * horizontal line representing the given sample value
	 */
	private int pointToY(E p) {
		return pointToY(p.doubleValue());
	}

	/**
	 * Convert a value to a canvas pixel location.
	 *
	 * @param p The sample value
	 * @return The number of pixels between the top of the graph and the
	 * horizontal line representing the given sample value
	 */
	private int pointToY(double p) {
		return (int) (((maximum - (1 - margin) * p) / (maximum - minimum)) * getSize().getHeight());
	}

	/**
	 * Convert a time-index to a canvas pixel location
	 *
	 * @param x The time index
	 * @return The pixel X corresponding to that time index
	 */
	private int pointToX(int x) {
		return (int) (((float) x / (float) sampleCount) * getSize().getWidth());
	}

	/**
	 * Paints the graph onto the provided graphics object
	 *
	 * @param canvas The graphics object to paint onto
	 */
	public void paint(Graphics canvas) {
		/* The Y component of the X axis can shift based on scaling, so
		 * we calculate it once to save computation */
		int y0 = pointToY(0);

		/* Background */
		canvas.setColor(config.getBackgroundColor());
		canvas.fillRect(0, 0, width, height);

		/* Border */
		canvas.setColor(config.getBorderColor());
		canvas.drawRect(0, 0, width, height);

		/* Horizontal axis */
		canvas.setColor(config.getAxisColor());
		canvas.setFont(config.getLabelFont());
		canvas.drawLine(0, y0, width, y0);

		if (config.getTickDistance() != 0) {
			int firstDisplayedIndex = Math.max(0, lastSampleIndex - sampleCount);
			int tickLocation = config.getTickDistance() - 
				(firstDisplayedIndex % config.getTickDistance());
			int tickPixel = pointToX(tickLocation);

			while (tickPixel < width) {
				canvas.drawLine(tickPixel, y0, tickPixel, y0 + 2);
				if (config.isTickLabelLocation(tickLocation + firstDisplayedIndex))
					canvas.drawString(new Integer(tickLocation + firstDisplayedIndex).toString(),
														tickPixel, y0 + 3 + config.getLabelFont().getSize());
				tickLocation += config.getTickDistance();
				tickPixel = pointToX(tickLocation);
			}
		}				

		/* Graph label */
		canvas.drawString(config.getLabelValue(), 1, y0 - 2);

		canvas.setColor(config.getLineColor());

		/* Create a lock on the series list so that the series
		 * cannot be updated while we are drawing */
		synchronized (series) {
			if (series.size() > 0) {
				int lastY = pointToY(series.get(0));
				int lastX = pointToX(0);

				/* Loop through the points in X, connecting them as we go. */
				for (int i = 0; i < series.size(); i++) {
					int y = pointToY(series.get(i));
					int x = pointToX(i);

					canvas.drawLine(lastX, lastY, x, y);
					if (config.getEnableInspector() &&
							currentFocusVertical > lastX && currentFocusVertical <= x) {
						canvas.setColor(config.getInspectorColor());

						canvas.drawOval(x-2, y-2, 4, 4);

						canvas.drawLine(x, y, 0, y);
						canvas.drawString(series.get(i).toString(), 1, y - 2);
						canvas.drawLine(x, y, x, y0);
						canvas.drawString(new Integer(Math.max(0, lastSampleIndex - sampleCount) + i).toString(),
															x + 2, y0 - 3);

						canvas.setColor(config.getLineColor());
					}

					lastY = y;
					lastX = x;
				}
			}
		}
	}

	private class GraphMouseHandler extends MouseAdapter implements MouseMotionListener {
		public void mouseExited(MouseEvent e) {
			if (config.getEnableInspector()) {
				currentFocusVertical = (int) (0.75 * width) - 1;
				repaint();
			}
		}

		public void mouseMoved(MouseEvent e) {
			if (config.getEnableInspector()) {
				currentFocusVertical = e.getX();
				repaint();
			}
		}

		public void mouseDragged(MouseEvent e) {
			;
		}
	}
}