package models;

import java.net.URL;

import javax.persistence.Entity;

import play.db.jpa.Model;

@Entity
public class Account extends Model {
	public long twitterId;
	public String userName;
	public URL profileImgUrl;
	public String accessToken;
	public String accessTokenSecret;

	public Account(String userName) {
		this.userName = userName;
	}

	public static Account findOrCreate(String userName) {
		Account account = Account.find("userName", userName).first();
		if (account == null)
			account = new Account(userName);
		return account;
	}
}