package org.chernovia.net.games.parlour.acro.server;

import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import org.chernovia.lib.misc.MiscUtil;
import org.chernovia.lib.netgames.zugserv.Connection;
import org.chernovia.lib.netgames.zugserv.ZugServ;

public class AcroGame extends Thread {
	static final String ACRO_DG = "&", DG_DELIM = "~", 
	NO_TOPIC = "", NO_ACRO = "";
	static final int
	DG_NEW_ACRO = 0,
	DG_ACRO_TIME = 1,
	DG_ACRO_ENTERED = 2,
	DG_SHOW_ACROS = 3,
	DG_VOTE_TIME = 4,
	DG_RESULTS = 5,
	DG_WAIT_TIME = 6,
	DG_PLAYERS = 7,
	DG_INFO = 8,
	DG_VARS = 9,
	DG_NEXT_ACRO = 10;
	
	static final int
	MOD_RESET = -3,MOD_PAUSE = -2,MOD_IDLE = -1,
	MOD_NEW = 0,MOD_ACRO = 1,MOD_VOTE = 2,MOD_WAIT = 3;

	class Acro  {
		String acro; AcroPlayer author; int votes; long time;
		public Acro(String S, AcroPlayer a, long t) {
			acro = S; author = a; votes = 0; time = t;
		}
	}
	
	private Vector<AcroPlayer> players;
	private Vector<Acro> acrolist;
	private AcroLetter[] letters;
	private String[] topics;
	private String acro,topic,winshout,newshout; //,deftopic,adshout;
	private ZugServ serv;
	private Connection manager;
	private long newtime;
	private int chan, mode,
	acrotime,votetime,waittime,basetime,
	atimevar,vtimevar,maxacro,minacro,
	speedpts,round,winscore,longhand,acrolen,
	maxcol, votecol, maxtopic, maxround, maxplay;
	boolean FLATTIME,REVEAL,TIEBONUS,TOPICS,GUI,ADULT,SHOUTING;
	boolean TESTING = false;
	AcroPlayer lastWin;
	AcroBox box;
	
	public void tch(String msg) { serv.tch(chan, ZugServ.MSG_SERV, msg); }
	public void spamTell(String msg) { spamTell(ZugServ.MSG_SERV,msg); }
	public void spamTell(String type, String msg) {
		for (AcroPlayer p : players) p.conn.tell(type, msg);
	}
	
	public int getNumAcros() { return acrolist.size(); }
	public Vector<AcroPlayer> getPlayers() { return players; }
	
	public String dumpGame() {
		StringBuffer dump = 
		new StringBuffer(
		AcroGame.ACRO_DG + DG_DELIM + AcroGame.DG_INFO + DG_DELIM);
		dump.append(chan + DG_DELIM);
		dump.append(((manager == null) ? "-" : manager) + DG_DELIM);
		dump.append(round + DG_DELIM);
		dump.append(((topic == NO_TOPIC) ? "-" : topic) + DG_DELIM);
		dump.append(((acro == NO_ACRO) ? "-" : acro) + DG_DELIM);
		dump.append(mode + DG_DELIM);
		return dump.toString();
	}
	
	public String dumpVars() {
		StringBuffer dump = new StringBuffer(
		ACRO_DG + DG_DELIM + DG_VARS + DG_DELIM);
		dump.append(DG_DELIM);
		dump.append(TIEBONUS ? 
		"fastest acro breaks tie" : "ties are not broken");	
		dump.append(DG_DELIM);
		dump.append(REVEAL ? "public voting" : "private voting");
		dump.append(DG_DELIM);
		dump.append(TOPICS ? "topics" : "no topics");
		dump.append(DG_DELIM);
		dump.append(FLATTIME ? 
		"dynamic acro times" : "static acro times");
		dump.append(DG_DELIM);
		dump.append(ADULT ? "adult themes" : "clean themes");
		dump.append(DG_DELIM);
		dump.append("Current players: " + players.size());
		dump.append(DG_DELIM);
		dump.append("Max players: " + maxplay);
		dump.append(DG_DELIM);
		dump.append("Min letters: " + minacro);
		dump.append(DG_DELIM);
		dump.append("Max letters: " + maxacro);
		return dump.toString();
	}
	
