package dosn.recommendation.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import dosn.database.facade.DatabaseInteractionLayer;
import dosn.recommendation.logic.LocalRecommendation;
import dosn.recommendation.logic.PotentielServerConnectionResultListener;
import dosn.recommendation.logic.PotentielServerConnectionThread;
import dosn.recommendation.logic.SendResponseThread;
import dosn.utility.general.PropertiesLookup;
import dosn.utility.json.RRequestJSON;
import dosn.utility.json.RResponseJSON;
import dosn.utility.json.UserRecommendationJSON;

/**
 * This class is responsible for managing the recommendation requests so this
 * Recommendation Servlet Handles /startRecommendation and
 * /recommendAndPropagate
 * 
 */

@Controller
@Scope(value = "singleton")
public class RecommendationServlet {

	public HashMap<String, Date> requestMap = new HashMap<String, Date>();

	@Inject
	LocalRecommendation localRecommendation;

	@Inject
	DatabaseInteractionLayer databaseInteractionLayer;

	/**
	 * Retrieve Request from local GUI Module to start Recommendation for User
	 * and Interests
	 * 
	 * @param request
	 * @param requestJSON
	 */
	@RequestMapping(value = { "/startRecommendation" }, method = RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void startRecommendation(HttpServletRequest request,
			@RequestBody RRequestJSON requestJSON) {

		StopWatch stopWatch = new StopWatch();
		stopWatch.start("Recommendation");
		// System.out.println("start recommendation process");

		// Read all Parameter from Request
		String responseURI = requestJSON.getResponseURI();
		List<String> interests = requestJSON.getInterests();
		String msgID = requestJSON.getMsgID();
		String userID = requestJSON.getUserID();
		Integer friendLevel = requestJSON.getFriendLevel();
		Integer maxFriendLevel = requestJSON.getMaxFriendLevel();
		Integer maxResults = requestJSON.getMaxResults();
		Boolean friendsInculded = requestJSON.getIncludeFriends();
		Double minSimilarityScore = requestJSON.getMinSimilarityScore();
		Integer maxRequestPerHop = PropertiesLookup.getMaxRequests();

		List<String> localPotentialServer = databaseInteractionLayer
				.retrievePotentielServers();

		// Create Response Object --> we merge all incoming Responses in this
		// Object to build up a finial Result to send to GUI
		RResponseJSON recommendationResults = new RResponseJSON(msgID);

		// First Round of Recommendation Algorithm based on Friends Location of
		// user
		List<String> potentialServer = databaseInteractionLayer
				.retrieveFriendServer(userID);
		// System.out.println("Friend Server:" + potentialServer.toString());

		// start first Round with server of Friends, then extend the potential
		// Server List
		for (int i = 0; i < PropertiesLookup.getMaxHops(); i++) {
			if (stopWatch.getTotalTimeSeconds() > PropertiesLookup
					.getMaxRuntime()) {
				break;
			}

			recommendationResults.mergeResponse(propagateRequest(
					potentialServer, interests, msgID, userID));
			recommendationResults.mergePotentialServer(localPotentialServer);
			potentialServer = recommendationResults
					.buildRandomPotentialServer(maxRequestPerHop);
			// System.out.println("New Potential Server:" +
			// potentialServer.toString());

			// send incoming Results to GUI
			// send only user matching Recommendation Parameter:
			// -->
			// recommendationResults.getUserJSON(maxResults,minSimilarityScore,friendsInculded)
			SendResponseThread sendResultsToGUI = new SendResponseThread(
					responseURI, msgID, recommendationResults.getUserJSON(
							maxResults, minSimilarityScore, friendsInculded));
			sendResultsToGUI.start();
			recommendationResults.mergeVisitedServer(potentialServer);

		}

		// System.out.println("Final Recommendation Results:" +
		// recommendationResults.toString());
		// stopWatch.stop();
		// System.out.println(stopWatch.prettyPrint());

	}

	/**
	 * Recursive Method to reach friend of friends Each server extends response
	 * by: - matching User - visited Server(own server location) - potential
	 * Server(known server) Server Propagates Request to other friends that are
	 * friend of requesting user, changing request Server waits for all
	 * outstanding Requests he made and sending 1 merged response back to server
	 * the initial request come from
	 * 
	 * @param request
	 * @param requestJSON
	 * @return
	 */
	@RequestMapping(value = { "/recommendAndPropagate" }, method = RequestMethod.POST)
	@ResponseBody
	public RResponseJSON doPost(HttpServletRequest request,
			@RequestBody RRequestJSON requestJSON) {

		List<String> interests = requestJSON.getInterests();
		String msgID = requestJSON.getMsgID();
		String userID = requestJSON.getUserID();
		Integer friendLevel = requestJSON.getFriendLevel();
		Integer maxFriendLevel = requestJSON.getMaxFriendLevel();

		// Create Response
		RResponseJSON responseMessage = new RResponseJSON(msgID);

		// add server URL to visited server, so we don't have to make a request
		// twice
		responseMessage.getVisitedServers()
				.add(PropertiesLookup.getServerUrl());

		// System.out.println("/recommendAndPropagate " +
		// requestJSON.toString());

		Boolean processingRequest = true;
		// check, if request should processed or not
		if (!requestMap.containsKey(msgID)) {
			// first Request, save msgId and Date
			requestMap.put(msgID, new Date());
			// do public scoring here once!!
			List<UserRecommendationJSON> localUsers = localRecommendation
					.findUserByPublicInterests(interests);
			// System.out.println("local user: " + localUsers.toString() +
			// " for interests: " + interests);
			responseMessage.setUsers(localUsers);

		} else {
			// System.out.println("stop recommendation for public level");
			if (friendLevel.equals(0)) {
				processingRequest = false;
			}
		}
		// }

		// stop if second msg with same msgId for friendLvl=0 (public Interest
		// Search)
		if (processingRequest) {
			// get Friends for User
			List<String> friendOfUser = databaseInteractionLayer
					.retriveFriendsByUser(userID);

			// next search for all Friends of FriendsOfUser
			// Save as Entry(User,FriendOfUser)
			List<Entry<String, String>> friendsOfFriend = databaseInteractionLayer
					.retriveFriendsOfFriend(friendOfUser);

			// increase FriendLevel++
			friendLevel++;

			// System.out.println("Friends found:" + friendOfUser.toString());
			// System.out.println("Friends of Friends to propagate Request:" +
			// friendsOfFriend.toString());

			// search there Interests based on FriendLevel (Me=0,Friend=1,
			// FriendOfFriend=2)
			List<UserRecommendationJSON> foundUserList = localRecommendation
					.findUserBasedOnFriendshipRelation(interests, friendLevel,
							friendOfUser);
			// add user to resultMessage

			responseMessage.mergeUser(foundUserList);
			// System.out.println("merged friends" +
			// responseMessage.getUsers().toString());

			responseMessage.mergeResponse(propagateRequest(friendsOfFriend,
					interests, msgID, friendLevel, maxFriendLevel));
			// System.out.println("merged all Responses from all friends" +
			// responseMessage.getUsers().toString());

			// Add potential Server to Response here
			responseMessage
					.mergePotentialServer(databaseInteractionLayer
							.retrievePotentielServers(PropertiesLookup
									.getMaxRequests()));

		}

		responseMessage.mergePotentialServer(databaseInteractionLayer
				.retrievePotentielServers());
		// System.out.println(responseMessage.getUsers());
		return responseMessage;
	}

	/**
	 * Propagate the Request to server of friends, replace the request userName
	 * with the name of friendOfUser, increased friendLevel
	 * 
	 * @param users
	 * @param interests
	 * @param msgID
	 * @param friendLevel
	 * @param maxFriendLevel
	 * @return
	 */
	public RResponseJSON propagateRequest(List<Entry<String, String>> users,
			List<String> interests, String msgID, Integer friendLevel,
			Integer maxFriendLevel) {
		PotentielServerConnectionResultListener resultListener = new PotentielServerConnectionResultListener(
				users.size());

		if (friendLevel < maxFriendLevel) {

			String serviceURI = PropertiesLookup
					.getRecommendationAndPropagateUri();
			List<Thread> threads = new ArrayList<Thread>();
			for (Entry<String, String> friend : users) {
				String friendServer = databaseInteractionLayer
						.retriveServerByFriendName(friend.getValue());

				if (!friendServer.endsWith("/"))
					friendServer += "/";
				Thread thread = null;

				thread = new PotentielServerConnectionThread(friendServer
						+ serviceURI, interests, friend.getKey(), msgID,
						friendLevel, maxFriendLevel, resultListener);

				thread.start();
				threads.add(thread);
			}

			// wait until all requests send a response
			while (threads.size() != resultListener.getFinishedThreads().size()) {
				synchronized (resultListener) {
					try {
						resultListener.wait();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			// the final response is the merged response of all requested server
			return resultListener.getResponseMessage();

		} else {
			return null;
		}

	}

	/**
	 * This Method is call in each Iteration Step from startRecommendation
	 * Method to connect to new Server and ask for User with Public Interest
	 * Match, and new Potential Server
	 * 
	 * @param servers
	 * @param interests
	 * @param msgID
	 * @param userId
	 * @return
	 */
	public RResponseJSON propagateRequest(List<String> servers,
			List<String> interests, String msgID, String userId) {
		PotentielServerConnectionResultListener resultListener = new PotentielServerConnectionResultListener(
				servers.size());

		String serviceURI = PropertiesLookup.getRecommendationAndPropagateUri();
		List<Thread> threads = new ArrayList<Thread>();
		for (String server : servers) {

			if (!server.endsWith("/"))
				server += "/";
			Thread thread = null;

			thread = new PotentielServerConnectionThread(server + serviceURI,
					interests, userId, msgID, 0, 2, resultListener);

			thread.start();
			threads.add(thread);
		}

		// wait until all requests send a response
		while (threads.size() != resultListener.getFinishedThreads().size()) {
			synchronized (resultListener) {
				try {
					resultListener.wait();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		// the final response is the merged response of all requested server
		return resultListener.getResponseMessage();

	}

}
