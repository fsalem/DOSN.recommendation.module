package dosn.recommendation.logic;

import java.util.ArrayList;
import java.util.List;

import dosn.utility.json.RResponseJSON;

/**
 * 
 * This class is the listener for the opening threads to the potential servers
 *
 */
public class PotentielServerConnectionResultListener {
	
	private RResponseJSON responseMessage = null;
	private List<Long> finishedThreads = null;
	//private List<String> jsonObjects = null;
	private Integer limitToNotify = null;
	
	public PotentielServerConnectionResultListener() {
		//jsonObjects = new ArrayList<String>();
		responseMessage = new  RResponseJSON();
		finishedThreads = new ArrayList<Long>();
	}
	
	public PotentielServerConnectionResultListener(Integer limitToNotify) {
		//jsonObjects = new ArrayList<String>();
		responseMessage = new RResponseJSON();
		finishedThreads = new ArrayList<Long>();
		this.limitToNotify = limitToNotify;
	}

//	public List<String> getJsonObjects() {
//		return jsonObjects;
//	}
//	
//	public void addJsonObject(String jsonObject) {
//		//System.out.println(this.getClass().getName()+": json object added");
//		this.jsonObjects.add(jsonObject);
//	}

	public List<Long> getFinishedThreads() {
		return finishedThreads;
	}

	public void addFinishedThread(Long finishedThread) {
		//System.out.println(this.getClass().getName()+": a thread finished");
		this.finishedThreads.add(finishedThread);
		if (this.limitToNotify != null && this.finishedThreads.size() == this.limitToNotify){
			//System.out.println(this.getClass().getName() + ": to notify");
			synchronized (this) {
				this.notify();
			}
			//System.out.println(this.getClass().getName() + ": notification DONE");
			limitToNotify = null;
		}
	}

	public RResponseJSON getResponseMessage() {
		return responseMessage;
	}

	public void setResponseMessage(RResponseJSON responseMessage) {
		this.responseMessage = responseMessage;
	}

	public void addResponseMessage(RResponseJSON response){
		if(response != null){
			 responseMessage.mergeResponse(response);
		}
		
	}
	
}