	public AcroPlayer getPlayer(String name) {
		for (AcroPlayer p : players) if (p.conn.getHandle().equalsIgnoreCase(name)) return p;
		return null;
	}
	
	public AcroPlayer getLastWinner() {
		return lastWin;
	}
	
	public boolean inChan(Connection conn, int c) {
		return conn.getChannels().contains(new Integer(c));
	}
	
	public String dumpPlayers() {
		StringBuffer dump =
		new StringBuffer(AcroGame.ACRO_DG + DG_DELIM + AcroGame.DG_PLAYERS + DG_DELIM);
		StringBuffer playStr = new StringBuffer();
		int n=0;
		for (AcroPlayer p : players) {
			if (inChan(p.conn,chan)) {
				n++;
				playStr.append(p.conn.getHandle() + DG_DELIM);
				playStr.append(p.score + DG_DELIM);
			}		
		}
		dump.append(n + DG_DELIM + playStr);
		return dump.toString();
	}	
	
	public String dumpAcros() {
		StringBuffer dump = new StringBuffer(ACRO_DG + DG_DELIM + 
		((mode == MOD_WAIT) ?	DG_RESULTS: DG_SHOW_ACROS) + DG_DELIM);
		dump.append(acrolist.size() + DG_DELIM);
		for (Acro a : acrolist) {
			dump.append(a.acro + DG_DELIM +	a.author.getName() + DG_DELIM + a.votes + DG_DELIM);
		}
		return dump.toString();
	}
	
	public void dumpAll(Connection conn) {
		conn.tell("bigdump",dumpGame());
		conn.tell("bigdump",dumpVars());
		conn.tell("bigdump",dumpPlayers());
	}
	
	private void spamAllGUI() {
		spamTell(dumpGame(),"gamedump");
		spamTell(dumpVars(),"vardump");
		spamTell(dumpPlayers(),"playdump");
	}

	public AcroGame(ZugServ srv, int c) {
		manager = null;	serv = srv; chan = c;
		//TODO: make this only for Twitch
		box = new AcroBox(); box.setVisible(true); 
	}

	private void initGame() {
		mode = MOD_IDLE; newLetters(AcroServ.ABCDEF);
		if (TESTING) {
			acrotime = 20; votetime = 10; waittime = 5;
		}
		else {
			acrotime = 90; votetime = 60; waittime = 30;
			//acrotime = 30; votetime = 30; waittime = 5;
		}
		atimevar = 6; vtimevar = 6; basetime = 20;
		winscore = 30; speedpts = 2;
		maxacro = 8; minacro = 3;
		maxcol = 60; votecol = 10;
		maxround = 99; maxtopic = 3;
		maxplay = 24; //TODO: too many?!
		FLATTIME = true; TOPICS = true; SHOUTING = false;
		REVEAL = false; TIEBONUS = false; GUI = false; ADULT = false;
		winshout = " is really KEWL!";
		newshout = "New Acro Game starting in channel " +
		chan + "!";
		//adshout = "Play Acrophobia!  Finger me for details.";
		tch(AcroServ.VERSION);
	}

	private boolean initNewGame() {
		acro = NO_ACRO; newtime = 0; lastWin = null;
		acrolen = newLength();
		round = 0; topic = NO_TOPIC;
		players = new Vector<AcroPlayer>(); 
		topics = new String[maxtopic];
		return true;
	}

	public void run() {
		try {
			initGame();
			while (initNewGame()) {
				if (mode != MOD_IDLE) mode = MOD_ACRO;
				else idle();
				tch("New Game Starting!"); int deserted = 0;
				if (SHOUTING) serv.broadcast(ZugServ.MSG_SERV,newshout);
				while (mode > MOD_NEW) {
					box.updateHiScores(new StringTokenizer( //just for the colors
					AcroBase.topTen("wins"),AcroServ.CR));
					acrolist = new Vector<Acro>();
					round++;
					acroRound(); if (GUI) spamAllGUI();
					acrolen = newLength();
					if (acrolist.size() == 0) {
						tch("No acros."); deserted++;
						if (deserted == 3) {
							tch("Bah. noone's here. I sleep.");
							mode = MOD_IDLE;
						}
					}
					else if (!TESTING && acrolist.size() < 3) {
						deserted = 0;
						tch(showAcros());
						tch("Too few acros to vote on (need at least three).");
					}
					else {
						deserted = 0; 
						voteRound(); if (GUI) spamAllGUI();
						scoreRound(); if (GUI) spamAllGUI();
					}
				}
			}
		}
		catch (Exception augh) {
			tch("Oops: " + augh.getMessage());
			augh.printStackTrace();
		}
	}

