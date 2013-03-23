package controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import ccx4700.twitter.TwitterMng;

import models.Account;
import models.TwitterInfo;

import play.*;
import play.cache.*;
import play.libs.*;
import play.libs.F.*;
import play.libs.F.Matcher.*;
import play.mvc.*;
import play.mvc.Http.*;

import static play.libs.F.*;
import static play.libs.F.Matcher.*;
import static play.mvc.Http.WebSocketEvent.*;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import twitter4j.UserStreamAdapter;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class Application extends Controller {

	// コンシューマ・キーとコンシューマ・シークレット
	static final String m_ConsumerKey = "~~~";
	static final String m_ConsumerSecret = "~~~";
	static final String m_ConsumerKeySub = "~~~";
	static final String m_ConsumerSecretSub = "~~~";

	//graph.pjs 全てのTwitterアカウントを管理するクラス
	static TwitterMng tm = new TwitterMng();

	/**
	 * ①
	 */
	public static void index() {
		boolean isLoggedIn = isLoggedIn();
		Account account = (Account) Cache.get("Account");
		render(isLoggedIn, account);
	}

	/**
	 * ② Redirect to Twitter oauth pages.
	 */
	public static void login() {
		TwitterFactory twitterFactory = new TwitterFactory();
		Twitter twitter = twitterFactory.getInstance();
		twitter.setOAuthConsumer(m_ConsumerKey, m_ConsumerSecret);

		try {
			RequestToken reqToken = twitter.getOAuthRequestToken();
			Cache.set("RequestToken", reqToken);
			Cache.set("Twitter", twitter);
			redirect(reqToken.getAuthorizationURL());
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ③ After Success Oauth. 認証後にもどって来る. ここでmodelのクラスを作るべき
	 * @throws TwitterException
	 */
	public static void oauthSuccess() throws TwitterException {
		Twitter twitter = (Twitter) Cache.get("Twitter");
		AccessToken accessToken = null;
		try {
			accessToken = twitter.getOAuthAccessToken(
					(RequestToken) Cache.get("RequestToken"),
					params.get("oauth_verifier"));
		} catch (TwitterException e) {
			e.printStackTrace();
		}

		TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
		twitterStream.setOAuthConsumer(m_ConsumerKey, m_ConsumerSecret);
		twitterStream.setOAuthAccessToken(accessToken);

		if (accessToken != null) {
			if (twitter == null) {
				System.out.println("Token Nothing");
			}

			/**
			 * ④初期グラフ作成のため、変数map及び twlistに情報を入れる
			 */
			TwitterInfo twitterInfo = new TwitterInfo(twitter, twitterStream);
			
			tm.addInfo(twitterInfo);
            /**
			 * ⑤グラフ作成のため、変数Accountに情報を入れる
			 */

			try {
				String userName = twitter.getScreenName();
				Account account = getAccount(userName);
				account.userName = userName;
				account.twitterId = twitter.getId();
				account.profileImgUrl = twitter.showUser(userName)
						.getProfileImageURL();
				account.accessToken = accessToken.getToken();
				account.accessTokenSecret = accessToken.getTokenSecret();
				account.save();
				login(account);
				Cache.set("Account", account);
				render(true, account, null, null);
			} catch (TwitterException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * WebSocket用のコントローラー
	 * @author bojonne
	 */
	public static class MentionSocket extends WebSocketController {
		public static void join(String user) {
			EventStream<TwitterMng.Event> messagesStream = tm.join(user);
			// WebSocketが開いている間はループ
			while (inbound.isOpen()) {
				// Wait for an event (either something coming on the inbound
				// socket channel, or ChatRoom messages)
				Either<WebSocketEvent, TwitterMng.Event> e = await(Promise
						.waitEither(inbound.nextEvent(),
								messagesStream.nextEvent()));
				// Case: User typed 'quit'
				for (String userMessage : TextFrame.and(Equals("quit")).match(
						e._1)) {
					tm.leave(user);
					outbound.send("quit:ok");
					disconnect();
				}
				// Case: TextEvent received on the "socket"
				for (String userMessage : TextFrame.match(e._1)) {
					System.out.println("不正解");
					tm.say(user, userMessage);
				}
				// Case: Someone joined the room
				for (TwitterMng.Join joined : ClassOf(TwitterMng.Join.class)
						.match(e._2)) {
					outbound.send("join:%s", joined.user);
				}
				// Case: New message on the chat room
				for (TwitterMng.Message message : ClassOf(
						TwitterMng.Message.class).match(e._2)) {
					//System.out.println("正解");
					//System.out.println(message.user + ":" + message.text);
					outbound.send("message:%s:%s", message.user, message.text);
				}
				// Case: Someone left the room
				for (TwitterMng.Leave left : ClassOf(TwitterMng.Leave.class)
						.match(e._2)) {
					outbound.send("leave:%s", left.user);
				}
				// Case: The socket has been closed
				for (WebSocketClose closed : SocketClosed.match(e._1)) {
					tm.leave(user);
					disconnect();
				}
				// 追加
				// Case: New node added
				for (TwitterMng.Node node : ClassOf(
						TwitterMng.Node.class).match(e._2)) {
					System.out.println("New Node : " + node.screenName);
					outbound.send("node:%s:%s", node.screenName, node.profileImageURL);
				}
				// Case: New edge added
				for (TwitterMng.Edge edge : ClassOf(
						TwitterMng.Edge.class).match(e._2)) {
					System.out.println("New Edge : " + edge.fromName + " <-> " + edge.toName + ", weight=" + edge.weight);
					outbound.send("edge:%s:%s:%d", edge.fromName, edge.toName, edge.weight);
				}
				// Case: New balloon added
				for (TwitterMng.Balloon balloon : ClassOf(
						TwitterMng.Balloon.class).match(e._2)) {
					System.out.println("New Balloon : " + balloon.userName + " : " + balloon.text);
					outbound.send("balloon:%s:%s", balloon.userName, balloon.text);
				}
				// Case: New mention added
				for (TwitterMng.Mention mention : ClassOf(
						TwitterMng.Mention.class).match(e._2)) {
					System.out.println("New Mention : " + mention.fromName + " -> " + mention.toName);
					outbound.send("mention:%s:%s", mention.fromName, mention.toName);
				}
				// Case: End of Graph Extend
				for (TwitterMng.EndExtend endExtend : ClassOf(
						TwitterMng.EndExtend.class).match(e._2)) {
					System.out.println("Graph Extend : complete.");
					outbound.send("endExtend");
				}
            }
		}
	}

	public static void extendGraph(long oauthId, String user) {
		System.out.println("Graph Extend : " + user + " at id=" + oauthId);
		tm.extendGraph(oauthId, user);
	}

	
	/**
	 * When Logout.
	 */
	public static void logout() {
		session.clear();
		redirect("Application.index");
	}

	private static void login(Account account) {
		session.put("sessionKey", account.id);
		session.put("userName", account.userName);
	}

	private static boolean isLoggedIn() {
		return session.contains("sessionKey");
	}

	private static Account getAccount(String username) {
		return Account.findOrCreate(username);
	}
}