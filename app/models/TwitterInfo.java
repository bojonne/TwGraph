package models;

import ccx4700.twitter.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import javax.imageio.ImageIO;
import controllers.*;

import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import twitter4j.UserStreamAdapter;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;


public class TwitterInfo {
	static final boolean DEBUG_PRINT = false;
	static final boolean PRINT = true;

	private Twitter twitter = null;
	private TwitterStream twitterStream = null; // 一応
	static final int MAX_EDGE = 5;
	static final int SUB_EDGE_MIN = 1;

	private long oauthId = -1;
	private String oauthName = null;
	private HashSet<Long> nodes;
	private HashSet<Long> coreNodes;
	private HashMap<String,Long> nameMap;
	private HashMap<Long, User> userCache;

	private HashSet<Long> oldStatus;
	private HashMap<Long, long[]> tempEdge;

	public TwitterInfo(Twitter twitter, TwitterStream twitterStream) {
		this.twitter = twitter;
		this.twitterStream = twitterStream;
		userCache = new HashMap<Long, User>();
		nodes = new HashSet<Long>();
		coreNodes = new HashSet<Long>();
		nameMap = new HashMap<String,Long>();
		tempEdge = new HashMap<Long, long[]>();
		oldStatus = new HashSet<Long>();

		try {
			oauthId = twitter.getId();
		} catch (Exception e) {
			System.out.println("fatal error : invalid twitter account.");
		}
		UserStreamAdapter userAdapter = new UserStreamAdapter(){
			public void onStatus(Status status) {
				recieveStatus(status);
			}
		};
		twitterStream.addListener(userAdapter);
	}

	private void makeNode(User user) {
		long id = user.getId();
		if(!nodes.contains(id)) {
			nodes.add(id);
			TwitterMng.publishNode(oauthId, user);
		} else {
			System.out.println("makeNode : ERROR (existing node added.)");
		}
	}
	
	private void makeEdge(String fromName, String toName, int score, boolean mention) {
		TwitterMng.publishEdge(oauthId, fromName, toName, score);
		if (mention) {
			TwitterMng.publishMention(oauthId, fromName, toName);
		}
	}
	
	public long getId() {
		return oauthId;
	}

	public void startStream() {
		twitterStream.user();
	}

	public void initGraph() {
		addUser(oauthId);
		User oauthUser = userCache.get(oauthId);
		oauthName = oauthUser.getScreenName();
		makeNode(oauthUser);

		makeGraph(oauthUser, getMentionScore());
	}

    
	public void extendGraph(String screenName) {
		long id = nameMap.get(screenName);
		User user = userCache.get(id);
		makeGraph(user, getMentionScore(id));
		//coreNodes.add(id);
		TwitterMng.publishEndExtend(oauthId);
	}

	private void makeGraph(User center, HashMap<Long, Integer> mentionScore) {
		coreNodes.add(center.getId());
		
		String centerName = center.getScreenName();
		ArrayList<Integer> topScore = new ArrayList<Integer>();
		for(long id : mentionScore.keySet()) {
			int score = mentionScore.get(id);
			if(!nodes.contains(id)) {
				if(topScore.size() < MAX_EDGE) {
					topScore.add(score);
				} else if(score > topScore.get(0)) {
					topScore.remove(0);
					topScore.add(score);
				}
				Collections.sort(topScore);
			} else {
				makeEdge(centerName, userCache.get(id).getScreenName(), score, false);
			}
		}

		int edgeNum = 0;
		for(long id : mentionScore.keySet()) {
			int score = mentionScore.get(id);
			if(topScore.contains(score)) {
				User friend = userCache.get(id);
				makeNode(friend);
				makeEdge(centerName, friend.getScreenName(), score, false);
				if(++edgeNum == MAX_EDGE) {
					break;
				}
			}
		}
		
		HashMap<Long, long[]> subEdge = new HashMap<Long, long[]>();
		HashMap<Long, Integer> subEdgeScore = new HashMap<Long, Integer>();
		for(long statusId : tempEdge.keySet()) {
			long[] ids = tempEdge.get(statusId);
			if(nodes.contains(ids[0]) && nodes.contains(ids[1])
				&& (coreNodes.contains(ids[0]) || coreNodes.contains(ids[1]))) {
				long id = ids[0]*ids[1];
				if (subEdge.containsKey(id)) {
					subEdgeScore.put(id, subEdgeScore.get(id)+1);
				} else {
					subEdge.put(id, ids);
					subEdgeScore.put(id, 1);
				}
				oldStatus.add(statusId);
			}
		}
		for(long id : subEdge.keySet()) {
			long[] ids = subEdge.get(id);
			int score = subEdgeScore.get(id);
			if(score >= SUB_EDGE_MIN) {
				makeEdge(userCache.get(ids[0]).getScreenName(), userCache.get(ids[1]).getScreenName(), score, false);
			}
		}
		tempEdge = new HashMap<Long, long[]>();
		
	}

