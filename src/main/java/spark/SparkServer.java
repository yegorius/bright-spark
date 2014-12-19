package spark;

public interface SparkServer {
	void ignite(String host, int port, String keystoreFile,
				String keystorePassword, String truststoreFile,
				String truststorePassword, String staticFilesFolder,
				String externalFilesFolder);

	void stop();
}