	private void idle() {
		try { while (mode != MOD_ACRO) sleep(999999); }
		catch (InterruptedException e) {
			if (mode != MOD_ACRO) idle();
		}
	}

	private String makeAcro(int numlets) {
		int t=0;
		for (int x=0;x<26;x++) t += letters[x].prob;
		StringBuffer S = new StringBuffer(""); int c=0;
		for (int x=0;x<numlets;x++) {
			int r = MiscUtil.randInt(t); int z=0;
			for (c=0;c<26;c++) {
				z += letters[c].prob;
				if (z>r) break;
			}
			S.append(letters[c].c.toUpperCase());
		}
		return S.toString();
	}

	private void acroRound() {
		if (mode > MOD_IDLE) mode = MOD_ACRO; else return;
		if (topic != NO_TOPIC) tch("Topic: " + topic);
		acro = makeAcro(acrolen); box.updateAcro(acro, topic);
		if (GUI) spamTell("gui",ACRO_DG + DG_DELIM + DG_NEW_ACRO + DG_DELIM + acro + DG_DELIM + round);
		int t = makeAcroTime();
		tch("Round " + round + " Acro: " + acro + AcroServ.CR + 
		"You have " + t + " seconds.");
		if (GUI) spamTell("gui",ACRO_DG + DG_DELIM + DG_ACRO_TIME + DG_DELIM + t + DG_DELIM);
		newtime = System.currentTimeMillis();
		sleeper((t/2)*1000);
		tch(t/2 + " seconds remaining.");
		sleeper(((t/2)-(t/6))*1000);
		tch(t/6 + " seconds...");
		sleeper((t/6)*1000);
	}

	private void sleeper(long t) {
		long rt = sleeping(t);
		while (rt > 0) {
			tch("Unpaused. (" +	rt/1000 + " seconds remaining)");
			rt = sleeping(rt);
		}
	}

	private long sleeping(long t) {
		int oldmode = mode;
		if (mode < MOD_NEW) return 0; //skip past stuff
		long s = System.currentTimeMillis();
		try { sleep(t); }
		catch (InterruptedException e) {
			if (mode != MOD_PAUSE) {
				tch("Skipping..."); return 0;
			}
			else {
				tch("Pausing...");
				try { sleep(999999); }
				catch (InterruptedException i) {
					mode = oldmode;
				}
				return t - (System.currentTimeMillis() - s);
			}
		}
		return 0;
	}

	private int makeAcroTime() {
		if (FLATTIME) return acrotime;
		return basetime + (atimevar*acro.length());
	}

	private int getVoteTime() {
		if (FLATTIME) return votetime;
		return basetime + (vtimevar*acrolist.size());
	}

	private String showAcros() {
		StringBuffer S = new StringBuffer(
				"Round " + round + " Acros: " + AcroServ.CR);
		jumble();
		//S.append("____________________________________________" + AcroServ.CR);
		longhand = 0; // for formatting
		int x=0; for (Acro a: acrolist) {
			S.append(++x + ". ");
			S.append(a.acro + AcroServ.CR);
			int l = a.author.getName().length();
			if (l > longhand) longhand = l; //get longest handle
		}
		//S.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~" + AcroServ.CR);
		return S.toString();
	}

	//TODO: rework this for vectors
	private void jumble() {
		//if (acronum < 2) return;
		//Acro tmpacro;
		//for (int x=0;x<acronum;x++) {
			//int y = MiscUtil.randInt(acronum);
			//tmpacro = acrolist[y];
			//acrolist[y] = acrolist[x];
			//acrolist[x] = tmpacro; }
	}

	private void voteRound() {
		if (mode > MOD_IDLE) mode = MOD_VOTE; else return;
		if (topic != NO_TOPIC) tch("Topic: " + topic);
		tch(showAcros()); if (GUI) spamTell(ZugServ.MSG_SERV,dumpAcros());
		box.updateAcros(acrolist); 
		int t=getVoteTime(); 
		tch("Time to vote!  Enter the number of an acro above. " +
		AcroServ.CR + "You have " + t + " seconds.");
		sleeper((t-(t/6))*1000); tch(t/6 + " seconds...");
		sleeper ((t/6)*1000);
	}