	/**
	 * ストリーム受信
	 */
	private void recieveStatus(Status status) {
		User fromUser = status.getUser();
		long fromId = fromUser.getId();
		String fromName = fromUser.getScreenName();
		addUser(fromUser);
		
		if(fromId == oauthId) {
			for(UserMentionEntity entity : status.getUserMentionEntities()) {
				long toId = entity.getId();
				addUser(toId);
				if(fromId != toId) {
					if(!nodes.contains(toId)) {
						makeNode(userCache.get(toId));
					}
					makeEdge(oauthName, userCache.get(toId).getScreenName(), 1, true);
				}
			}
		} else {
			for(UserMentionEntity entity : status.getUserMentionEntities()) {
				long toId = entity.getId();
				if(fromId != toId) {
					if(toId == oauthId) {
						if(!nodes.contains(fromId)) {
							makeNode(fromUser);
						}
						makeEdge(fromName, oauthName, 1, true);
					} else if (nodes.contains(fromId) || nodes.contains(toId)) {
						if(!nodes.contains(fromId)) {
							makeNode(fromUser);
						}
						if(!nodes.contains(toId)) {
							addUser(toId);
							makeNode(userCache.get(toId));
						}
						makeEdge(fromName, userCache.get(toId).getScreenName(), 1, true);
					}
                }
			}
		}
		String text = status.getText();
		TwitterMng.publishMessage(oauthId, fromName, text);
		if (nodes.contains(fromId)) {
			TwitterMng.publishBalloon(oauthId, fromName, text);
		}
	}

	public HashMap<Long, Integer> getMentionScore() {
		return getMentionScore(oauthId);
	}

	public HashMap<Long, Integer> getMentionScore(long userId) {
		HashMap<Long, Integer> mentionScore = new HashMap<Long, Integer>();
		HashMap<Long, Integer> outMention = getOutMention(userId);
		HashMap<Long, Integer> inMention = null;
		if (userId == oauthId) {
			inMention = getInMention();
		} else {
			inMention = getInMention(userId, outMention.keySet());
		}

		for (long id : outMention.keySet()) {
			if (inMention.containsKey(id)) {
				int score = (int)Math.round(Math.sqrt(outMention.get(id) * inMention.get(id) + 0.0));
				mentionScore.put(id, score);
			}
		}

		return mentionScore;
	}

	public HashMap<Long, Integer> getOutMention() {
		return getOutMention(oauthId);
	}

	public HashMap<Long, Integer> getOutMention(long userId) {
		HashMap<Long, Integer> outMention = new HashMap<Long, Integer>();
		HashSet<Long> tempIdSet = new HashSet<Long>();
		try {
			ResponseList<Status> timeline = twitter.getUserTimeline(userId, new Paging(1,200));
			addUser(timeline.get(0).getUser());
			for (Status status : timeline) {
				long statusId = status.getId();
				if (!oldStatus.contains(statusId) && !status.isRetweet()) {
					for (UserMentionEntity entity : status.getUserMentionEntities()) {
						long toId = entity.getId();
						if (toId != userId) {
							oldStatus.add(statusId);
							tempIdSet.add(toId);
							addIdCount(toId, outMention);
							if (DEBUG_PRINT) {
								System.out.println(status.getCreatedAt() + " : " + userId + " --> " + toId);
							}
						}
					}
				}
			}
		} catch (TwitterException e) {
			if (e.getRateLimitStatus().getRemainingHits() == 0) {
				System.out.println("getOutMention : Twitter.getUserTimeline failed. (probably rate limiting.)");
			} else {
				System.out.println("getOutMention : Twitter.getUserTimeline failed. (probably id=" + userId + " is locked.)");
			}
		}
		addUserFromId(tempIdSet);
		return outMention;
	}
	
