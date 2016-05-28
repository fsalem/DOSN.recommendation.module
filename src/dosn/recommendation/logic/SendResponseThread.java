package dosn.recommendation.logic;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import dosn.utility.general.Helper;
import dosn.utility.json.UserJSON;

/**
 * This class represents a thread to open it for asking other servers for recommendations  
 */
public class SendResponseThread extends Thread {

	private String responseURI;
	private String msgID;
	private List<UserJSON> users;

	public SendResponseThread(String responseURI, String msgID,
			List<UserJSON> users) {
		super();
		this.responseURI = responseURI;
		this.msgID = msgID;
		this.users = users;
	}

	@Override
	public void run() {
		try {

			URL url = new URL(responseURI);
			System.out.println("url: " + url.toString());

			HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			OutputStreamWriter out = new OutputStreamWriter(
					connection.getOutputStream());
			out.write(Helper.buildJSONResponse(users, msgID));
			out.close();
			connection.connect();
			connection.getResponseCode();

		} catch (Exception e) {
			System.out
					.println("connection timeout for sending recommendation results to server: "
							+ responseURI);
		}
	}

}