	private void scoreRound() {
		if (mode > MOD_IDLE) mode = MOD_WAIT; else return;
		if (topic != NO_TOPIC) tch("Topic: " + topic);
		tch(showVote(false));
		tch(showScore());
		box.updateScores(players);
		AcroPlayer winner = winCheck(); //System.out.println(w);
		if (winner == null) waitRound();
		else  {
			tch(winner.getName() + " wins!" + AcroServ.CR);
			if (SHOUTING) serv.broadcast(ZugServ.MSG_SERV,winner.getName() + winshout);
			//tch(summarize());
			//showHistory();
			AcroBase.updateStats(this, winner);
			tch("New game in " + (2*waittime) + " seconds.");
			sleeper(waittime*2000);
			mode = MOD_NEW;
		}
	}

	private void waitRound() {
		mode = MOD_WAIT; topic = NO_TOPIC; getTopics();
		if (TOPICS &&  lastWin != null) {
			tch(lastWin.getName() + " may choose a topic: " + AcroServ.CR + showTopics());
			lastWin.conn.tell(ZugServ.MSG_SERV,"Next acro: " + acrolen + " letters");
		}
		tch("Next round in " + waittime + " seconds.");
		sleeper(waittime*1000); lastWin = null;
	}

	private void getTopics() {
		List<String> topicvec = AcroBase.readFile(AcroServ.TOPFILE);
		int lof = topicvec.size();
		int[] toplist = new int[maxtopic];
		for (int x=0;x<maxtopic;x++) {
			boolean match;
			do {
				match = false;
				toplist[x] = MiscUtil.randInt(lof-1);
				//System.out.println("Topic #" + x + ": " +
				//toplist[x]);
				for (int y=x-1;y>=0;y--)
					if (toplist[x] == toplist[y]) match = true;
			} while (match);
		}
		for (int t=0;t<maxtopic;t++)
			topics[t] = topicvec.get(toplist[t]);
		//topics[MAXTOPIC-1] = DEFTOPIC;
	}

	private String showTopics() {
		StringBuffer SB = new StringBuffer();
		for (int t=0;t<maxtopic;t++)
			SB.append((t+1) + ". " + topics[t] + AcroServ.CR);
		return SB.toString();
	}

	protected void newTopic(AcroPlayer p, String msg) {
		String n = p.getName();
		if (msg.length()>6 && msg.startsWith("topic ")) {
			topic = msg.substring(6);
			tch(n + " selects " + topic + "...");
		}
		else {
			int t = MiscUtil.strToInt(msg)-1;
			if (t < 0 || t >= maxtopic) {
				p.conn.tell(ZugServ.MSG_SERV,"Bad Topic.");
			}
			else {
				topic = topics[t];
				tch(n + " selects " + topic + "...");
			}
		}
	}

	private AcroPlayer winCheck() {
		AcroPlayer leader = null;
		for (AcroPlayer p: players) if (leader == null || p.score > leader.score) leader = p;
		if (leader.score >= winscore) return leader; else return null; //TODO: ties!
	}

	private String showVote(boolean fancyformat) {
		String CR = AcroServ.CR;
		StringBuffer S = new StringBuffer(CR + "Round " +
				round + " Voting Results: " + CR + CR);
		int acrocol = maxcol - (votecol + longhand);
		if (fancyformat) for (Acro a: acrolist) {
			int h = a.author.getName().length();
			String acroline = 
			a.author.getName() + MiscUtil.txtPad(longhand-h) + a.votes + " votes! (" +	a.time/(float)1000 + ")" + CR;
			if (a.acro.length() < acrocol) {
				S.append("  " + a.acro + MiscUtil.txtPad(acrocol - a.acro.length()) + acroline);
			}
			else {
				int i = MiscUtil.lastSpc(a.acro.substring(0,acrocol));
				S.append("  " + 
				a.acro.substring(0,i) + MiscUtil.txtPad(acrocol - i) + acroline + " " + a.acro.substring(i) + CR);
			}
		}
		else for (Acro a: acrolist) {
			S.append(a.acro + " (" + a.author.getName() + ") " + a.votes + " votes! (" + a.time/(float)1000 + ")" +	CR);
		}
		S.append(CR);
		for (Acro a: acrolist) {
			if (a.votes > 0 && a.author.vote >= 0) a.author.score += a.votes;
		}
		AcroPlayer s = getSpeed(); if (s != null) {
			S.append(" " + s.getName() + " -> " + speedpts + " speed bonus points" + CR);
			s.score += speedpts;
		}
		S.append(winners() + CR);
		return S.toString();
	}