	public HashMap<Long, Integer> getInMention() {
		HashMap<Long, Integer> inMention = new HashMap<Long, Integer>();
		try {
			ResponseList<Status> timeline = twitter.getMentions(new Paging(1,200));
			for (Status status : timeline) {
				long statusId = status.getId();
				if (!oldStatus.contains(statusId)) {
					User user = status.getUser();
					long fromId = user.getId();
					addUser(user);
					for (UserMentionEntity entity : status.getUserMentionEntities()) {
						long toId = entity.getId();
						if (toId == oauthId) {
							oldStatus.add(statusId);
							addIdCount(fromId, inMention);
							if (DEBUG_PRINT) {
								System.out.println(status.getCreatedAt() + " : " + oauthId + " <-- " + fromId);
							}
						} else if (toId != fromId) {
							long[] ids = {fromId, toId};
							tempEdge.put(statusId, ids);
						}
					}
				}
			}
		} catch (TwitterException e) {
			if (e.getRateLimitStatus().getRemainingHits() == 0) {
				System.out.println("getInMention : Twitter.getMentions failed. (probably rate limiting.)");
			} else {
				e.printStackTrace();
			}
		}

		return inMention;
	}

	public HashMap<Long, Integer> getInMention(long userId, Set<Long> outMentionIds) {
		HashMap<Long, Integer> inMention = new HashMap<Long, Integer>();
		for (long friendId : outMentionIds){
			try {
				ResponseList<Status> timeline = twitter.getUserTimeline(friendId, new Paging(1,200));
				for (Status status : timeline) {
					long statusId = status.getId();
					if (!oldStatus.contains(statusId)) {
						addUser(status.getUser());
						for (UserMentionEntity entity : status.getUserMentionEntities()) {
							long toId = entity.getId();
							if (toId == userId) {
								addIdCount(friendId, inMention);
								if (DEBUG_PRINT) {
									System.out.println(status.getCreatedAt() + " : " + userId + " <-- " + friendId);
								}
								oldStatus.add(statusId);
							} else if (toId != friendId) {
								long[] ids = {friendId, toId};
								tempEdge.put(statusId, ids);
							}
						}
					}
				}
			} catch (TwitterException e) {
				if (e.getRateLimitStatus().getRemainingHits() == 0) {
					System.out.println("getInMention : Twitter.getUserTimeline failed. (probably rate limiting.)");
				} else {
					System.out.println("getInMention : Twitter.getUserTimeline failed. (probably id=" + friendId + " is locked.)");
				}
			}
		}
		return inMention;
	}

	private void addIdCount(long id, HashMap<Long, Integer> mentionMap) {
		int count = 1;
		if (mentionMap.containsKey(id)) {
			count += mentionMap.get(id);
		}
		mentionMap.put(id, count);
	}

	private void addUser(User user) {
		long id = user.getId();
		if(!userCache.containsKey(id)) {
			nameMap.put(user.getScreenName(), id);
			userCache.put(id, user);
		}
	}

	private void addUser(long id) {
		if(!userCache.containsKey(id)) {
			try {
				addUser(twitter.showUser(id));
			} catch (TwitterException e) {
				if (e.getRateLimitStatus().getRemainingHits() == 0) {
					System.out.println("addUser : Twitter.showUser failed. (probably rate limiting.)");
				} else {
					System.out.println("addUser : Twitter.showUser failed. (probably id=" + id + " is locked.)");
				}
			}
		}
	}

	private void addUserFromId(HashSet<Long> ids) {
		long[] idArray = new long[ids.size()];
		int i=0;
		for(long id : ids) {
			idArray[i++] = id;
		}
		try {
			for (User user : twitter.lookupUsers(idArray)) {
				addUser(user);
			}
		} catch (TwitterException e) {
			System.out.println("addUserFromId : Twitter.lookupUsers failed. (probably rate limiting.)");
		}
	}
}
