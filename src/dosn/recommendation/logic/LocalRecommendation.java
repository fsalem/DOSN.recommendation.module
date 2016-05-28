package dosn.recommendation.logic;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.springframework.stereotype.Component;

import dosn.database.entities.User;
import dosn.database.facade.DatabaseInteractionLayer;
import dosn.utility.general.PropertiesLookup;
import dosn.utility.json.UserRecommendationJSON;
import dosn.utility.userIdentifier.UserIdentifierFactory;
import dosn.utility.userIdentifier.impl.SONICUserIdentifier;
import dosn.utility.userIdentifier.impl.URIUserIdentifier;

/**
 * This class is responsible for the logic of local recommendation 
 */
@Singleton
@Component
public class LocalRecommendation {

	@Inject
	DatabaseInteractionLayer databaseInteractionLayer;

	@Inject
	SONICUserIdentifier sonicUserIdentifier;

	@Inject
	URIUserIdentifier uriUserIdentifier;

	
	/**
	 * Find all User, with matching public Interests
	 * @param interests
	 * @return
	 */
	public List<UserRecommendationJSON> findUserByPublicInterests(List<String> interests){
		List<User> users = databaseInteractionLayer
				.retrieveUsersByPublicInterests(interests);
		
		return convertToUserJSON(users,interests,0);
	}
	
	/**
	 * Find all User out of friends, with matching Interests, based on friendLevel 1=Friend  2=FriendOfFriend ... 
	 * 
	 * @param interests
	 * @param friendLevel
	 * @return
	 */
	public List<UserRecommendationJSON> findUserBasedOnFriendshipRelation(List<String> interests,Integer friendLevel,List<String> friends){
		List<User> user = new ArrayList<User>();
		for(String friend:friends){
			try{
			user.add(databaseInteractionLayer.retrieveUsersByUsername(friend).get(0));
			}catch(Exception e){
				
			}
		}
		
		List<User> users = databaseInteractionLayer
				.retrieveUsersByInterestAndFriendLevel(user,interests,friendLevel);
		
		return convertToUserJSON(users,interests,friendLevel);
	}
	


	/**
	 * This method is to build the userIds depending on SONIC or regular URI
	 * then build UserJSON objects
	 * 
	 * @param users
	 * @return
	 */
	private List<UserRecommendationJSON> convertToUserJSON(List<User> users,List<String> interests,Integer friendLevel) {
		UserIdentifierFactory userIdentifier;
		if (PropertiesLookup.isServerFollowSonic()) {
			userIdentifier = sonicUserIdentifier;
		} else {
			userIdentifier = uriUserIdentifier;
		}
		List<UserRecommendationJSON> userJSONs = new ArrayList<UserRecommendationJSON>();
		
		for (User user:users) {
			String userURI = userIdentifier.buildUserIdentifier(user);
			String userName = user.getUserName();
			Double userSimilarityScore = calculateSimilarityScore(user,interests,friendLevel);
			userJSONs.add(new UserRecommendationJSON(userURI, userName,userSimilarityScore));
		}
		return userJSONs;
	}
	
	private Double calculateSimilarityScore(User user,List<String> interests,Integer friendLevel){
		 return databaseInteractionLayer.retriveSimilarityScore(user,interests,friendLevel);
	}
}