	private AcroPlayer getSpeed() {
		AcroPlayer s = null; long t = 9999999;
		for (Acro a: acrolist) {
			if (a.time < t && a.author.score < winscore-4 &&  a.author.vote >= 0 &&	a.votes > 0) {
				s = a.author; t = a.time;
			}
		}
		return s;
	}

	private String winners() {
		String CR = AcroServ.CR;
		StringBuffer S = new StringBuffer("");
		for (Acro a: acrolist) {
			if (a.author.vote < 0) {
				S.append(" " + a.author.getName() + " did not vote, and thus forfeits this round." + CR);
			}
		}
		int w = 0, bonusPts = acro.length(); 
		long wintime = 999999;
		for (Acro a: acrolist) {
			if (a.author.vote >= 0 && a.votes > w) w = a.votes;
		}
		for (Acro a: acrolist) {
			if (a.votes == w && w > 0 && a.author.vote >= 0) {
				if (TIEBONUS) {
					a.author.score += bonusPts;
					S.append(" " + a.author.getName());
				}
				if (a.time < wintime) {
					lastWin = a.author;
					wintime = a.time;
				}
			}
		}
		if (w < 1) S.append(" No winners.");
		else if (TIEBONUS) {
			S.append(" -> " + bonusPts + " bonus points!");
		}
		else if (lastWin != null) { 
			S.append(" " + lastWin.getName() + " -> " + bonusPts + " bonus points!");
			lastWin.score += bonusPts;
		}
		if (TOPICS && lastWin != null)
			S.append(CR + " " + lastWin.getName() + " gets to choose the next topic.");
		return S.toString();
	}

	private String showScore() {
		// record scores
		for (AcroPlayer p : players) {
			Acro acro = findAcro(p); 
			int v = p.vote;
			if (v < 0) p.save(acro,null); else p.save(acro,acrolist.elementAt(v).author);
		}
		// show scores
		StringBuffer S = new StringBuffer(" Round " + round + " Scores: " + AcroServ.CR);
		for (AcroPlayer p : players) {
			if (p.score > 0) S.append(" " + p.getName() + ": " + p.score + " "); p.vote = -1;
		}
		return S.toString() + AcroServ.CR;
	}
	
	protected boolean isLegal(String A) {
		StringTokenizer ST = new StringTokenizer(A);
		System.out.println("Received acro: " + A + " (current: " + acro + ")");
		if (acro.length() != ST.countTokens()) {
			System.out.println("Bad length: " + ST.countTokens()); return false;
		}
		int x=0; 
		while (ST.hasMoreTokens()) {
			String S = ST.nextToken();
			char c = S.charAt(0);
			if (c == '"' && S.length()>1) c = S.charAt(1);
			if (acro.charAt(x)!=c) {
				System.out.println("Bad match: " + c + " != " + acro.charAt(x)); 
				return false; 
			}
			x++;
		}
		return true;
	}

	protected String makeAcro(String A) {
		StringBuffer SB = new StringBuffer();
		StringTokenizer ST = new StringTokenizer(A);
		while (ST.hasMoreTokens())
			SB.append(ST.nextToken().charAt(0));
		return SB.toString().toUpperCase();
	}

	private Acro findAcro(AcroPlayer p)  { //find acro by player
		for (Acro a : acrolist) if (a.author.equals(p)) return a;
		return null;
	}

	private int newLength() {
		return MiscUtil.randInt(maxacro-minacro) + minacro;
	}

	protected void newLetters(String ABCFILE) {
		letters =
			AcroLetter.loadABC(ABCFILE + AcroLetter.LETTEXT);
		if (letters == null) {
			tch("Can't find Letter File: " + ABCFILE);
			tch("Using default (" + AcroServ.ABCDEF + ") " +
			"instead.");
			letters = AcroLetter.loadABC(AcroServ.ABCDEF +
					AcroLetter.LETTEXT);
		}
		else tch("Loaded Letter File: " + ABCFILE);
	}

