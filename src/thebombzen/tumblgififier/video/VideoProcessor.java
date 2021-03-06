package thebombzen.tumblgififier.video;

import static thebombzen.tumblgififier.TumblGIFifier.log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import thebombzen.tumblgififier.gui.MainFrame;
import thebombzen.tumblgififier.util.ConcurrenceManager;
import thebombzen.tumblgififier.util.io.IOHelper;
import thebombzen.tumblgififier.util.io.resources.ProcessTerminatedException;
import thebombzen.tumblgififier.util.io.resources.Resource;
import thebombzen.tumblgififier.util.io.resources.ResourcesManager;
import thebombzen.tumblgififier.util.text.StatusProcessor;
import thebombzen.tumblgififier.util.text.StatusProcessorWriter;
import thebombzen.tumblgififier.util.text.TextHelper;

public class VideoProcessor {

	public VideoProcessor(VideoScan scan) {
		this.scan = scan;
	}

	private VideoScan scan;

	public VideoScan getScan() {
		return scan;
	}

	public void setScan(VideoScan scan) {
		this.scan = scan;
	}

	private Path gifFile;
	private Path nutFile;
	private Path paletteFile;

	private int decimator;

	private double highscale = 1D;
	private double lowscale = 0D;
	private long maxSize;
	private long minSize;
	private int targetWidth;
	private int targetHeight;
	private double scale = 1D;

	private double clipStartTime;
	private double clipEndTime;

	private StatusProcessor statusProcessor;

	private int prevWidth = -1;
	private int prevPrevWidth = -2;
	private int prevHeight = -1;
	private int prevPrevHeight = -2;

