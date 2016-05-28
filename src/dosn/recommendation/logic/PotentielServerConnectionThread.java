package dosn.recommendation.logic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import dosn.utility.general.Helper;
import dosn.utility.json.RResponseJSON;
/**
 * 
 * This is a thread class to open multiple connections at the same time to the
 * potential servers for asking to recommendation
 * 
 */

public class PotentielServerConnectionThread extends Thread {

	private RResponseJSON responseMessage = null;
	private String url = null;
	private List<String> interests = null;
	private String userID = null;
	private String messageUID = null;
	private Integer maxHops = null;
	private Integer friendLevel = null;
	private Integer maxFriendLevel = null;
	private PotentielServerConnectionResultListener resultListener = null;

	public PotentielServerConnectionThread(String url, List<String> interests,
			String userID, String messageUID, Integer friendLevel,
			Integer maxFriendLevel,
			PotentielServerConnectionResultListener resultListener) {
		super();
		this.url = url;
		this.interests = interests;
		this.userID = userID;
		this.messageUID = messageUID;
		this.friendLevel = friendLevel;
		this.maxFriendLevel = maxFriendLevel;
		this.resultListener = resultListener;
	}

	@Override
	public void run() {
		RResponseJSON recommendationResponse = sendRequestConnectionType();

		resultListener.getResponseMessage().mergeResponse(
				recommendationResponse);

		resultListener.addFinishedThread(getId());
	}

	private RResponseJSON sendRequestConnectionType() {
		if (interests == null) {
			return null;
		}

		// connect to server and wait for response
		try {
			URL urlPath = new URL(url);
			HttpURLConnection connection = (HttpURLConnection) urlPath
					.openConnection();
			connection.setDoInput(Boolean.TRUE);
			connection.setDoOutput(Boolean.TRUE);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestMethod("POST");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);

			OutputStreamWriter out = new OutputStreamWriter(
					connection.getOutputStream());
			out.write(Helper.buildJSONRecommendationRequest("", interests,
					messageUID, userID, friendLevel, maxFriendLevel));
			out.close();

			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(connection.getInputStream()));
			StringBuilder stringBuilder = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line + "\n");
			}
			bufferedReader.close();
			System.out.println("succecfully connected to server: " + url);
			return Helper
					.getJSONRecommendationRespone(stringBuilder.toString());

		} catch (Exception e) {
			System.out.println("connection timeout for server: " + url);
			return null;
		}

	}

	public RResponseJSON getResponseMessage() {
		return responseMessage;
	}

	public void setResponseMessage(RResponseJSON responseMessage) {
		this.responseMessage = responseMessage;
	}

}