	private AcroPlayer addNewPlayer(Connection conn) {
		if (players.size() > maxplay)  {
			conn.tell(ZugServ.MSG_SERV,"Game Full!?"); return null;
		}
		else {
			AcroPlayer p = new AcroPlayer(this,conn); players.add(p);
			conn.tell(ZugServ.MSG_SERV,"Welcome!");
			box.updateScores(players);
			return p;
		}
	}

	//public methods

	protected void newAcro(Connection conn, String acro_str) { //throws Exception {
		AcroPlayer p = getPlayer(conn.getHandle()); if (p == null) p = addNewPlayer(conn);	
		if (p == null) return; //zoiks
		Acro a = findAcro(p);
		if (a != null) {
			a.acro = acro_str;
			a.time = System.currentTimeMillis()-newtime;
			p.conn.tell(ZugServ.MSG_SERV,"Changed your acro.");
			p.conn.tell(ZugServ.MSG_SERV,"Time: " + a.time / (float)1000);
		}
		else {
			a = new Acro(acro_str,p,System.currentTimeMillis()-newtime);
			acrolist.add(a);
			p.conn.tell(ZugServ.MSG_SERV,"Entered your acro. Time: " + a.time / (float)1000);
			tch("Acro #" + acrolist.size() + " received!");
		}
	}

	protected void newVote(Connection conn, int v) {
		String handle = conn.getHandle();
		AcroPlayer p = getPlayer(handle);
		if (p == null) { p = addNewPlayer(conn); if (p == null) return; }
		if (acrolist.get(v).author == p) {
			tch("Voting for oneself is not allowed, " + handle + "."); return;
		}
		if (p.vote >= 0) acrolist.get(p.vote).votes--;
		acrolist.get(v).votes++; p.vote = v;
		p.conn.tell(ZugServ.MSG_SERV,"Your vote has been entered.");
	}

	protected String showLetters() {
		if (letters == null) return "Nothing loaded.";
		StringBuffer SB = new StringBuffer();
		for (int x=0;x<26;x++) {
			SB.append(letters[x].c + ": " +
					letters[x].prob + AcroServ.CR);
		}
		return SB.toString();
	}
	
	Connection getManager() { return manager; }
	void setManager(Connection mgr) { manager = mgr; }
	int getMode() { return mode; }
	void setMode(int m) { mode = m; }
	int getChan() { return chan; }
	int getAcroTime() { return acrotime; }
	int getMaxRound() { return maxround; }
	ZugServ getServ() { return serv; }
	String getAcro() { return acro; }
	
	protected String listPlayers() {
		StringBuffer playstr = new StringBuffer("Players: " + AcroServ.CR);
		for (AcroPlayer p : players) {
			playstr.append(p.conn.getHandle() + ": " + p.score + AcroServ.CR);
		}
		return playstr.toString();
	}
	
	public String listVars() {
		StringBuffer SB = new StringBuffer();
		//SB.append("Shouting: " + SHOUTING + AcroServ.CR);
		SB.append("Settings: " + AcroServ.CR);
		SB.append("Channel: " + chan);
		SB.append(AcroServ.CR);
		SB.append("Manager: " + (manager == null ? "Nobody" : 
		manager.getHandle()));
		SB.append(AcroServ.CR);
		SB.append(TIEBONUS ? 
		"fastest acro breaks tie" : "ties are not broken");	
		SB.append(AcroServ.CR);
		SB.append(REVEAL ? "public voting" : "private voting");
		SB.append(AcroServ.CR);
		SB.append(TOPICS ? "topics" : "no topics");
		SB.append(AcroServ.CR);
		SB.append(FLATTIME ? 
		"dynamic acro times" : "static acro times");
		SB.append(AcroServ.CR);
		SB.append(ADULT ? "adult themes" : "clean themes");
		SB.append(AcroServ.CR);
		SB.append("Current players: " + players.size());
		SB.append(AcroServ.CR);
		SB.append("Max players: " + maxplay);
		SB.append(AcroServ.CR);
		SB.append("Min letters: " + minacro);
		SB.append(AcroServ.CR);
		SB.append("Max letters: " + maxacro);
		SB.append(AcroServ.CR);
		return SB.toString();
	}
}