	private void adjustScale() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("Checking Filesize... ");
		long currFileSize;
		currFileSize = Files.size(gifFile);
		if (currFileSize > maxSize) {
			sb.append("Too Big: ");
			highscale = scale;
			scale = (lowscale + highscale) * 0.5D;
		} else if (currFileSize < minSize && scale < 1D) {
			sb.append("Too Small: ");
			lowscale = scale;
			scale = (lowscale + highscale) * 0.5D;
		} else {
			sb.append("Just Right: ");
		}
		sb.append(String.format("%d%n", currFileSize));
		this.statusProcessor.appendStatus(sb.toString());
	}

	public boolean convert(String overlay, StatusProcessor outputProcessor, Path path, double startTime, double endTime,
			long minSize, long maxSize, int targetWidth, int targetHeight, int decimator, int overlaySize) {
		MainFrame.getMainFrame().setBusy(true);
		boolean success = true;
		try {
			convert0(overlay, outputProcessor, path, startTime, endTime, minSize, maxSize, targetWidth, targetHeight,
					decimator, overlaySize);
		} catch (IOException ioe) {
			log(ioe);
			success = false;
		}
		IOHelper.deleteTempFile(gifFile);
		IOHelper.deleteTempFile(nutFile);
		IOHelper.deleteTempFile(paletteFile);
		MainFrame.getMainFrame().setBusy(false);
		return success;
	}

	private void convert0(String overlay, StatusProcessor outputProcessor, Path path, double startTime, double endTime,
			long minSize, long maxSize, int targetWidth, int targetHeight, int decimator, int overlaySize)
			throws IOException {
		this.statusProcessor = outputProcessor;
		this.clipStartTime = startTime;
		this.clipEndTime = endTime;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.decimator = decimator;
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;

		lowscale = 0D;
		scale = minSize <= 0 ? 1D
				: 1D / Math.sqrt(scan.getWidth() * scan.getHeight() * scan.getFramerate() / (1D + decimator)
						* (endTime - startTime) / (3D * maxSize));
		if (scale > 1D) {
			scale = 1D;
		}
		highscale = 1D;

		this.gifFile = IOHelper.createTempFile();
		this.nutFile = IOHelper.createTempFile();
		this.paletteFile = IOHelper.createTempFile();

		prevWidth = -1;
		prevHeight = -1;
		prevPrevWidth = -2;
		prevPrevHeight = -2;

		long gifLength = Files.size(gifFile);

		while (gifLength == 0 || (gifLength < minSize && scale < 1) || gifLength > maxSize) {
			createGif(overlay, overlaySize);
			gifLength = Files.size(gifFile);
			adjustScale();
			int newWidth = (int) (scan.getWidth() * scale);
			int newHeight = (int) (scan.getHeight() * scale);
			if (newWidth == prevWidth && newHeight == prevHeight
					|| newWidth == prevPrevWidth && newHeight == prevPrevHeight) {
				statusProcessor.appendStatus("Exiting Loop.");
				break;
			}
			prevPrevWidth = prevWidth;
			prevPrevHeight = prevHeight;
			prevWidth = newWidth;
			prevHeight = newHeight;
		}

		Files.copy(gifFile, path, StandardCopyOption.REPLACE_EXISTING);

	}

	private void createGif(String overlay, int overlaySize) throws IOException {
		int newWidth, newHeight;

		if (targetWidth > 0) {
			newWidth = targetWidth;
			scale = (double) newWidth / (double) scan.getWidth();
			newHeight = (int) Math.ceil(scan.getHeight() * scale);
		} else if (targetHeight > 0) {
			newHeight = targetHeight;
			scale = (double) newHeight / (double) scan.getHeight();
			newWidth = (int) Math.ceil(scan.getWidth() * scale);
		} else {
			newWidth = (int) Math.ceil(scan.getWidth() * scale);
			newHeight = (int) Math.ceil(scan.getHeight() * scale);
		}

		PrintWriter writer = new PrintWriter(new StatusProcessorWriter(statusProcessor), true);

		writer.format("Testing Size: %dx%d%n%n", newWidth, newHeight);

		writer.print("Scaling Video... \r");

		writer.flush();

		Resource mpv = ResourcesManager.getMpvLocation();

		String videoFilter = TextHelper.getTextHelper().createVideoFilter(null, "format=bgr0", newWidth, newHeight,
				false, decimator, scan.getWidth(), scan.getHeight(), overlaySize, overlay);

		try {
			scanPercentDone("Scaling Video... ", clipStartTime, clipEndTime - clipStartTime, writer,
					ConcurrenceManager.exec(false, mpv.getLocation().toString(), scan.getLocation().toString(),
							"--config=no", "--msg-level=all=v", "--msg-color=no",
							"--log-file=" + ResourcesManager.getLocalFile("mpv-scale.log"), "--input-terminal=no",
							"--aid=no", "--sid=no", "--oautofps", "--of=nut", "--ovc=ffv1", "--correct-downscaling",
							"--scale=spline36", "--dscale=spline36", "--cscale=spline36",
							"--term-status-msg=${=playback-time}", "--sws-scaler=spline",
							"--lavfi-complex=sws_flags=spline; [vid1]" + videoFilter + "[vo]",
							"--start=" + this.clipStartTime, "--end=" + this.clipEndTime,
							"--o=" + this.nutFile.toString()));
		} catch (ProcessTerminatedException ex) {
			writer.println("Scaling Video... Error.");
			ConcurrenceManager.stopAll();
			IOHelper.closeQuietly(writer);
			throw ex;
		}

		writer.println("Scaling Video... Done.");

		writer.print("Generating Palette... \r");
		writer.flush();

		try {
			ConcurrenceManager.exec(false, true, mpv.getLocation().toString(), this.nutFile.toString(), "--config=no",
					"--msg-level=all=v", "--msg-color=no",
					"--log-file=" + ResourcesManager.getLocalFile("mpv-palettegen.log"), "--input-terminal=no",
					"--aid=no", "--sid=no", "--oautofps", "--of=image2", "--ovc=png",
					"--lavfi-complex=[vid1]palettegen=max_colors=144[vo]", "--o=" + this.paletteFile.toString());
		} catch (ProcessTerminatedException ex) {
			writer.println("Generating Palette... Error.");
			ConcurrenceManager.stopAll();
			IOHelper.closeQuietly(writer);
			throw ex;
		}

		writer.println("Generating Palette... Done.");

		writer.print("Generating GIF... \r");

		try {
			scanPercentDone("Generating GIF... ", 0D, clipEndTime - clipStartTime, writer,
					ConcurrenceManager.exec(false, mpv.getLocation().toString(), this.paletteFile.toString(),
							"--external-file=" + this.nutFile.toString(), "--config=no", "--msg-level=all=v",
							"--msg-color=no", "--log-file=" + ResourcesManager.getLocalFile("mpv-paletteuse.log"),
							"--input-terminal=no", "--aid=no", "--sid=no", "--oautofps", "--of=gif", "--ovc=gif",
							"--term-status-msg=${=playback-time}",
							"--lavfi-complex=[vid2][vid1]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle[vo]",
							"--o=" + this.gifFile.toString()));
		} catch (ProcessTerminatedException ex) {
			writer.println("Generating GIF... Error.");
			ConcurrenceManager.stopAll();
			IOHelper.closeQuietly(writer);
			throw ex;
		}

		writer.println("Generating GIF... Done.");

		if (ResourcesManager.loadedPkgs.contains("gifsicle")) {
			try {
				Resource gifsicle = ResourcesManager.getXLocation("gifsicle", "gifsicle");
				writer.print("Crushing GIF... \r");
				ConcurrenceManager.exec(true, true, gifsicle.getLocation().toString(), "--batch", "--unoptimize",
						"--optimize=3", this.gifFile.toString());
				writer.println("Crushing GIF... Done.");
			} catch (ProcessTerminatedException ex) {
				writer.println("Crushing GIF... Error.");
				ConcurrenceManager.stopAll();
				writer.close();
				throw ex;
			}
		}

		writer.close();
	}

	private static void scanPercentDone(String prefix, double startOffset, double length, PrintWriter writer,
			InputStream in) {
		BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		br.lines().forEachOrdered(line -> {
			double realTime;
			try {
				realTime = Double.parseDouble(line);
			} catch (NumberFormatException nfe) {
				return;
			}
			double percent = (realTime - startOffset) * 100D / length;
			writer.format("%s%.2f%%\r", prefix, percent);
		});
	}

}
