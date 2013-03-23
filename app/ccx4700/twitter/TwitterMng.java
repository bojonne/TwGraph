package ccx4700.twitter;

import java.util.*;

import play.libs.*;
import play.libs.F.*;
import twitter4j.Status;
import twitter4j.User;
import twitter4j.UserMentionEntity;

import models.*;

public class TwitterMng {
	static private HashMap<Long, TwitterInfo> infoMap = new HashMap<Long, TwitterInfo>();
	//イベントストリームを管理する
	final static ArchivedEventStream<TwitterMng.Event> TwitterEvents = new ArchivedEventStream<TwitterMng.Event>(100);

	public void addInfo(TwitterInfo info) {
		try {
			long id = info.getId();
			infoMap.put(id, info);
			System.out.println("num of info = " + infoMap.size());
			info.initGraph();
			info.startStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void removeInfo(long id) {
		infoMap.remove(id);
	}

	public TwitterInfo getInfo(long id) {
		return infoMap.get(id);
	}

	public static void publishNode(long oauthId, User user) {
		TwitterEvents.publish(new Node(user));
	}

	public static void publishEdge(long oauthId, String fromUser, String toUser, int weight) {
		String[] names = {fromUser, toUser};
		Arrays.sort(names);
		TwitterEvents.publish(new Edge(names[0], names[1], weight));
	}

	public static void publishMessage(long oauthId, String screenName, String text) {
		TwitterEvents.publish(new Message(screenName, text));
	}

	public static void extendGraph(long oauthId, String user) {
		TwitterInfo info = infoMap.get(oauthId);
		info.extendGraph(user);
	}
	
	public static void publishBalloon(long oauthId, String userName, String text) {
		TwitterEvents.publish(new Balloon(userName, text));
	}
	
	public static void publishMention(long oauthId, String fromName, String toName) {
		TwitterEvents.publish(new Mention(fromName, toName));
	}
	
	public static void publishEndExtend(long oauthId) {
		TwitterEvents.publish(new EndExtend());
	}

    
    public EventStream<TwitterMng.Event> join(String user) {
    	TwitterEvents.publish(new Join(user));
        return TwitterEvents.eventStream();
    }

   
    public void leave(String user) {
        TwitterEvents.publish(new Leave(user));
    }

   
    public void say(String user, String text) {
        if(text == null || text.trim().equals("")) {
            return;
        }
        System.out.println("Message が来た");
        System.out.println(user);
        System.out.println(text);
        TwitterEvents.publish(new Message(user, text));
    }
   
    public Promise<List<IndexedEvent<TwitterMng.Event>>> nextMessages(long lastReceived) {
        return TwitterEvents.nextEvents(lastReceived);
    }

    public List<TwitterMng.Event> archive() {
        return TwitterEvents.archive();
    }

    public static abstract class Event {
        final public String type;
        final public Long timestamp;
        public Event(String type) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class Join extends Event {
        final public String user;
        public Join(String user) {
            super("join");
            this.user = user;
        }
    }

    public static class Leave extends Event {
        final public String user;
        public Leave(String user) {
            super("leave");
            this.user = user;
        }
    }
    
    public static class Message extends Event {
        final public String user;
        final public String text;
        public Message(String user, String text) {
            super("message");
            this.user = user;
            this.text = text;
        }
    }

    public static class Node extends Event {
        final public String screenName;
        final public String profileImageURL;
        public Node(User user) {
            super("node");
            screenName = user.getScreenName();
            profileImageURL = user.getProfileImageURL().toString().split("://")[1];
        }
    }
    
    public static class Edge extends Event {
        final public String fromName;
        final public String toName;
        final public int weight;
        public Edge(String fromName, String toName, int weight) {
            super("edge");
            this.fromName = fromName;
            this.toName = toName;
            this.weight = weight;
        }
    }

    public static class Balloon extends Event {
        final public String userName;
        final public String text;
        public Balloon(String userName, String text) {
            super("balloon");
            this.userName = userName;
            this.text = text;
        }
    }
    
    public static class Mention extends Event {
        final public String fromName;
        final public String toName;
        public Mention(String fromName, String toName) {
            super("mention");
            this.fromName = fromName;
            this.toName = toName;
        }
    }
    
    public static class EndExtend extends Event {
        public EndExtend() {
            super("endExtend");
        }
    }
}
